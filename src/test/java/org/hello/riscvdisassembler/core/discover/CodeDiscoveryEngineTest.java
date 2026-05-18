package org.hello.riscvdisassembler.core.discover;

import org.hello.riscvdisassembler.TestPaths;
import org.hello.riscvdisassembler.core.binary.model.BinaryImage;
import org.hello.riscvdisassembler.core.binary.model.BinarySection;
import org.hello.riscvdisassembler.core.decode.InstructionDecoder;
import org.hello.riscvdisassembler.core.decode.Rv32iDecoder;
import org.hello.riscvdisassembler.core.decode.model.InstructionIr;
import org.hello.riscvdisassembler.core.decode.model.ast.AssignExpr;
import org.hello.riscvdisassembler.core.decode.model.ast.BinaryOpExpr;
import org.hello.riscvdisassembler.core.decode.model.ast.ImmediateExpr;
import org.hello.riscvdisassembler.core.decode.model.ast.MemoryLoadExpr;
import org.hello.riscvdisassembler.core.decode.model.ast.Operator;
import org.hello.riscvdisassembler.core.decode.model.ast.RegisterExpr;
import org.hello.riscvdisassembler.core.discover.indirect.IndirectBranchResolver;
import org.hello.riscvdisassembler.core.resolve.ResolvedProgram;
import org.hello.riscvdisassembler.core.resolve.SectionSymbolResolver;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

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

    @Test
    void recursiveModeResolvesIndirectBranches() {
        // Arrange: Synthetic binary structure
        byte[] rodataBytes = new byte[] {
            0x04, 0x10, 0x00, 0x00, // 0x1004
            0x08, 0x10, 0x00, 0x00  // 0x1008
        };
        
        byte[] imageBytes = new byte[0x2000 + 8];
        System.arraycopy(rodataBytes, 0, imageBytes, 0x2000, 8);

        BinarySection textSection = new BinarySection(0, ".text", 0x0FFC, 0x0FFC, 20, true);
        BinarySection rodataSection = new BinarySection(1, ".rodata", 0x2000, 0x2000, 8, false);

        BinaryImage mockImage = new BinaryImage(0x0FFC, imageBytes, List.of(textSection, rodataSection), Collections.emptyList());
        ResolvedProgram mockProgram = new ResolvedProgram(
                mockImage, 
                List.of(textSection, rodataSection), 
                new TreeMap<>(), 
                Map.of(".text", new TreeMap<>(), ".rodata", new TreeMap<>())
        );

        InstructionDecoder mockDecoder = new InstructionDecoder() {
            @Override
            public InstructionIr decodeAt(ResolvedProgram program, BinarySection section, long address) {
                if (address == 0x0FFC) {
                    // Predecessor: bounds check x10 < 2
                    return new InstructionIr(0x0FFC, 0, "bltu", List.of(), "B",
                            InstructionIr.ControlFlowType.CONDITIONAL_BRANCH, 0x1010L, ".text",
                            new AssignExpr(new RegisterExpr("$cond"), new BinaryOpExpr(
                                    Operator.LESS_THAN_UNSIGNED,
                                    new RegisterExpr("x10"),
                                    new ImmediateExpr(2)
                            )));
                }
                if (address == 0x1000) {
                    // jalr instruction loading from jump table: pc = Mem[0x2000 + x10 * 4]
                    return new InstructionIr(0x1000, 0, "jalr", List.of(), "I",
                            InstructionIr.ControlFlowType.UNCONDITIONAL_JUMP, null, ".text",
                            new AssignExpr(new RegisterExpr("pc"), new MemoryLoadExpr(
                                    new BinaryOpExpr(
                                            Operator.ADD,
                                            new ImmediateExpr(0x2000),
                                            new BinaryOpExpr(
                                                    Operator.MUL,
                                                    new RegisterExpr("x10"),
                                                    new ImmediateExpr(4)
                                            )
                                    ), 4)));
                }
                return new InstructionIr(address, 0, "nop", List.of(), "I",
                        InstructionIr.ControlFlowType.NORMAL, null, ".text", null);
            }
        };

        CodeDiscoveryEngine engine = new CodeDiscoveryEngine(mockDecoder, new IndirectBranchResolver());

        // Act
        DiscoveredProgram discovered = engine.discover(mockProgram, DiscoveryMode.RECURSIVE);

        // Assert: Fixpoint iteration should have enqueued and processed 0x1004 and 0x1008
        assertTrue(discovered.instructions().stream().anyMatch(i -> i.address() == 0x1000L), "jalr should be discovered");
        assertTrue(discovered.instructions().stream().anyMatch(i -> i.address() == 0x1004L), "target 1 should be discovered");
        assertTrue(discovered.instructions().stream().anyMatch(i -> i.address() == 0x1008L), "target 2 should be discovered");
        assertTrue(discovered.edges().stream().anyMatch(e -> e.from() == 0x1000L && e.to() == 0x1004L), "edge to target 1 should exist");
        assertTrue(discovered.edges().stream().anyMatch(e -> e.from() == 0x1000L && e.to() == 0x1008L), "edge to target 2 should exist");
    }

    @Test
    void recursiveModeResolvesJumpTableWithEquivalenceClass() {
        // Arrange: Synthetic binary structure with 3 targets
        byte[] rodataBytes = new byte[] {
            0x04, 0x10, 0x00, 0x00, // 0x1004
            0x08, 0x10, 0x00, 0x00, // 0x1008
            0x0C, 0x10, 0x00, 0x00  // 0x100C
        };
        
        byte[] imageBytes = new byte[0x2000 + 12];
        System.arraycopy(rodataBytes, 0, imageBytes, 0x2000, 12);

        BinarySection textSection = new BinarySection(0, ".text", 0x0FF4, 0x0FF4, 28, true);
        BinarySection rodataSection = new BinarySection(1, ".rodata", 0x2000, 0x2000, 12, false);

        BinaryImage mockImage = new BinaryImage(0x0FF4, imageBytes, List.of(textSection, rodataSection), Collections.emptyList());
        ResolvedProgram mockProgram = new ResolvedProgram(
                mockImage, 
                List.of(textSection, rodataSection), 
                new TreeMap<>(), 
                Map.of(".text", new TreeMap<>(), ".rodata", new TreeMap<>())
        );

        InstructionDecoder mockDecoder = new InstructionDecoder() {
            @Override
            public InstructionIr decodeAt(ResolvedProgram program, BinarySection section, long address) {
                if (address == 0x0FF4) {
                    // Predecessor: bounds check x11 < 3
                    return new InstructionIr(0x0FF4, 0, "bltu", List.of(), "B",
                            InstructionIr.ControlFlowType.CONDITIONAL_BRANCH, 0x1010L, ".text",
                            new AssignExpr(new RegisterExpr("$cond"), new BinaryOpExpr(
                                    Operator.LESS_THAN_UNSIGNED,
                                    new RegisterExpr("x11"),
                                    new ImmediateExpr(3)
                            )));
                }
                if (address == 0x0FF8) {
                    // Register move: x10 = x11
                    return new InstructionIr(0x0FF8, 0, "mv", List.of(), "I",
                            InstructionIr.ControlFlowType.NORMAL, null, ".text",
                            new AssignExpr(new RegisterExpr("x10"), new RegisterExpr("x11")));
                }
                if (address == 0x0FFC) {
                    // Unrelated instruction
                    return new InstructionIr(0x0FFC, 0, "addi", List.of(), "I",
                            InstructionIr.ControlFlowType.NORMAL, null, ".text",
                            new AssignExpr(new RegisterExpr("t0"), new ImmediateExpr(1)));
                }
                if (address == 0x1000) {
                    // jalr instruction loading from jump table: pc = Mem[0x2000 + x10 * 4]
                    return new InstructionIr(0x1000, 0, "jalr", List.of(), "I",
                            InstructionIr.ControlFlowType.UNCONDITIONAL_JUMP, null, ".text",
                            new AssignExpr(new RegisterExpr("pc"), new MemoryLoadExpr(
                                    new BinaryOpExpr(
                                            Operator.ADD,
                                            new ImmediateExpr(0x2000),
                                            new BinaryOpExpr(
                                                    Operator.MUL,
                                                    new RegisterExpr("x10"),
                                                    new ImmediateExpr(4)
                                            )
                                    ), 4)));
                }
                return new InstructionIr(address, 0, "nop", List.of(), "I",
                        InstructionIr.ControlFlowType.TERMINATOR, null, ".text", null);
            }
        };

        CodeDiscoveryEngine engine = new CodeDiscoveryEngine(mockDecoder, new IndirectBranchResolver());

        // Act
        DiscoveredProgram discovered = engine.discover(mockProgram, DiscoveryMode.RECURSIVE);

        // Assert: Fixpoint iteration should have enqueued and processed targets 0x1004, 0x1008, 0x100C
        assertTrue(discovered.instructions().stream().anyMatch(i -> i.address() == 0x1000L), "jalr should be discovered");
        assertTrue(discovered.instructions().stream().anyMatch(i -> i.address() == 0x1004L), "target 1 should be discovered");
        assertTrue(discovered.instructions().stream().anyMatch(i -> i.address() == 0x1008L), "target 2 should be discovered");
        assertTrue(discovered.instructions().stream().anyMatch(i -> i.address() == 0x100CL), "target 3 should be discovered");
        
        // Assert Control Flow Edges
        assertTrue(discovered.edges().stream().anyMatch(e -> e.from() == 0x1000L && e.to() == 0x1004L), "edge to target 1 should exist");
        assertTrue(discovered.edges().stream().anyMatch(e -> e.from() == 0x1000L && e.to() == 0x1008L), "edge to target 2 should exist");
        assertTrue(discovered.edges().stream().anyMatch(e -> e.from() == 0x1000L && e.to() == 0x100CL), "edge to target 3 should exist");
    }
}

