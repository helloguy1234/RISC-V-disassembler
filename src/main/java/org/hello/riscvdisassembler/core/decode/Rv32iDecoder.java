package org.hello.riscvdisassembler.core.decode;

import org.hello.riscvdisassembler.core.binary.model.BinarySection;
import org.hello.riscvdisassembler.core.decode.model.InstructionIr;
import org.hello.riscvdisassembler.core.decode.model.ast.*;
import org.hello.riscvdisassembler.core.resolve.ResolvedProgram;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.hello.riscvdisassembler.core.decode.model.InstructionIr.ControlFlowType;

/**
 * Decodes executable bytes into {@link InstructionIr} objects for the RV32I
 * base ISA.
 *
 * <p>
 * Unsupported or invalid encodings are preserved as raw words so disassembly
 * can
 * continue even when the decoder does not recognize a particular instruction.
 * </p>
 */
public final class Rv32iDecoder implements InstructionDecoder {
    private static final String RAW_WORD_MNEMONIC = ".word";
    private static final String[] REGISTERS = {
            "zero", "ra", "sp", "gp", "tp", "t0", "t1", "t2",
            "s0", "s1", "a0", "a1", "a2", "a3", "a4", "a5",
            "a6", "a7", "s2", "s3", "s4", "s5", "s6", "s7",
            "s8", "s9", "s10", "s11", "t3", "t4", "t5", "t6"
    };

    /**
     * Decodes all executable sections in a resolved program.
     *
     * @param program resolved program view containing executable sections and ELF
     *                bytes
     * @return decoded instructions in section and address order
     */
    public List<InstructionIr> decode(ResolvedProgram program) {
        List<InstructionIr> instructions = new ArrayList<>();
        for (BinarySection section : program.executableSections()) {
            for (long address = section.address(); address + 3 < section.address() + section.size(); address += 4) {
                instructions.add(decodeAt(program, section, address));
            }
        }
        return instructions;
    }

    /**
     * Decodes one instruction at a concrete address inside an executable section.
     *
     * @param program resolved program holding raw ELF bytes
     * @param section owning executable section
     * @param address instruction address within the section
     * @return decoded instruction or raw fallback
     */
    @Override
    public InstructionIr decodeAt(ResolvedProgram program, BinarySection section, long address) {
        if (address < section.address() || address + 3 >= section.address() + section.size()) {
            throw new IllegalArgumentException("Address is outside the section: " + String.format("0x%08x", address));
        }

        byte[] bytes = program.binaryImage().slice(section);
        int offset = Math.toIntExact(address - section.address());
        int word = readWord(bytes, offset);
        return decodeWord(address, word, section.name());
    }

    /**
     * Decodes a single 32-bit instruction word.
     *
     * @param pc          instruction address
     * @param word        raw 32-bit word
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
            case 0x37: {
                int imm = immU(word);
                AssignExpr semantic = new AssignExpr(
                        new RegisterExpr(reg(rd)),
                        new ImmediateExpr(Integer.toUnsignedLong(imm)));
                return ir(pc, word, "lui", ops(reg(rd), hex(Integer.toUnsignedLong(imm))), "U",
                        ControlFlowType.NORMAL, null, sectionName, semantic);
            }
            case 0x17: {
                int imm = immU(word);
                AssignExpr semantic = new AssignExpr(
                        new RegisterExpr(reg(rd)),
                        new BinaryOpExpr(Operator.ADD, new ImmediateExpr(pc),
                                new ImmediateExpr(Integer.toUnsignedLong(imm))));
                return ir(pc, word, "auipc", ops(reg(rd), hex(Integer.toUnsignedLong(imm))), "U",
                        ControlFlowType.NORMAL, null, sectionName, semantic);
            }
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
                return ir(pc, word, funct3 == 0 ? "fence" : "fence.i", noOps(), "I", ControlFlowType.NORMAL, null,
                        sectionName, null);
            case 0x73:
                return decodeSystem(pc, word, rd, rs1, funct3, sectionName);
            default:
                return raw(pc, word, sectionName, null);
        }
    }

    /**
     * Decodes a {@code jal} instruction.
     *
     * @param pc          current instruction address
     * @param word        raw 32-bit word
     * @param rd          destination register
     * @param sectionName owning section name
     * @return decoded jump or call instruction
     */
    private InstructionIr decodeJal(long pc, int word, int rd, String sectionName) {
        int imm = immJ(word);
        long target = (pc + imm) & 0xFFFFFFFFL;
        ControlFlowType type = (rd == 1 || rd == 5) ? ControlFlowType.CALL : ControlFlowType.UNCONDITIONAL_JUMP;
        AssignExpr semantic = new AssignExpr(
                new RegisterExpr(reg(rd)),
                new ImmediateExpr(pc + 4) // Return address
        );
        return ir(pc, word, "jal", ops(reg(rd), hex(target)), "J", type, target, sectionName, semantic);
    }

    /**
     * Decodes a {@code jalr} instruction and classifies it as call, return, or
     * jump.
     *
     * @param pc          current instruction address
     * @param word        raw 32-bit word
     * @param rd          destination register
     * @param rs1         base register
     * @param funct3      function field that must be zero for {@code jalr}
     * @param sectionName owning section name
     * @return decoded instruction, or a raw word when the encoding is invalid
     */
    private InstructionIr decodeJalr(long pc, int word, int rd, int rs1, int funct3, String sectionName) {
        if (funct3 != 0) {
            return raw(pc, word, sectionName, null);
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
        // jalr writes return address (PC+4) to rd, target is rs1 + imm
        // Use $target to represent the computed target address for indirect branch
        // analysis
        AssignExpr semantic = new AssignExpr(
                new RegisterExpr("$target"),
                new BinaryOpExpr(Operator.ADD, new RegisterExpr(reg(rs1)), new ImmediateExpr(imm)));
        return ir(pc, word, "jalr", ops(reg(rd), imm + "(" + reg(rs1) + ")"), "I", type, null, sectionName, semantic);
    }

    /**
     * Decodes a conditional branch instruction.
     *
     * @param pc          current instruction address
     * @param word        raw 32-bit word
     * @param rs1         first source register
     * @param rs2         second source register
     * @param funct3      branch subtype selector
     * @param sectionName owning section name
     * @return decoded branch instruction, or a raw word for unsupported variants
     */
    private InstructionIr decodeBranch(long pc, int word, int rs1, int rs2, int funct3, String sectionName) {
        String mnemonic;
        Operator op;
        switch (funct3) {
            case 0:
                mnemonic = "beq";
                op = Operator.EQUAL;
                break;
            case 1:
                mnemonic = "bne";
                op = Operator.NOT_EQUAL;
                break;
            case 4:
                mnemonic = "blt";
                op = Operator.LESS_THAN;
                break;
            case 5:
                mnemonic = "bge";
                op = Operator.GREATER_EQUAL;
                break;
            case 6:
                mnemonic = "bltu";
                op = Operator.LESS_THAN_UNSIGNED;
                break;
            case 7:
                mnemonic = "bgeu";
                op = Operator.GREATER_EQUAL_UNSIGNED;
                break;
            default:
                mnemonic = RAW_WORD_MNEMONIC;
                op = null;
                break;
        }
        if (mnemonic.equals(RAW_WORD_MNEMONIC)) {
            return raw(pc, word, sectionName, null);
        }
        long target = (pc + immB(word)) & 0xFFFFFFFFL;
        AssignExpr semantic = new AssignExpr(
                new RegisterExpr("$cond"),
                new BinaryOpExpr(op, new RegisterExpr(reg(rs1)), new RegisterExpr(reg(rs2))));
        return ir(pc, word, mnemonic, ops(reg(rs1), reg(rs2), hex(target)), "B", ControlFlowType.CONDITIONAL_BRANCH,
                target, sectionName, semantic);
    }

    /**
     * Decodes a load instruction.
     *
     * @param pc          current instruction address
     * @param word        raw 32-bit word
     * @param rd          destination register
     * @param rs1         base register
     * @param funct3      load subtype selector
     * @param sectionName owning section name
     * @return decoded load instruction, or a raw word for unsupported variants
     */
    private InstructionIr decodeLoad(long pc, int word, int rd, int rs1, int funct3, String sectionName) {
        String mnemonic;
        int sizeBytes;
        switch (funct3) {
            case 0:
                mnemonic = "lb";
                sizeBytes = 1;
                break;
            case 1:
                mnemonic = "lh";
                sizeBytes = 2;
                break;
            case 2:
                mnemonic = "lw";
                sizeBytes = 4;
                break;
            case 4:
                mnemonic = "lbu";
                sizeBytes = 1;
                break;
            case 5:
                mnemonic = "lhu";
                sizeBytes = 2;
                break;
            default:
                mnemonic = RAW_WORD_MNEMONIC;
                sizeBytes = 0;
                break;
        }
        if (mnemonic.equals(RAW_WORD_MNEMONIC)) {
            return raw(pc, word, sectionName, null);
        }
        int offset = immI(word);
        AssignExpr semantic = new AssignExpr(
                new RegisterExpr(reg(rd)),
                new MemoryLoadExpr(
                        new BinaryOpExpr(Operator.ADD, new RegisterExpr(reg(rs1)), new ImmediateExpr(offset)),
                        sizeBytes));
        return ir(pc, word, mnemonic, ops(reg(rd), offset + "(" + reg(rs1) + ")"), "I", ControlFlowType.NORMAL,
                null, sectionName, semantic);
    }

    /**
     * Decodes a store instruction.
     *
     * @param pc          current instruction address
     * @param word        raw 32-bit word
     * @param rs1         base register
     * @param rs2         value register
     * @param funct3      store subtype selector
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
                mnemonic = RAW_WORD_MNEMONIC;
                break;
        }
        if (mnemonic.equals(RAW_WORD_MNEMONIC)) {
            return raw(pc, word, sectionName, null);
        }
        // Store instructions have no rd, so semantic = null
        return ir(pc, word, mnemonic, ops(reg(rs2), immS(word) + "(" + reg(rs1) + ")"), "S", ControlFlowType.NORMAL,
                null, sectionName, null);
    }

    /**
     * Decodes an immediate arithmetic or logical instruction.
     *
     * @param pc          current instruction address
     * @param word        raw 32-bit word
     * @param rd          destination register
     * @param rs1         source register
     * @param funct3      subtype selector
     * @param funct7      upper function bits used by shift variants
     * @param sectionName owning section name
     * @return decoded instruction, or a raw word when unsupported
     */
    private InstructionIr decodeOpImm(long pc, int word, int rd, int rs1, int funct3, int funct7, String sectionName) {
        String mnemonic;
        List<String> operands;
        AssignExpr semantic = null;
        switch (funct3) {
            case 0: {
                mnemonic = "addi";
                int imm = immI(word);
                operands = ops(reg(rd), reg(rs1), Integer.toString(imm));
                semantic = new AssignExpr(
                        new RegisterExpr(reg(rd)),
                        new BinaryOpExpr(Operator.ADD, new RegisterExpr(reg(rs1)), new ImmediateExpr(imm)));
                break;
            }
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
                    return raw(pc, word, sectionName, null);
                }
                mnemonic = "slli";
                int shamt = (word >>> 20) & 0x1F;
                operands = ops(reg(rd), reg(rs1), Integer.toString(shamt));
                semantic = new AssignExpr(
                        new RegisterExpr(reg(rd)),
                        new BinaryOpExpr(Operator.SHIFT_LEFT, new RegisterExpr(reg(rs1)), new ImmediateExpr(shamt)));
                break;
            case 5:
                if (funct7 == 0x00) {
                    mnemonic = "srli";
                } else if (funct7 == 0x20) {
                    mnemonic = "srai";
                } else {
                    return raw(pc, word, sectionName, null);
                }
                operands = ops(reg(rd), reg(rs1), Integer.toString((word >>> 20) & 0x1F));
                break;
            default:
                return raw(pc, word, sectionName, null);
        }
        return ir(pc, word, mnemonic, operands, "I", ControlFlowType.NORMAL, null, sectionName, semantic);
    }

    /**
     * Decodes a register-register arithmetic or logical instruction.
     *
     * @param pc          current instruction address
     * @param word        raw 32-bit word
     * @param rd          destination register
     * @param rs1         first source register
     * @param rs2         second source register
     * @param funct3      subtype selector
     * @param funct7      upper function bits
     * @param sectionName owning section name
     * @return decoded instruction, or a raw word when unsupported
     */
    private InstructionIr decodeOp(long pc, int word, int rd, int rs1, int rs2, int funct3, int funct7,
            String sectionName) {
        String mnemonic;
        AssignExpr semantic = null;
        switch (funct3) {
            case 0:
                if (funct7 == 0x00) {
                    mnemonic = "add";
                    semantic = new AssignExpr(
                            new RegisterExpr(reg(rd)),
                            new BinaryOpExpr(Operator.ADD, new RegisterExpr(reg(rs1)), new RegisterExpr(reg(rs2))));
                } else if (funct7 == 0x20) {
                    mnemonic = "sub";
                } else {
                    mnemonic = RAW_WORD_MNEMONIC;
                }
                break;
            case 1:
                mnemonic = funct7 == 0x00 ? "sll" : RAW_WORD_MNEMONIC;
                break;
            case 2:
                mnemonic = funct7 == 0x00 ? "slt" : RAW_WORD_MNEMONIC;
                break;
            case 3:
                mnemonic = funct7 == 0x00 ? "sltu" : RAW_WORD_MNEMONIC;
                break;
            case 4:
                mnemonic = funct7 == 0x00 ? "xor" : RAW_WORD_MNEMONIC;
                break;
            case 5:
                if (funct7 == 0x00) {
                    mnemonic = "srl";
                } else if (funct7 == 0x20) {
                    mnemonic = "sra";
                } else {
                    mnemonic = RAW_WORD_MNEMONIC;
                }
                break;
            case 6:
                mnemonic = funct7 == 0x00 ? "or" : RAW_WORD_MNEMONIC;
                break;
            case 7:
                mnemonic = funct7 == 0x00 ? "and" : RAW_WORD_MNEMONIC;
                break;
            default:
                mnemonic = RAW_WORD_MNEMONIC;
                break;
        }
        if (mnemonic.equals(RAW_WORD_MNEMONIC)) {
            return raw(pc, word, sectionName, null);
        }
        return ir(pc, word, mnemonic, ops(reg(rd), reg(rs1), reg(rs2)), "R", ControlFlowType.NORMAL, null, sectionName,
                semantic);
    }

    /**
     * Decodes system and CSR instructions.
     *
     * @param pc          current instruction address
     * @param word        raw 32-bit word
     * @param rd          destination register
     * @param rs1         source register or immediate carrier depending on the CSR
     *                    variant
     * @param funct3      subtype selector
     * @param sectionName owning section name
     * @return decoded instruction, or a raw word when unsupported
     */
    private InstructionIr decodeSystem(long pc, int word, int rd, int rs1, int funct3, String sectionName) {
        if (funct3 == 0) {
            if ((word >>> 20) == 0) {
                // ecall has no rd, so semantic = null
                return ir(pc, word, "ecall", noOps(), "I", ControlFlowType.TERMINATOR, null, sectionName, null);
            }
            if ((word >>> 20) == 1) {
                // ebreak has no rd, so semantic = null
                return ir(pc, word, "ebreak", noOps(), "I", ControlFlowType.TERMINATOR, null, sectionName, null);
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
                mnemonic = RAW_WORD_MNEMONIC;
                break;
        }
        if (mnemonic.equals(RAW_WORD_MNEMONIC)) {
            return raw(pc, word, sectionName, null);
        }
        int csr = (word >>> 20) & 0xFFF;
        // CSR instructions have rd, use UnknownExpr for semantic
        AssignExpr semantic = new AssignExpr(
                new RegisterExpr(reg(rd)),
                new UnknownExpr());
        return ir(pc, word, mnemonic, ops(reg(rd), String.format("0x%03x", csr), reg(rs1)), "I", ControlFlowType.NORMAL,
                null, sectionName, semantic);
    }

    /**
     * Creates a decoded instruction object.
     *
     * @param address         instruction address
     * @param rawInstruction  raw 32-bit word
     * @param mnemonic        decoded mnemonic
     * @param operands        textual operands
     * @param format          encoding family label
     * @param controlFlowType semantic control-flow category
     * @param branchTarget    direct target address when known
     * @param sectionName     owning section name
     * @param semantic        semantic AST representation
     * @return instruction IR instance
     */
    private static InstructionIr ir(long address, int rawInstruction, String mnemonic, List<String> operands,
            String format,
            ControlFlowType controlFlowType, Long branchTarget, String sectionName, AssignExpr semantic) {
        return new InstructionIr(address, rawInstruction, mnemonic, operands, format, controlFlowType, branchTarget,
                sectionName, semantic);
    }

    /**
     * Creates a fallback IR node for unsupported or undecodable words.
     *
     * @param address        instruction address
     * @param rawInstruction raw 32-bit word
     * @param sectionName    owning section name
     * @param semantic       semantic AST representation (null for instructions
     *                       without rd)
     * @return pseudo-instruction that preserves the raw word as data
     */
    private static InstructionIr raw(long address, int rawInstruction, String sectionName, AssignExpr semantic) {
        return ir(address, rawInstruction, RAW_WORD_MNEMONIC, ops(hex(Integer.toUnsignedLong(rawInstruction))), "RAW",
                ControlFlowType.NORMAL, null, sectionName, semantic);
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
     * @param bytes  section bytes
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
     * @param bits  number of significant bits in {@code value}
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
