package org.hello.riscvdisassembler.core.discover;

import org.hello.riscvdisassembler.TestPaths;
import org.hello.riscvdisassembler.core.decode.Rv32iDecoder;
import org.hello.riscvdisassembler.core.resolve.ResolvedProgram;
import org.hello.riscvdisassembler.core.resolve.SectionSymbolResolver;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CodeDiscoveryEngineTest {
    private final SectionSymbolResolver resolver = new SectionSymbolResolver();
    private final CodeDiscoveryEngine discoveryEngine = new CodeDiscoveryEngine(new Rv32iDecoder());

    @Test
    void recursiveModeKeepsOnlyReachableInstructions() throws IOException {
        ResolvedProgram program = resolver.resolve(TestPaths.sampleBinaryImage());

        DiscoveredProgram discovered = discoveryEngine.discover(program, DiscoveryMode.RECURSIVE);

        assertEquals(4, discovered.instructions().size());
        assertEquals("addi", discovered.instructions().getFirst().mnemonic());
        assertEquals("beq", discovered.instructions().getLast().mnemonic());
        assertTrue(discovered.edges().stream().anyMatch(edge -> edge.from() == 12L && edge.to() == 12L));
    }

    @Test
    void linearModeStillSupportsDisassembleAllInspection() throws IOException {
        ResolvedProgram program = resolver.resolve(TestPaths.sampleBinaryImage(), true);

        DiscoveredProgram discovered = discoveryEngine.discover(program, DiscoveryMode.LINEAR);

        assertTrue(discovered.instructions().stream().anyMatch(instruction -> ".symtab".equals(instruction.sectionName())));
    }

    @Test
    void recursiveModeDoesNotSeedDataLabelsInsideText() throws IOException {
        ResolvedProgram program = resolver.resolve(TestPaths.codeAndDataTogetherBinaryImage());

        DiscoveredProgram discovered = discoveryEngine.discover(program, DiscoveryMode.RECURSIVE);

        assertTrue(discovered.instructions().stream().anyMatch(instruction -> instruction.address() == 0x8000001cL));
        assertFalse(discovered.instructions().stream().anyMatch(instruction -> instruction.address() == 0x80000010L));
        assertFalse(discovered.instructions().stream().anyMatch(instruction -> instruction.address() == 0x80000014L));
        assertFalse(discovered.instructions().stream().anyMatch(instruction -> instruction.address() == 0x80000018L));
        assertTrue(discovered.regions().stream().anyMatch(region ->
                region.kind() == RegionKind.DATA
                        && region.sectionName().equals(".text")
                        && region.start() == 0x80000010L
                        && region.end() == 0x8000001cL));
    }
}

