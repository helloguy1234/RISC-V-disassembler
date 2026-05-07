package org.hello.riscvdisassembler.core.decode.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class InstructionIrTest {

    @Test
    void testInstructionIrGetters() {
        InstructionIr instruction = new InstructionIr(
                0x1000,
                0x00000013,
                "addi",
                List.of("x0", "x0", "0"),
                "I",
                InstructionIr.ControlFlowType.NORMAL,
                null,
                ".text"
        );

        assertEquals(0x1000, instruction.address());
        assertEquals(0x00000013, instruction.rawInstruction());
        assertEquals("addi", instruction.mnemonic());
        assertEquals(List.of("x0", "x0", "0"), instruction.operands());
        assertEquals("I", instruction.format());
        assertEquals(InstructionIr.ControlFlowType.NORMAL, instruction.controlFlowType());
        assertNull(instruction.branchTarget());
        assertEquals(".text", instruction.sectionName());
    }
    
    @Test
    void testInstructionIrWithBranchTarget() {
        InstructionIr instruction = new InstructionIr(
                0x1004,
                0x0040006f,
                "j",
                List.of("0x1008"),
                "J",
                InstructionIr.ControlFlowType.UNCONDITIONAL_JUMP,
                0x1008L,
                ".text"
        );

        assertEquals(InstructionIr.ControlFlowType.UNCONDITIONAL_JUMP, instruction.controlFlowType());
        assertEquals(0x1008L, instruction.branchTarget());
    }
}
