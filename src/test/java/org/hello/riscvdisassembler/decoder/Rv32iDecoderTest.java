package org.hello.riscvdisassembler.decoder;

import org.hello.riscvdisassembler.TestPaths;
import org.hello.riscvdisassembler.elf.ElfLoader;
import org.hello.riscvdisassembler.ir.InstructionIr;
import org.hello.riscvdisassembler.resolver.ResolvedProgram;
import org.hello.riscvdisassembler.resolver.SectionSymbolResolver;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class Rv32iDecoderTest {
    private final ElfLoader loader = new ElfLoader();
    private final SectionSymbolResolver resolver = new SectionSymbolResolver();
    private final Rv32iDecoder decoder = new Rv32iDecoder();

    @Test
    void decodeSampleProgramIntoExpectedInstructions() throws IOException {
        ResolvedProgram program = resolver.resolve(loader.load(TestPaths.sampleElf()));

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
