package org.hello.riscvdisassembler.decoder;

import org.hello.riscvdisassembler.elf.model.SectionHeader;
import org.hello.riscvdisassembler.ir.InstructionIr;
import org.hello.riscvdisassembler.resolver.ResolvedProgram;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.hello.riscvdisassembler.ir.InstructionIr.ControlFlowType;

/**
 * Decodes executable bytes into {@link InstructionIr} objects for the RV32I base ISA.
 *
 * <p>Unsupported or invalid encodings are preserved as raw words so disassembly can
 * continue even when the decoder does not recognize a particular instruction.</p>
 */
public final class Rv32iDecoder {
    private static final String[] REGISTERS = {
            "zero", "ra", "sp", "gp", "tp", "t0", "t1", "t2",
            "s0", "s1", "a0", "a1", "a2", "a3", "a4", "a5",
            "a6", "a7", "s2", "s3", "s4", "s5", "s6", "s7",
            "s8", "s9", "s10", "s11", "t3", "t4", "t5", "t6"
    };

    /**
     * Decodes all executable sections in a resolved program.
     *
     * @param program resolved program view containing executable sections and ELF bytes
     * @return decoded instructions in section and address order
     */
    public List<InstructionIr> decode(ResolvedProgram program) {
        List<InstructionIr> instructions = new ArrayList<>();
        for (SectionHeader section : program.executableSections()) {
            byte[] bytes = program.elfFile().slice(section);
            // RV32I instructions are decoded as fixed-width 32-bit words, so trailing bytes
            // that do not fill a full word are intentionally skipped.
            for (int offset = 0; offset + 3 < bytes.length; offset += 4) {
                int word = readWord(bytes, offset);
                long address = section.address() + offset;
                instructions.add(decodeWord(address, word, section.name()));
            }
        }
        return instructions;
    }

    /**
     * Decodes a single 32-bit instruction word.
     *
     * @param pc instruction address
     * @param word raw 32-bit word
     * @param sectionName owning section name
     * @return decoded instruction IR, or a raw pseudo-instruction when unsupported
     */
    private InstructionIr decodeWord(long pc, int word, String sectionName) {
        int opcode = word & 0x7F;
        int rd = (word >>> 7) & 0x1F;
        int funct3 = (word >>> 12) & 0x7;
        int rs1 = (word >>> 15) & 0x1F;
        int rs2 = (word >>> 20) & 0x1F;
        int funct7 = (word >>> 25) & 0x7F;

        switch (opcode) {
            case 0x37:
                return ir(pc, word, "lui", ops(reg(rd), hex(immU(word))), "U", ControlFlowType.NORMAL, null, sectionName);
            case 0x17:
                return ir(pc, word, "auipc", ops(reg(rd), hex(immU(word))), "U", ControlFlowType.NORMAL, null, sectionName);
            case 0x6F:
                return decodeJal(pc, word, rd, sectionName);
            case 0x67:
                return decodeJalr(pc, word, rd, rs1, funct3, sectionName);
            case 0x63:
                return decodeBranch(pc, word, rs1, rs2, funct3, sectionName);
            case 0x03:
                return decodeLoad(pc, word, rd, rs1, funct3, sectionName);
            case 0x23:
                return decodeStore(pc, word, rs1, rs2, funct3, sectionName);
            case 0x13:
                return decodeOpImm(pc, word, rd, rs1, funct3, funct7, sectionName);
            case 0x33:
                return decodeOp(pc, word, rd, rs1, rs2, funct3, funct7, sectionName);
            case 0x0F:
                return ir(pc, word, funct3 == 0 ? "fence" : "fence.i", noOps(), "I", ControlFlowType.NORMAL, null, sectionName);
            case 0x73:
                return decodeSystem(pc, word, rd, rs1, funct3, sectionName);
            default:
                return raw(pc, word, sectionName);
        }
    }

    /**
     * Decodes a {@code jal} instruction.
     *
     * @param pc current instruction address
     * @param word raw 32-bit word
     * @param rd destination register
     * @param sectionName owning section name
     * @return decoded jump or call instruction
     */
    private InstructionIr decodeJal(long pc, int word, int rd, String sectionName) {
        int imm = immJ(word);
        long target = pc + imm;
        ControlFlowType type = (rd == 1 || rd == 5) ? ControlFlowType.CALL : ControlFlowType.UNCONDITIONAL_JUMP;
        return ir(pc, word, "jal", ops(reg(rd), hex(target)), "J", type, target, sectionName);
    }

    /**
     * Decodes a {@code jalr} instruction and classifies it as call, return, or jump.
     *
     * @param pc current instruction address
     * @param word raw 32-bit word
     * @param rd destination register
     * @param rs1 base register
     * @param funct3 function field that must be zero for {@code jalr}
     * @param sectionName owning section name
     * @return decoded instruction, or a raw word when the encoding is invalid
     */
    private InstructionIr decodeJalr(long pc, int word, int rd, int rs1, int funct3, String sectionName) {
        if (funct3 != 0) {
            return raw(pc, word, sectionName);
        }
        int imm = immI(word);
        ControlFlowType type;
        if (rd == 0 && rs1 == 1 && imm == 0) {
            type = ControlFlowType.RETURN;
        } else if (rd == 1 || rd == 5) {
            type = ControlFlowType.CALL;
        } else {
            type = ControlFlowType.UNCONDITIONAL_JUMP;
        }
        return ir(pc, word, "jalr", ops(reg(rd), imm + "(" + reg(rs1) + ")"), "I", type, null, sectionName);
    }

    /**
     * Decodes a conditional branch instruction.
     *
     * @param pc current instruction address
     * @param word raw 32-bit word
     * @param rs1 first source register
     * @param rs2 second source register
     * @param funct3 branch subtype selector
     * @param sectionName owning section name
     * @return decoded branch instruction, or a raw word for unsupported variants
     */
    private InstructionIr decodeBranch(long pc, int word, int rs1, int rs2, int funct3, String sectionName) {
        String mnemonic;
        switch (funct3) {
            case 0:
                mnemonic = "beq";
                break;
            case 1:
                mnemonic = "bne";
                break;
            case 4:
                mnemonic = "blt";
                break;
            case 5:
                mnemonic = "bge";
                break;
            case 6:
                mnemonic = "bltu";
                break;
            case 7:
                mnemonic = "bgeu";
                break;
            default:
                mnemonic = ".word";
                break;
        }
        if (mnemonic.equals(".word")) {
            return raw(pc, word, sectionName);
        }
        long target = pc + immB(word);
        return ir(pc, word, mnemonic, ops(reg(rs1), reg(rs2), hex(target)), "B", ControlFlowType.CONDITIONAL_BRANCH, target, sectionName);
    }

    /**
     * Decodes a load instruction.
     *
     * @param pc current instruction address
     * @param word raw 32-bit word
     * @param rd destination register
     * @param rs1 base register
     * @param funct3 load subtype selector
     * @param sectionName owning section name
     * @return decoded load instruction, or a raw word for unsupported variants
     */
    private InstructionIr decodeLoad(long pc, int word, int rd, int rs1, int funct3, String sectionName) {
        String mnemonic;
        switch (funct3) {
            case 0:
                mnemonic = "lb";
                break;
            case 1:
                mnemonic = "lh";
                break;
            case 2:
                mnemonic = "lw";
                break;
            case 4:
                mnemonic = "lbu";
                break;
            case 5:
                mnemonic = "lhu";
                break;
            default:
                mnemonic = ".word";
                break;
        }
        if (mnemonic.equals(".word")) {
            return raw(pc, word, sectionName);
        }
        return ir(pc, word, mnemonic, ops(reg(rd), immI(word) + "(" + reg(rs1) + ")"), "I", ControlFlowType.NORMAL, null, sectionName);
    }

    /**
     * Decodes a store instruction.
     *
     * @param pc current instruction address
     * @param word raw 32-bit word
     * @param rs1 base register
     * @param rs2 value register
     * @param funct3 store subtype selector
     * @param sectionName owning section name
     * @return decoded store instruction, or a raw word for unsupported variants
     */
    private InstructionIr decodeStore(long pc, int word, int rs1, int rs2, int funct3, String sectionName) {
        String mnemonic;
        switch (funct3) {
            case 0:
                mnemonic = "sb";
                break;
            case 1:
                mnemonic = "sh";
                break;
            case 2:
                mnemonic = "sw";
                break;
            default:
                mnemonic = ".word";
                break;
        }
        if (mnemonic.equals(".word")) {
            return raw(pc, word, sectionName);
        }
        return ir(pc, word, mnemonic, ops(reg(rs2), immS(word) + "(" + reg(rs1) + ")"), "S", ControlFlowType.NORMAL, null, sectionName);
    }

    /**
     * Decodes an immediate arithmetic or logical instruction.
     *
     * @param pc current instruction address
     * @param word raw 32-bit word
     * @param rd destination register
     * @param rs1 source register
     * @param funct3 subtype selector
     * @param funct7 upper function bits used by shift variants
     * @param sectionName owning section name
     * @return decoded instruction, or a raw word when unsupported
     */
    private InstructionIr decodeOpImm(long pc, int word, int rd, int rs1, int funct3, int funct7, String sectionName) {
        String mnemonic;
        List<String> operands;
        switch (funct3) {
            case 0:
                mnemonic = "addi";
                operands = ops(reg(rd), reg(rs1), Integer.toString(immI(word)));
                break;
            case 2:
                mnemonic = "slti";
                operands = ops(reg(rd), reg(rs1), Integer.toString(immI(word)));
                break;
            case 3:
                mnemonic = "sltiu";
                operands = ops(reg(rd), reg(rs1), Integer.toUnsignedString(immI(word)));
                break;
            case 4:
                mnemonic = "xori";
                operands = ops(reg(rd), reg(rs1), Integer.toString(immI(word)));
                break;
            case 6:
                mnemonic = "ori";
                operands = ops(reg(rd), reg(rs1), Integer.toString(immI(word)));
                break;
            case 7:
                mnemonic = "andi";
                operands = ops(reg(rd), reg(rs1), Integer.toString(immI(word)));
                break;
            case 1:
                if (funct7 != 0x00) {
                    return raw(pc, word, sectionName);
                }
                mnemonic = "slli";
                operands = ops(reg(rd), reg(rs1), Integer.toString((word >>> 20) & 0x1F));
                break;
            case 5:
                if (funct7 == 0x00) {
                    mnemonic = "srli";
                } else if (funct7 == 0x20) {
                    mnemonic = "srai";
                } else {
                    return raw(pc, word, sectionName);
                }
                operands = ops(reg(rd), reg(rs1), Integer.toString((word >>> 20) & 0x1F));
                break;
            default:
                return raw(pc, word, sectionName);
        }
        return ir(pc, word, mnemonic, operands, "I", ControlFlowType.NORMAL, null, sectionName);
    }

    /**
     * Decodes a register-register arithmetic or logical instruction.
     *
     * @param pc current instruction address
     * @param word raw 32-bit word
     * @param rd destination register
     * @param rs1 first source register
     * @param rs2 second source register
     * @param funct3 subtype selector
     * @param funct7 upper function bits
     * @param sectionName owning section name
     * @return decoded instruction, or a raw word when unsupported
     */
    private InstructionIr decodeOp(long pc, int word, int rd, int rs1, int rs2, int funct3, int funct7, String sectionName) {
        String mnemonic;
        switch (funct3) {
            case 0:
                if (funct7 == 0x00) {
                    mnemonic = "add";
                } else if (funct7 == 0x20) {
                    mnemonic = "sub";
                } else {
                    mnemonic = ".word";
                }
                break;
            case 1:
                mnemonic = funct7 == 0x00 ? "sll" : ".word";
                break;
            case 2:
                mnemonic = funct7 == 0x00 ? "slt" : ".word";
                break;
            case 3:
                mnemonic = funct7 == 0x00 ? "sltu" : ".word";
                break;
            case 4:
                mnemonic = funct7 == 0x00 ? "xor" : ".word";
                break;
            case 5:
                if (funct7 == 0x00) {
                    mnemonic = "srl";
                } else if (funct7 == 0x20) {
                    mnemonic = "sra";
                } else {
                    mnemonic = ".word";
                }
                break;
            case 6:
                mnemonic = funct7 == 0x00 ? "or" : ".word";
                break;
            case 7:
                mnemonic = funct7 == 0x00 ? "and" : ".word";
                break;
            default:
                mnemonic = ".word";
                break;
        }
        if (mnemonic.equals(".word")) {
            return raw(pc, word, sectionName);
        }
        return ir(pc, word, mnemonic, ops(reg(rd), reg(rs1), reg(rs2)), "R", ControlFlowType.NORMAL, null, sectionName);
    }

    /**
     * Decodes system and CSR instructions.
     *
     * @param pc current instruction address
     * @param word raw 32-bit word
     * @param rd destination register
     * @param rs1 source register or immediate carrier depending on the CSR variant
     * @param funct3 subtype selector
     * @param sectionName owning section name
     * @return decoded instruction, or a raw word when unsupported
     */
    private InstructionIr decodeSystem(long pc, int word, int rd, int rs1, int funct3, String sectionName) {
        if (funct3 == 0) {
            if ((word >>> 20) == 0) {
                return ir(pc, word, "ecall", noOps(), "I", ControlFlowType.TERMINATOR, null, sectionName);
            }
            if ((word >>> 20) == 1) {
                return ir(pc, word, "ebreak", noOps(), "I", ControlFlowType.TERMINATOR, null, sectionName);
            }
        }

        String mnemonic;
        switch (funct3) {
            case 1:
                mnemonic = "csrrw";
                break;
            case 2:
                mnemonic = "csrrs";
                break;
            case 3:
                mnemonic = "csrrc";
                break;
            case 5:
                mnemonic = "csrrwi";
                break;
            case 6:
                mnemonic = "csrrsi";
                break;
            case 7:
                mnemonic = "csrrci";
                break;
            default:
                mnemonic = ".word";
                break;
        }
        if (mnemonic.equals(".word")) {
            return raw(pc, word, sectionName);
        }
        int csr = (word >>> 20) & 0xFFF;
        return ir(pc, word, mnemonic, ops(reg(rd), String.format("0x%03x", csr), reg(rs1)), "I", ControlFlowType.NORMAL, null, sectionName);
    }

    /**
     * Creates a decoded instruction object.
     *
     * @param address instruction address
     * @param rawInstruction raw 32-bit word
     * @param mnemonic decoded mnemonic
     * @param operands textual operands
     * @param format encoding family label
     * @param controlFlowType semantic control-flow category
     * @param branchTarget direct target address when known
     * @param sectionName owning section name
     * @return instruction IR instance
     */
    private static InstructionIr ir(long address, int rawInstruction, String mnemonic, List<String> operands, String format,
                                    ControlFlowType controlFlowType, Long branchTarget, String sectionName) {
        return new InstructionIr(address, rawInstruction, mnemonic, operands, format, controlFlowType, branchTarget, sectionName);
    }

    /**
     * Creates a fallback IR node for unsupported or undecodable words.
     *
     * @param address instruction address
     * @param rawInstruction raw 32-bit word
     * @param sectionName owning section name
     * @return pseudo-instruction that preserves the raw word as data
     */
    private static InstructionIr raw(long address, int rawInstruction, String sectionName) {
        return ir(address, rawInstruction, ".word", ops(hex(Integer.toUnsignedLong(rawInstruction))), "RAW",
                ControlFlowType.NORMAL, null, sectionName);
    }

    /**
     * Wraps operand strings into a list.
     *
     * @param values operand strings
     * @return operand list
     */
    private static List<String> ops(String... values) {
        return Arrays.asList(values);
    }

    /**
     * Returns an empty operand list.
     *
     * @return immutable empty list
     */
    private static List<String> noOps() {
        return Collections.emptyList();
    }

    /**
     * Resolves an ABI register name from a register index.
     *
     * @param index register number in the range {@code 0..31}
     * @return ABI register name such as {@code a0} or {@code sp}
     */
    private static String reg(int index) {
        return REGISTERS[index];
    }

    /**
     * Reads one little-endian 32-bit instruction word from a byte array.
     *
     * @param bytes section bytes
     * @param offset byte offset within the section
     * @return 32-bit instruction word
     */
    private static int readWord(byte[] bytes, int offset) {
        return Byte.toUnsignedInt(bytes[offset])
                | (Byte.toUnsignedInt(bytes[offset + 1]) << 8)
                | (Byte.toUnsignedInt(bytes[offset + 2]) << 16)
                | (Byte.toUnsignedInt(bytes[offset + 3]) << 24);
    }

    /**
     * Extracts and sign-extends an I-type immediate.
     *
     * @param word raw instruction word
     * @return signed immediate value
     */
    private static int immI(int word) {
        return signExtend(word >>> 20, 12);
    }

    /**
     * Extracts and sign-extends an S-type immediate.
     *
     * @param word raw instruction word
     * @return signed immediate value
     */
    private static int immS(int word) {
        int imm = ((word >>> 7) & 0x1F) | (((word >>> 25) & 0x7F) << 5);
        return signExtend(imm, 12);
    }

    /**
     * Extracts and sign-extends a B-type branch immediate.
     *
     * @param word raw instruction word
     * @return signed branch displacement
     */
    private static int immB(int word) {
        int imm = (((word >>> 8) & 0xF) << 1)
                | (((word >>> 25) & 0x3F) << 5)
                | (((word >>> 7) & 0x1) << 11)
                | (((word >>> 31) & 0x1) << 12);
        return signExtend(imm, 13);
    }

    /**
     * Extracts a U-type immediate.
     *
     * @param word raw instruction word
     * @return upper immediate value with low 12 bits cleared
     */
    private static int immU(int word) {
        return word & 0xFFFFF000;
    }

    /**
     * Extracts and sign-extends a J-type jump immediate.
     *
     * @param word raw instruction word
     * @return signed jump displacement
     */
    private static int immJ(int word) {
        int imm = (((word >>> 21) & 0x3FF) << 1)
                | (((word >>> 20) & 0x1) << 11)
                | (((word >>> 12) & 0xFF) << 12)
                | (((word >>> 31) & 0x1) << 20);
        return signExtend(imm, 21);
    }

    /**
     * Sign-extends a value with the specified source width to 32 bits.
     *
     * @param value bit-field value before sign extension
     * @param bits number of significant bits in {@code value}
     * @return sign-extended 32-bit integer
     */
    private static int signExtend(int value, int bits) {
        int shift = 32 - bits;
        return (value << shift) >> shift;
    }

    /**
     * Formats a value as an 8-digit hexadecimal string.
     *
     * @param value numeric value to format
     * @return hexadecimal string prefixed with {@code 0x}
     */
    private static String hex(long value) {
        return String.format("0x%08x", value);
    }
}
