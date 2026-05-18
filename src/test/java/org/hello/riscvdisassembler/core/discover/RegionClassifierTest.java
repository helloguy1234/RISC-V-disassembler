package org.hello.riscvdisassembler.core.discover;

import org.hello.riscvdisassembler.core.binary.model.BinaryImage;
import org.hello.riscvdisassembler.core.binary.model.BinarySection;
import org.hello.riscvdisassembler.core.decode.model.InstructionIr;
import org.hello.riscvdisassembler.core.resolve.ResolvedProgram;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RegionClassifierTest {

    private RegionClassifier classifier;
    private ResolvedProgram resolvedProgram;

    @BeforeEach
    void setUp() {
        classifier = new RegionClassifier();
        BinarySection textSection = new BinarySection(1, ".text", 0x1000, 0, 0x14, true); // size 20 bytes
        BinaryImage image = new BinaryImage(0x1000, new byte[0], List.of(textSection), List.of());
        resolvedProgram = new ResolvedProgram(image, List.of(textSection), new TreeMap<>(), Map.of());
    }

    @Test
    void testClassifyLinearMode() {
        List<InstructionIr> instructions = List.of(
                new InstructionIr(0x1000, 0, "nop", List.of(), "I", InstructionIr.ControlFlowType.NORMAL, null, ".text",
                        null));

        List<DiscoveredRegion> regions = classifier.classify(resolvedProgram, instructions, DiscoveryMode.LINEAR);

        assertEquals(1, regions.size());
        DiscoveredRegion region = regions.getFirst();
        assertEquals(".text", region.sectionName());
        assertEquals(0x1000, region.start());
        assertEquals(0x1014, region.end());
        assertEquals(RegionKind.CODE, region.kind());
        assertEquals("linear-sweep", region.reason());
    }

    @Test
    void testClassifyUnreachableSection() {
        List<InstructionIr> instructions = List.of(); // No instructions in section

        List<DiscoveredRegion> regions = classifier.classify(resolvedProgram, instructions, DiscoveryMode.RECURSIVE);

        assertEquals(1, regions.size());
        DiscoveredRegion region = regions.getFirst();
        assertEquals(".text", region.sectionName());
        assertEquals(0x1000, region.start());
        assertEquals(0x1014, region.end());
        assertEquals(RegionKind.DATA, region.kind());
        assertEquals("unreachable-section", region.reason());
    }

    @Test
    void testClassifyWithGaps() {
        // Section: 0x1000 to 0x1014 (size 20)
        // Instructions at: 0x1004, 0x1008
        // Gap at start: 0x1000 to 0x1004
        // Code run: 0x1004 to 0x100C (length 8)
        // Gap at end: 0x100C to 0x1014
        List<InstructionIr> instructions = List.of(
                new InstructionIr(0x1004, 0, "nop", List.of(), "I", InstructionIr.ControlFlowType.NORMAL, null, ".text",
                        null),
                new InstructionIr(0x1008, 0, "nop", List.of(), "I", InstructionIr.ControlFlowType.NORMAL, null, ".text",
                        null));

        List<DiscoveredRegion> regions = classifier.classify(resolvedProgram, instructions, DiscoveryMode.RECURSIVE);

        assertEquals(3, regions.size());

        // Gap at start
        assertEquals(0x1000, regions.get(0).start());
        assertEquals(0x1004, regions.get(0).end());
        assertEquals(RegionKind.DATA, regions.get(0).kind());
        assertEquals("unreachable-gap", regions.get(0).reason());

        // Code run
        assertEquals(0x1004, regions.get(1).start());
        assertEquals(0x100C, regions.get(1).end());
        assertEquals(RegionKind.CODE, regions.get(1).kind());
        assertEquals("reachable-code", regions.get(1).reason());

        // Gap at end
        assertEquals(0x100C, regions.get(2).start());
        assertEquals(0x1014, regions.get(2).end());
        assertEquals(RegionKind.DATA, regions.get(2).kind());
        assertEquals("unreachable-gap", regions.get(2).reason());
    }

    @Test
    void testClassifyMiddleGap() {
        // Section: 0x1000 to 0x1014 (size 20)
        // Instructions at: 0x1000, 0x1010
        // Code run 1: 0x1000 to 0x1004
        // Middle gap: 0x1004 to 0x1010
        // Code run 2: 0x1010 to 0x1014
        List<InstructionIr> instructions = List.of(
                new InstructionIr(0x1000, 0, "nop", List.of(), "I", InstructionIr.ControlFlowType.NORMAL, null,
                        ".text", null),
                new InstructionIr(0x1010, 0, "nop", List.of(), "I", InstructionIr.ControlFlowType.NORMAL, null,
                        ".text", null));

        List<DiscoveredRegion> regions = classifier.classify(resolvedProgram, instructions, DiscoveryMode.RECURSIVE);

        assertEquals(3, regions.size());

        // Code run 1
        assertEquals(0x1000, regions.get(0).start());
        assertEquals(0x1004, regions.get(0).end());
        assertEquals(RegionKind.CODE, regions.get(0).kind());

        // Middle gap
        assertEquals(0x1004, regions.get(1).start());
        assertEquals(0x1010, regions.get(1).end());
        assertEquals(RegionKind.DATA, regions.get(1).kind());
        assertEquals("unreachable-gap", regions.get(1).reason());

        // Code run 2
        assertEquals(0x1010, regions.get(2).start());
        assertEquals(0x1014, regions.get(2).end());
        assertEquals(RegionKind.CODE, regions.get(2).kind());
    }
}
