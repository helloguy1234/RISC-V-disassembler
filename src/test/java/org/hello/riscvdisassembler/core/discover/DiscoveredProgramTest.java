package org.hello.riscvdisassembler.core.discover;

import org.hello.riscvdisassembler.core.binary.model.BinaryImage;
import org.hello.riscvdisassembler.core.decode.model.InstructionIr;
import org.hello.riscvdisassembler.core.resolve.ResolvedProgram;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class DiscoveredProgramTest {

    @Test
    void testDiscoveredProgramGetters() {
        BinaryImage image = new BinaryImage(0, new byte[0], List.of(), List.of());
        ResolvedProgram resolvedProgram = new ResolvedProgram(image, List.of(), new TreeMap<>(), Map.of());
        
        InstructionIr instruction = new InstructionIr(0, 0, "nop", List.of(), "I", InstructionIr.ControlFlowType.NORMAL, null, ".text");
        List<InstructionIr> instructions = List.of(instruction);
        
        ControlFlowEdge edge = new ControlFlowEdge(0, 4);
        List<ControlFlowEdge> edges = List.of(edge);
        
        DiscoveredRegion region = new DiscoveredRegion(".text", 0, 4, RegionKind.CODE, "reachable");
        List<DiscoveredRegion> regions = List.of(region);
        
        DiscoveredProgram program = new DiscoveredProgram(resolvedProgram, instructions, edges, regions, DiscoveryMode.LINEAR);

        assertSame(resolvedProgram, program.resolvedProgram());
        assertEquals(1, program.instructions().size());
        assertEquals(instruction, program.instructions().getFirst());
        assertEquals(1, program.edges().size());
        assertEquals(edge, program.edges().getFirst());
        assertEquals(1, program.regions().size());
        assertEquals(region, program.regions().getFirst());
        assertEquals(DiscoveryMode.LINEAR, program.mode());
    }
}
