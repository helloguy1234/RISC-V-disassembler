package org.hello.riscvdisassembler.core.discover.indirect;

import org.hello.riscvdisassembler.core.binary.model.BinaryImage;
import org.hello.riscvdisassembler.core.binary.model.BinarySection;
import org.hello.riscvdisassembler.core.decode.model.InstructionIr;
import org.hello.riscvdisassembler.core.decode.model.ast.*;
import org.hello.riscvdisassembler.core.discover.ControlFlowEdge;
import org.hello.riscvdisassembler.core.discover.DiscoveredProgram;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class IndirectBranchResolverTest {

        private final IndirectBranchResolver resolver = new IndirectBranchResolver();

        @Test
        void testExtractStructure_SimplePattern() {
                // Pattern: lw t0, 0(a0 + (a1 << 2))
                // AST: MemoryLoad(ADD(RegisterExpr("a0"), SHIFT_LEFT(RegisterExpr("a1"),
                // ImmediateExpr(2))))
                Expression indexExpr = new BinaryOpExpr(Operator.SHIFT_LEFT,
                                new RegisterExpr("a1"),
                                new ImmediateExpr(2));
                Expression baseExpr = new BinaryOpExpr(Operator.ADD,
                                new RegisterExpr("a0"),
                                indexExpr);
                Expression jalrAst = new MemoryLoadExpr(baseExpr, 4);

                Optional<IndirectBranchResolver.JumpTableStructure> result = resolver.extractStructure(jalrAst);

                assertTrue(result.isPresent());
                assertEquals("a0", ((RegisterExpr) result.get().base()).name());
                assertEquals("a1", result.get().index().name());
                assertEquals(4, result.get().scale()); // 1 << 2 = 4
        }

        @Test
        void testExtractStructure_CommutativeAdd() {
                // Pattern: lw t0, 0((a1 << 2) + a0) - reversed ADD
                Expression indexExpr = new BinaryOpExpr(Operator.SHIFT_LEFT,
                                new RegisterExpr("a1"),
                                new ImmediateExpr(2));
                Expression baseExpr = new BinaryOpExpr(Operator.ADD,
                                indexExpr,
                                new RegisterExpr("a0"));
                Expression jalrAst = new MemoryLoadExpr(baseExpr, 4);

                Optional<IndirectBranchResolver.JumpTableStructure> result = resolver.extractStructure(jalrAst);

                assertTrue(result.isPresent());
                assertEquals("a0", ((RegisterExpr) result.get().base()).name());
                assertEquals("a1", result.get().index().name());
                assertEquals(4, result.get().scale());
        }

        @Test
        void testExtractStructure_MulPattern() {
                // Pattern: lw t0, 0(a0 + (a1 * 4))
                Expression indexExpr = new BinaryOpExpr(Operator.MUL,
                                new RegisterExpr("a1"),
                                new ImmediateExpr(4));
                Expression baseExpr = new BinaryOpExpr(Operator.ADD,
                                new RegisterExpr("a0"),
                                indexExpr);
                Expression jalrAst = new MemoryLoadExpr(baseExpr, 4);

                Optional<IndirectBranchResolver.JumpTableStructure> result = resolver.extractStructure(jalrAst);

                assertTrue(result.isPresent());
                assertEquals("a0", ((RegisterExpr) result.get().base()).name());
                assertEquals("a1", result.get().index().name());
                assertEquals(4, result.get().scale());
        }

        @Test
        void testExtractStructure_CommutativeMul() {
                // Pattern: lw t0, 0(a0 + (4 * a1)) - reversed MUL
                Expression indexExpr = new BinaryOpExpr(Operator.MUL,
                                new ImmediateExpr(4),
                                new RegisterExpr("a1"));
                Expression baseExpr = new BinaryOpExpr(Operator.ADD,
                                new RegisterExpr("a0"),
                                indexExpr);
                Expression jalrAst = new MemoryLoadExpr(baseExpr, 4);

                Optional<IndirectBranchResolver.JumpTableStructure> result = resolver.extractStructure(jalrAst);

                assertTrue(result.isPresent());
                assertEquals("a0", ((RegisterExpr) result.get().base()).name());
                assertEquals("a1", result.get().index().name());
                assertEquals(4, result.get().scale());
        }

        @Test
        void testExtractStructure_NonMatchingPattern() {
                // Pattern that doesn't match: just a register
                Expression jalrAst = new RegisterExpr("t0");

                Optional<IndirectBranchResolver.JumpTableStructure> result = resolver.extractStructure(jalrAst);

                assertFalse(result.isPresent());
        }

        @Test
        void testResolveTargets_WithMockImage() {
                // Create a mock jump table structure
                IndirectBranchResolver.JumpTableStructure structure = new IndirectBranchResolver.JumpTableStructure(
                                new ImmediateExpr(0x1000),
                                new RegisterExpr("a1"),
                                4);

                // Create a mock binary image with .rodata at 0x1000
                byte[] mockBytes = new byte[0x2000];
                // Write mock jump table entries: 0x2000, 0x3000, 0x4000 (little-endian)
                mockBytes[0x1000] = (byte) 0x00;
                mockBytes[0x1001] = (byte) 0x20;
                mockBytes[0x1002] = 0x00;
                mockBytes[0x1003] = 0x00;
                mockBytes[0x1004] = (byte) 0x00;
                mockBytes[0x1005] = (byte) 0x30;
                mockBytes[0x1006] = 0x00;
                mockBytes[0x1007] = 0x00;
                mockBytes[0x1008] = (byte) 0x00;
                mockBytes[0x1009] = (byte) 0x40;
                mockBytes[0x100A] = 0x00;
                mockBytes[0x100B] = 0x00;

                BinarySection mockSection = new BinarySection(0, ".rodata", 0x1000, 0x1000, 0x1000, false);
                BinaryImage mockImage = new BinaryImage(0x0, mockBytes, List.of(mockSection), List.of());

                List<Long> targets = resolver.resolveTargets(structure, 3, mockImage);

                assertEquals(3, targets.size());
                assertEquals(0x2000L, targets.get(0));
                assertEquals(0x3000L, targets.get(1));
                assertEquals(0x4000L, targets.get(2));
        }

        @Test
        void testFindUpperBound_WithBoundsCheck() {
                // Create a mock CFG with a bounds check before jalr
                InstructionIr bltuInstr = createMockInstruction(0x1000, "bltu",
                                new AssignExpr(new RegisterExpr("$cond"),
                                                new BinaryOpExpr(Operator.LESS_THAN_UNSIGNED,
                                                                new RegisterExpr("a0"),
                                                                new ImmediateExpr(5))));

                InstructionIr jalrInstr = createMockInstruction(0x1004, "jalr", null);

                List<InstructionIr> instructions = List.of(bltuInstr, jalrInstr);
                List<ControlFlowEdge> edges = List.of(
                                new ControlFlowEdge(0x1000, 0x1004) // bltu -> jalr
                );

                DiscoveredProgram mockCfg = new DiscoveredProgram(
                                null,
                                instructions,
                                edges,
                                List.of(),
                                org.hello.riscvdisassembler.core.discover.DiscoveryMode.RECURSIVE);

                Long bound = resolver.findUpperBound(new RegisterExpr("a0"), jalrInstr, mockCfg);

                assertEquals(5L, bound);
        }

        @Test
        void testFindUpperBound_WithRegisterMove() {
                // Create a mock CFG with bounds check before register move
                InstructionIr bltuInstr = createMockInstruction(0x1000, "bltu",
                                new AssignExpr(new RegisterExpr("$cond"),
                                                new BinaryOpExpr(Operator.LESS_THAN_UNSIGNED,
                                                                new RegisterExpr("a0"), // Compare against a0
                                                                new ImmediateExpr(10))));

                InstructionIr mvInstr = createMockInstruction(0x1004, "mv",
                                new AssignExpr(new RegisterExpr("t0"), new RegisterExpr("a0"))); // Move a0 to t0

                InstructionIr jalrInstr = createMockInstruction(0x1008, "jalr", null);

                List<InstructionIr> instructions = List.of(bltuInstr, mvInstr, jalrInstr);
                List<ControlFlowEdge> edges = List.of(
                                new ControlFlowEdge(0x1000, 0x1004),
                                new ControlFlowEdge(0x1004, 0x1008));

                DiscoveredProgram mockCfg = new DiscoveredProgram(
                                null,
                                instructions,
                                edges,
                                List.of(),
                                org.hello.riscvdisassembler.core.discover.DiscoveryMode.RECURSIVE);

                // Start with t0 (used in jalr calculation), should track back to a0 and find bound
                Long bound = resolver.findUpperBound(new RegisterExpr("t0"), jalrInstr, mockCfg);

                assertEquals(10L, bound);
        }

        @Test
        void testFindUpperBound_NoBoundsCheck() {
                // Create a mock CFG without bounds check
                InstructionIr jalrInstr = createMockInstruction(0x1000, "jalr", null);

                List<InstructionIr> instructions = List.of(jalrInstr);
                List<ControlFlowEdge> edges = List.of();

                DiscoveredProgram mockCfg = new DiscoveredProgram(
                                null,
                                instructions,
                                edges,
                                List.of(),
                                org.hello.riscvdisassembler.core.discover.DiscoveryMode.RECURSIVE);

                Long bound = resolver.findUpperBound(new RegisterExpr("a0"), jalrInstr, mockCfg);

                assertNull(bound);
        }

        @Test
        void testResolveTargets_UnresolvableBaseAddress() {
                // Create structure with a base that cannot be evaluated to a constant
                IndirectBranchResolver.JumpTableStructure structure = new IndirectBranchResolver.JumpTableStructure(
                                new RegisterExpr("unknown_base"),
                                new RegisterExpr("a1"),
                                4);

                BinaryImage mockImage = new BinaryImage(0x0, new byte[0], List.of(), List.of());

                List<Long> targets = resolver.resolveTargets(structure, 3, mockImage);

                assertTrue(targets.isEmpty());
        }

        @Test
        void testResolveTargets_MemoryAccessException() {
                // Structure pointing to address 0x1000
                IndirectBranchResolver.JumpTableStructure structure = new IndirectBranchResolver.JumpTableStructure(
                                new ImmediateExpr(0x1000),
                                new RegisterExpr("a1"),
                                4);

                // Create an empty binary image (no sections)
                BinaryImage mockImage = new BinaryImage(0x0, new byte[0], List.of(), List.of());

                assertThrows(MemoryAccessException.class, () -> {
                        resolver.resolveTargets(structure, 1, mockImage);
                });
        }

        @Test
        void testFindUpperBound_ExceedsThreshold() {
                // Create a mock CFG with 51 instructions before jalr
                // The bounds check is placed at the 52nd position, which should be ignored
                List<InstructionIr> instructions = new java.util.ArrayList<>();
                List<ControlFlowEdge> edges = new java.util.ArrayList<>();

                InstructionIr bltuInstr = createMockInstruction(0x1000, "bltu",
                                new AssignExpr(new RegisterExpr("$cond"),
                                                new BinaryOpExpr(Operator.LESS_THAN_UNSIGNED,
                                                                new RegisterExpr("a0"),
                                                                new ImmediateExpr(5))));
                instructions.add(bltuInstr);

                long currentAddr = 0x1004;
                edges.add(new ControlFlowEdge(0x1000, currentAddr));

                for (int i = 0; i < 50; i++) {
                        InstructionIr nopInstr = createMockInstruction(currentAddr, "nop", null);
                        instructions.add(nopInstr);
                        long nextAddr = currentAddr + 4;
                        edges.add(new ControlFlowEdge(currentAddr, nextAddr));
                        currentAddr = nextAddr;
                }

                InstructionIr jalrInstr = createMockInstruction(currentAddr, "jalr", null);
                instructions.add(jalrInstr);

                DiscoveredProgram mockCfg = new DiscoveredProgram(
                                null,
                                instructions,
                                edges,
                                List.of(),
                                org.hello.riscvdisassembler.core.discover.DiscoveryMode.RECURSIVE);

                Long bound = resolver.findUpperBound(new RegisterExpr("a0"), jalrInstr, mockCfg);

                assertNull(bound, "Should return null because bounds check is beyond the 50 instruction threshold");
        }

        private InstructionIr createMockInstruction(long address, String mnemonic, AssignExpr semantic) {
                return new InstructionIr(
                                address,
                                0,
                                mnemonic,
                                List.of(),
                                "I",
                                InstructionIr.ControlFlowType.NORMAL,
                                null,
                                ".text",
                                semantic);
        }
}
