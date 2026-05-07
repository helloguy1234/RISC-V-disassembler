package org.hello.riscvdisassembler.core.decode;

import org.hello.riscvdisassembler.core.binary.model.BinaryImage;
import org.hello.riscvdisassembler.core.binary.model.BinarySection;
import org.hello.riscvdisassembler.core.decode.model.InstructionIr;
import org.hello.riscvdisassembler.core.resolve.ResolvedProgram;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class Rv32iDecoderExtendedTest {

    private Rv32iDecoder decoder;

    @BeforeEach
    void setUp() {
        decoder = new Rv32iDecoder();
    }

    private InstructionIr decodeSingleInstruction(int instructionWord) {
        final long baseAddress = 0x1000;
        final String sectionName = ".text";

        // Create a dummy binary image with just our instruction
        byte[] bytes = new byte[] {
                (byte) (instructionWord),
                (byte) (instructionWord >> 8),
                (byte) (instructionWord >> 16),
                (byte) (instructionWord >> 24)
        };

        BinarySection section = new BinarySection(1, sectionName, baseAddress, 0, bytes.length, true);
        List<BinarySection> sections = Collections.singletonList(section);

        BinaryImage image = new BinaryImage(baseAddress, bytes, sections, Collections.emptyList());

        // Create a ResolvedProgram
        ResolvedProgram program = new ResolvedProgram(image, sections, Collections.emptyNavigableMap(),
                Collections.emptyMap());

        return decoder.decodeAt(program, section, baseAddress);
    }

    @Test
    void testLui() {
        // lui a0, 0xdeadb => 0xdeadb000
        // opcode=0x37, rd=10 (a0), imm=0xdeadb
        int instruction = 0xdeadb537;
        InstructionIr ir = decodeSingleInstruction(instruction);

        assertEquals("lui", ir.mnemonic());
        assertEquals(List.of("a0", "0xdeadb000"), ir.operands());
        assertEquals("U", ir.format());
        assertEquals(InstructionIr.ControlFlowType.NORMAL, ir.controlFlowType());
        assertEquals(null, ir.branchTarget());
    }

    @Test
    void testAuipc() {
        // auipc t0, 0xabcde
        // opcode=0x17, rd=5 (t0), imm=0xabcde
        int instruction = 0xabcde297;
        InstructionIr ir = decodeSingleInstruction(instruction);

        assertEquals("auipc", ir.mnemonic());
        assertEquals(List.of("t0", "0xabcde000"), ir.operands());
        assertEquals("U", ir.format());
    }

    @Test
    void testJal() {
        // jal ra, 0x20 (decimal) => 0x1000 + 20 = 0x1014
        // opcode=0x6F, rd=1 (ra), imm=20
        int instruction = 0x014000ef;
        InstructionIr ir = decodeSingleInstruction(instruction);

        assertEquals("jal", ir.mnemonic());
        assertEquals(List.of("ra", "0x00001014"), ir.operands());
        assertEquals("J", ir.format());
        assertEquals(InstructionIr.ControlFlowType.CALL, ir.controlFlowType());
        assertEquals(0x1014L, ir.branchTarget());
    }

    @Test
    void testJalrReturn() {
        // jalr zero, ra, 0 => ret
        // opcode=0x67, rd=0, rs1=1, funct3=0, imm=0
        int instruction = 0x00008067;
        InstructionIr ir = decodeSingleInstruction(instruction);

        assertEquals("jalr", ir.mnemonic());
        assertEquals(List.of("zero", "0(ra)"), ir.operands());
        assertEquals("I", ir.format());
        assertEquals(InstructionIr.ControlFlowType.RETURN, ir.controlFlowType());
    }

    @Test
    void testJalrCall() {
        // jalr ra, t2, 8
        // opcode=0x67, rd=1, rs1=7, funct3=0, imm=8
        int instruction = 0x008380e7;
        InstructionIr ir = decodeSingleInstruction(instruction);

        assertEquals("jalr", ir.mnemonic());
        assertEquals(List.of("ra", "8(t2)"), ir.operands());
        assertEquals("I", ir.format());
        assertEquals(InstructionIr.ControlFlowType.CALL, ir.controlFlowType());
    }

    @Test
    void testJalrJump() {
        // jalr zero, t1, 4 (Nhảy vô điều kiện, không lưu địa chỉ quay về)
        // opcode=0x67, rd=0, rs1=6, funct3=0, imm=4
        int instruction = 0x00430067;
        InstructionIr ir = decodeSingleInstruction(instruction);

        assertEquals("jalr", ir.mnemonic());
        assertEquals(List.of("zero", "4(t1)"), ir.operands());
        assertEquals("I", ir.format());
        assertEquals(InstructionIr.ControlFlowType.UNCONDITIONAL_JUMP, ir.controlFlowType());
    }

    @Test
    void testBne() {
        // bne a0, a1, -8 => 0x1000 - 8 = 0xff8
        // opcode=0x63, rs1=10, rs2=11, funct3=1, imm=-8
        int instruction = 0xfeb51ce3;
        InstructionIr ir = decodeSingleInstruction(instruction);

        assertEquals("bne", ir.mnemonic());
        assertEquals(List.of("a0", "a1", "0x00000ff8"), ir.operands());
        assertEquals("B", ir.format());
        assertEquals(InstructionIr.ControlFlowType.CONDITIONAL_BRANCH, ir.controlFlowType());
        assertEquals(0xff8L, ir.branchTarget());
    }

    @Test
    void testLw() {
        // lw a2, 12(sp)
        // opcode=0x03, rd=12, rs1=2, funct3=2, imm=12
        int instruction = 0x00c12603;
        InstructionIr ir = decodeSingleInstruction(instruction);

        assertEquals("lw", ir.mnemonic());
        assertEquals(List.of("a2", "12(sp)"), ir.operands());
        assertEquals("I", ir.format());
    }

    @Test
    void testSw() {
        // sw a3, 16(sp)
        // opcode=0x23, rs1=2, rs2=13, funct3=2, imm=16
        int instruction = 0x00d12823;
        InstructionIr ir = decodeSingleInstruction(instruction);

        assertEquals("sw", ir.mnemonic());
        assertEquals(List.of("a3", "16(sp)"), ir.operands());
        assertEquals("S", ir.format());
    }

    @Test
    void testAddi() {
        // addi a4, a4, -1
        // opcode=0x13, rd=14, rs1=14, funct3=0, imm=-1
        int instruction = 0xfff70713;
        InstructionIr ir = decodeSingleInstruction(instruction);

        assertEquals("addi", ir.mnemonic());
        assertEquals(List.of("a4", "a4", "-1"), ir.operands());
        assertEquals("I", ir.format());
    }

    @Test
    void testAdd() {
        // add t0, t1, t2
        // opcode=0x33, rd=5, rs1=6, rs2=7, funct3=0, funct7=0x00
        int instruction = 0x007302b3;
        InstructionIr ir = decodeSingleInstruction(instruction);

        assertEquals("add", ir.mnemonic());
        assertEquals(List.of("t0", "t1", "t2"), ir.operands());
        assertEquals("R", ir.format());
    }

    @Test
    void testSll() {
        // sll t0, t1, t2
        // opcode=0x33, rd=5, rs1=6, rs2=7, funct3=1, funct7=0x00
        int instruction = 0x007312b3;
        InstructionIr ir = decodeSingleInstruction(instruction);

        assertEquals("sll", ir.mnemonic());
        assertEquals(List.of("t0", "t1", "t2"), ir.operands());
        assertEquals("R", ir.format());
    }

    @Test
    void testSlt() {
        // slt t0, t1, t2
        // opcode=0x33, rd=5, rs1=6, rs2=7, funct3=2, funct7=0x00
        int instruction = 0x007322b3;
        InstructionIr ir = decodeSingleInstruction(instruction);

        assertEquals("slt", ir.mnemonic());
        assertEquals(List.of("t0", "t1", "t2"), ir.operands());
        assertEquals("R", ir.format());
    }

    @Test
    void testSltu() {
        // sltu t0, t1, t2
        // opcode=0x33, rd=5, rs1=6, rs2=7, funct3=3, funct7=0x00
        int instruction = 0x007332b3;
        InstructionIr ir = decodeSingleInstruction(instruction);

        assertEquals("sltu", ir.mnemonic());
        assertEquals(List.of("t0", "t1", "t2"), ir.operands());
        assertEquals("R", ir.format());
    }

    @Test
    void testXor() {
        // xor t0, t1, t2
        // opcode=0x33, rd=5, rs1=6, rs2=7, funct3=4, funct7=0x00
        int instruction = 0x007342b3;
        InstructionIr ir = decodeSingleInstruction(instruction);

        assertEquals("xor", ir.mnemonic());
        assertEquals(List.of("t0", "t1", "t2"), ir.operands());
        assertEquals("R", ir.format());
    }

    @Test
    void testSrl() {
        // srl t0, t1, t2
        // opcode=0x33, rd=5, rs1=6, rs2=7, funct3=5, funct7=0x00
        int instruction = 0x007352b3;
        InstructionIr ir = decodeSingleInstruction(instruction);

        assertEquals("srl", ir.mnemonic());
        assertEquals(List.of("t0", "t1", "t2"), ir.operands());
        assertEquals("R", ir.format());
    }

    @Test
    void testSra() {
        // sra t0, t1, t2
        // opcode=0x33, rd=5, rs1=6, rs2=7, funct3=5, funct7=0x20
        int instruction = 0x407352b3;
        InstructionIr ir = decodeSingleInstruction(instruction);

        assertEquals("sra", ir.mnemonic());
        assertEquals(List.of("t0", "t1", "t2"), ir.operands());
        assertEquals("R", ir.format());
    }

    @Test
    void testOr() {
        // or t0, t1, t2
        // opcode=0x33, rd=5, rs1=6, rs2=7, funct3=6, funct7=0x00
        int instruction = 0x007362b3;
        InstructionIr ir = decodeSingleInstruction(instruction);

        assertEquals("or", ir.mnemonic());
        assertEquals(List.of("t0", "t1", "t2"), ir.operands());
        assertEquals("R", ir.format());
    }

    @Test
    void testAnd() {
        // and t0, t1, t2
        // opcode=0x33, rd=5, rs1=6, rs2=7, funct3=7, funct7=0x00
        int instruction = 0x007372b3;
        InstructionIr ir = decodeSingleInstruction(instruction);

        assertEquals("and", ir.mnemonic());
        assertEquals(List.of("t0", "t1", "t2"), ir.operands());
        assertEquals("R", ir.format());
    }

    @Test
    void testSub() {
        // sub a5, a5, a0
        // opcode=0x33, rd=15, rs1=15, rs2=10, funct3=0, funct7=0x20
        int instruction = 0x40a787b3;
        InstructionIr ir = decodeSingleInstruction(instruction);

        assertEquals("sub", ir.mnemonic());
        assertEquals(List.of("a5", "a5", "a0"), ir.operands());
        assertEquals("R", ir.format());
    }

    @Test
    void testInvalidRTypeFunct7() {
        // opcode=0x33, rd=5, rs1=6, rs2=7, funct3=0, funct7=0x01 (Không hợp lệ cho lệnh add)
        int instruction = 0x027302b3;
        InstructionIr ir = decodeSingleInstruction(instruction);

        assertEquals(".word", ir.mnemonic());
        assertEquals(List.of("0x027302b3"), ir.operands());
        assertEquals("RAW", ir.format());
    }

    @Test
    void testInvalidInstruction() {
        // An all-zero instruction is not a valid RV32I instruction.
        int instruction = 0x00000000;
        InstructionIr ir = decodeSingleInstruction(instruction);

        assertEquals(".word", ir.mnemonic());
        assertEquals(List.of("0x00000000"), ir.operands());
        assertEquals("RAW", ir.format());
    }
}
