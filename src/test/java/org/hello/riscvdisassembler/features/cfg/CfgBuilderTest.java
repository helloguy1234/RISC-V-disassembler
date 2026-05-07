package org.hello.riscvdisassembler.features.cfg;

import org.hello.riscvdisassembler.TestPaths;
import org.hello.riscvdisassembler.core.discover.CodeDiscoveryEngine;
import org.hello.riscvdisassembler.core.discover.DiscoveredProgram;
import org.hello.riscvdisassembler.core.discover.DiscoveryMode;
import org.hello.riscvdisassembler.core.decode.Rv32iDecoder;
import org.hello.riscvdisassembler.core.resolve.ResolvedProgram;
import org.hello.riscvdisassembler.core.resolve.SectionSymbolResolver;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CfgBuilderTest {
    private final SectionSymbolResolver resolver = new SectionSymbolResolver();
    private final CodeDiscoveryEngine discoveryEngine = new CodeDiscoveryEngine(new Rv32iDecoder());
    private final CfgBuilder cfgBuilder = new CfgBuilder();

    @Test
    void buildCreatesTwoBasicBlocksForRecursiveSampleLoop() throws IOException {
        ResolvedProgram program = resolver.resolve(TestPaths.sampleBinaryImage());
        DiscoveredProgram discovered = discoveryEngine.discover(program, DiscoveryMode.RECURSIVE);

        List<BasicBlock> blocks = cfgBuilder.build(discovered);

        assertEquals(2, blocks.size());
        assertEquals(0L, blocks.get(0).startAddress());
        assertEquals(8L, blocks.get(0).endAddress());
        assertEquals(List.of(12L), blocks.get(0).successors());
        assertEquals(List.of(12L), blocks.get(1).successors());
    }

    @Test
    void buildSupportsLinearDiscoveryGraph() throws IOException {
        ResolvedProgram program = resolver.resolve(TestPaths.sampleBinaryImage());
        DiscoveredProgram discovered = discoveryEngine.discover(program, DiscoveryMode.LINEAR);

        List<BasicBlock> blocks = cfgBuilder.build(discovered);

        assertEquals(2, blocks.size());
        assertTrue(blocks.stream().anyMatch(block -> block.startAddress() == 0L && block.endAddress() == 8L));
        assertTrue(blocks.stream().anyMatch(block -> block.startAddress() == 12L && block.successors().contains(12L)));
    }

    @Test
    void emitIncludesModeEntryPointAndBlockSummary() throws IOException {
        ResolvedProgram program = resolver.resolve(TestPaths.sampleBinaryImage());
        DiscoveredProgram discovered = discoveryEngine.discover(program, DiscoveryMode.RECURSIVE);

        String output = cfgBuilder.emit(discovered);

        assertTrue(output.contains("CFG summary"));
        assertTrue(output.contains("Discovery mode: recursive"));
        assertTrue(output.contains("Entry point: 0x00000000"));
        assertTrue(output.contains("Basic blocks: 2"));
        assertTrue(output.contains("Block 0x00000000 -> 0x00000008 | successors: 0x0000000c"));
        assertTrue(output.contains("Block 0x0000000c -> 0x0000000c | successors: 0x0000000c"));
    }

    @Test
    void buildReturnsEmptyListForProgramWithoutInstructions() throws IOException {
        ResolvedProgram program = resolver.resolve(TestPaths.sampleBinaryImage());
        DiscoveredProgram emptyProgram = new DiscoveredProgram(program, List.of(), List.of(), List.of(), DiscoveryMode.RECURSIVE);

        List<BasicBlock> blocks = cfgBuilder.build(emptyProgram);

        assertTrue(blocks.isEmpty());
    }
}

