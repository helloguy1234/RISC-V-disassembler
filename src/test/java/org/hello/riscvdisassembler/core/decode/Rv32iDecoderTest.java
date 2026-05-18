package org.hello.riscvdisassembler.core.decode;

import org.hello.riscvdisassembler.TestPaths;
import org.hello.riscvdisassembler.core.decode.model.InstructionIr;
import org.hello.riscvdisassembler.core.resolve.ResolvedProgram;
import org.hello.riscvdisassembler.core.resolve.SectionSymbolResolver;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class Rv32iDecoderTest {
    private final SectionSymbolResolver resolver = new SectionSymbolResolver();
    private final Rv32iDecoder decoder = new Rv32iDecoder();

    @Test
    void decodeSampleProgramIntoExpectedInstructions() throws IOException {
        ResolvedProgram program = resolver.resolve(TestPaths.sampleBinaryImage());

        List<InstructionIr> instructions = decoder.decode(program);

        assertEquals(4, instructions.size());

        assertEquals("addi", instructions.get(0).mnemonic());
        assertEquals(List.of("ra", "zero", "5"), instructions.get(0).operands());
        assertEquals("I", instructions.get(0).format());
        assertNull(instructions.get(0).branchTarget());

        assertEquals("add", instructions.get(2).mnemonic());
        assertEquals(List.of("gp", "ra", "sp"), instructions.get(2).operands());

        assertEquals("beq", instructions.get(3).mnemonic());
        assertEquals(InstructionIr.ControlFlowType.CONDITIONAL_BRANCH, instructions.get(3).controlFlowType());
        assertEquals(12L, instructions.get(3).branchTarget());
    }
}

