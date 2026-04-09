package org.hello.riscvdisassembler.emit;

import org.hello.riscvdisassembler.TestPaths;
import org.hello.riscvdisassembler.decoder.Rv32iDecoder;
import org.hello.riscvdisassembler.elf.ElfLoader;
import org.hello.riscvdisassembler.ir.InstructionIr;
import org.hello.riscvdisassembler.resolver.ResolvedProgram;
import org.hello.riscvdisassembler.resolver.SectionSymbolResolver;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmittersTest {
    private final ElfLoader loader = new ElfLoader();
    private final SectionSymbolResolver resolver = new SectionSymbolResolver();
    private final Rv32iDecoder decoder = new Rv32iDecoder();
    private final TextEmitter textEmitter = new TextEmitter();
    private final JsonEmitter jsonEmitter = new JsonEmitter();
    private final HeaderEmitter headerEmitter = new HeaderEmitter();

    @Test
    void textEmitterRendersSampleLabelsAndInstructions() throws IOException {
        ResolvedProgram program = resolver.resolve(loader.load(TestPaths.sampleElf()));
        List<InstructionIr> instructions = decoder.decode(program);

        String output = textEmitter.emit(program, instructions);

        assertTrue(output.contains("; entry = 0x00000000"));
        assertTrue(output.contains("_start:"));
        assertTrue(output.contains("loop:"));
        assertTrue(output.contains("addi ra, zero, 5"));
        assertTrue(output.contains("beq zero, zero, 0x0000000c"));
    }

    @Test
    void textEmitterAvoidsCrossSectionLabelLeakWhenDisassemblingAll() throws IOException {
        ResolvedProgram program = resolver.resolve(loader.load(TestPaths.sampleElf()), true);
        List<InstructionIr> instructions = decoder.decode(program);

        String output = textEmitter.emit(program, instructions);

        assertTrue(output.contains(".symtab:"));
        assertFalse(output.contains(".symtab:" + System.lineSeparator() + "_start:"));
    }

    @Test
    void jsonEmitterIncludesDecodedBranchTarget() throws IOException {
        ResolvedProgram program = resolver.resolve(loader.load(TestPaths.sampleElf()));
        List<InstructionIr> instructions = decoder.decode(program);

        String json = jsonEmitter.emit(program, instructions);

        assertTrue(json.contains("\"mnemonic\": \"beq\""));
        assertTrue(json.contains("\"branchTarget\": \"0x0000000c\""));
    }

    @Test
    void headerEmitterRendersParsedHeader() throws IOException {
        String output = headerEmitter.emit(loader.loadHeader(TestPaths.sampleElf()));

        assertTrue(output.contains("ELF header (lenient parse)"));
        assertTrue(output.contains("Class: 1 (ELF32)"));
        assertTrue(output.contains("Machine: 243"));
    }
}
