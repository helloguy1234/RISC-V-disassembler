package org.hello.riscvdisassembler.features.text;

import org.hello.riscvdisassembler.TestPaths;
import org.hello.riscvdisassembler.core.decode.Rv32iDecoder;
import org.hello.riscvdisassembler.core.discover.CodeDiscoveryEngine;
import org.hello.riscvdisassembler.core.discover.DiscoveredProgram;
import org.hello.riscvdisassembler.core.discover.DiscoveryMode;
import org.hello.riscvdisassembler.adapters.input.elf.ElfLoader;
import org.hello.riscvdisassembler.core.resolve.ResolvedProgram;
import org.hello.riscvdisassembler.core.resolve.SectionSymbolResolver;
import org.hello.riscvdisassembler.features.header.HeaderEmitter;
import org.hello.riscvdisassembler.features.json.JsonEmitter;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmittersTest {
    private final SectionSymbolResolver resolver = new SectionSymbolResolver();
    private final CodeDiscoveryEngine discoveryEngine = new CodeDiscoveryEngine(new Rv32iDecoder());
    private final TextEmitter textEmitter = new TextEmitter();
    private final JsonEmitter jsonEmitter = new JsonEmitter();
    private final HeaderEmitter headerEmitter = new HeaderEmitter();

    @Test
    void textEmitterRendersSampleLabelsAndInstructions() throws IOException {
        ResolvedProgram program = resolver.resolve(TestPaths.sampleBinaryImage());
        DiscoveredProgram discovered = discoveryEngine.discover(program, DiscoveryMode.RECURSIVE);

        String output = textEmitter.emit(discovered);

        assertTrue(output.contains("; mode = recursive"));
        assertTrue(output.contains("; entry = 0x00000000"));
        assertTrue(output.contains("_start:"));
        assertTrue(output.contains("loop:"));
        assertTrue(output.contains("addi ra, zero, 5"));
        assertTrue(output.contains("beq zero, zero, 0x0000000c"));
    }

    @Test
    void textEmitterAvoidsCrossSectionLabelLeakWhenDisassemblingAll() throws IOException {
        ResolvedProgram program = resolver.resolve(TestPaths.sampleBinaryImage(), true);
        DiscoveredProgram discovered = discoveryEngine.discover(program, DiscoveryMode.LINEAR);

        String output = textEmitter.emit(discovered);

        assertTrue(output.contains(".symtab:"));
        assertFalse(output.contains(".symtab:" + System.lineSeparator() + "_start:"));
    }

    @Test
    void jsonEmitterIncludesDecodedBranchTarget() throws IOException {
        ResolvedProgram program = resolver.resolve(TestPaths.sampleBinaryImage());
        DiscoveredProgram discovered = discoveryEngine.discover(program, DiscoveryMode.RECURSIVE);

        String json = jsonEmitter.emit(discovered);

        assertTrue(json.contains("\"discoveryMode\": \"RECURSIVE\""));
        assertTrue(json.contains("\"regions\": ["));
        assertTrue(json.contains("\"edges\": ["));
        assertTrue(json.contains("\"mnemonic\": \"beq\""));
        assertTrue(json.contains("\"branchTarget\": \"0x0000000c\""));
    }

    @Test
    void headerEmitterRendersParsedHeader() throws IOException {
        String output = headerEmitter.emit(new ElfLoader().loadHeader(TestPaths.sampleElf()));

        assertTrue(output.contains("ELF header (lenient parse)"));
        assertTrue(output.contains("Class: 1 (ELF32)"));
        assertTrue(output.contains("Machine: 243"));
    }

    @Test
    void textEmitterRendersDataRegionsForRecursiveInlineDataSample() throws IOException {
        ResolvedProgram program = resolver.resolve(TestPaths.codeAndDataTogetherBinaryImage());
        DiscoveredProgram discovered = discoveryEngine.discover(program, DiscoveryMode.RECURSIVE);

        String output = textEmitter.emit(discovered);

        assertTrue(output.contains("; data region (unreachable-gap)"));
        assertTrue(output.contains(".word 0xdeadbeef"));
        assertFalse(output.contains("0x80000010  0xdeadbeef  jal"));
    }

    @Test
    void textEmitterRendersLinearMode() throws IOException {
        ResolvedProgram program = resolver.resolve(TestPaths.sampleBinaryImage());
        DiscoveredProgram discovered = discoveryEngine.discover(program, DiscoveryMode.LINEAR);

        String output = textEmitter.emit(discovered);

        assertTrue(output.contains("; mode = linear"));
        assertTrue(output.contains("; entry = 0x00000000"));
    }

    @Test
    void textEmitterRendersMultipleSections() throws IOException {
        ResolvedProgram program = resolver.resolve(TestPaths.sampleBinaryImage(), true);
        DiscoveredProgram discovered = discoveryEngine.discover(program, DiscoveryMode.LINEAR);

        String output = textEmitter.emit(discovered);

        assertTrue(output.contains(".text:"));
        assertTrue(output.contains(".symtab:"));
    }

    @Test
    void textEmitterRendersInstructionWithOperands() throws IOException {
        ResolvedProgram program = resolver.resolve(TestPaths.sampleBinaryImage());
        DiscoveredProgram discovered = discoveryEngine.discover(program, DiscoveryMode.RECURSIVE);

        String output = textEmitter.emit(discovered);

        assertTrue(output.contains("addi ra, zero, 5"));
        assertTrue(output.contains("add gp, ra, sp"));
    }

    @Test
    void textEmitterRendersInstructionAddress() throws IOException {
        ResolvedProgram program = resolver.resolve(TestPaths.sampleBinaryImage());
        DiscoveredProgram discovered = discoveryEngine.discover(program, DiscoveryMode.RECURSIVE);

        String output = textEmitter.emit(discovered);

        assertTrue(output.contains("0x00000000"));
    }
}
