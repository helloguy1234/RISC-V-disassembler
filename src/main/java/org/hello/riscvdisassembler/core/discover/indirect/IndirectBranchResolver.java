package org.hello.riscvdisassembler.core.discover.indirect;

import org.hello.riscvdisassembler.core.binary.model.BinaryImage;
import org.hello.riscvdisassembler.core.decode.model.InstructionIr;
import org.hello.riscvdisassembler.core.decode.model.ast.*;
import org.hello.riscvdisassembler.core.discover.DiscoveredProgram;
import org.hello.riscvdisassembler.core.discover.indirect.domain.State;

import java.util.*;

/**
 * Resolves indirect branch targets (jalr) using Static Jump Table Analysis
 * (SJA).
 * Performs structural pattern matching, bounds analysis, and target resolution.
 */
public final class IndirectBranchResolver {

    /**
     * Represents the mathematical structure of a jump table: TA = Base + (Index *
     * Scale)
     */
    public static record JumpTableStructure(Expression base, RegisterExpr index, int scale) {
    }

    /**
     * Extracts the jump table structure from a jalr instruction's AST.
     * Uses Java 21 record patterns to detect: MemoryLoad(ADD(Base,
     * SHIFT_LEFT/MUL(Index, Scale)))
     *
     * @param jalrAst the AST expression from the jalr instruction (typically the
     *                RHS of an AssignExpr)
     * @return Optional containing the structure if detected, empty otherwise
     */
    public Optional<JumpTableStructure> extractStructure(Expression jalrAst) {
        jalrAst = simplify(jalrAst);

        return switch (jalrAst) {
            case MemoryLoadExpr(var baseAddress, var sizeBytes) -> {
                // Pattern: MemoryLoad(ADD(Base, Offset)) where Offset contains Index * Scale
                if (baseAddress instanceof BinaryOpExpr addExpr && addExpr.op() == Operator.ADD) {
                    // Check both sides for commutativity: ADD(Base, Offset) or ADD(Offset, Base)
                    var result = tryExtractFromAdd(addExpr);
                    yield result;
                }
                yield Optional.empty();
            }
            default -> Optional.empty();
        };
    }

    private Expression simplify(Expression expr) {
        if (expr == null)
            return null;
        return switch (expr) {
            case BinaryOpExpr(Operator op, Expression left, Expression right) -> {
                Expression simLeft = simplify(left);
                Expression simRight = simplify(right);
                if (op == Operator.ADD) {
                    Expression simplified = simplifyAdd(simLeft, simRight);
                    if (simplified != null) {
                        yield simplified;
                    }
                }
                yield new BinaryOpExpr(op, simLeft, simRight);
            }
            case MemoryLoadExpr(Expression base, int size) -> new MemoryLoadExpr(simplify(base), size);
            default -> expr;
        };
    }

    private Expression simplifyAdd(Expression left, Expression right) {
        if (right instanceof ImmediateExpr imm && imm.value() == 0)
            return left;
        if (left instanceof ImmediateExpr imm && imm.value() == 0)
            return right;
        if (left instanceof ImmediateExpr immL && right instanceof ImmediateExpr immR) {
            return new ImmediateExpr(immL.value() + immR.value());
        }
        if (left instanceof BinaryOpExpr binLeft && binLeft.op() == Operator.ADD) {
            return simplifyNestedAdd(binLeft, right);
        }
        return null;
    }

    private Expression simplifyNestedAdd(BinaryOpExpr leftAdd, Expression right) {
        if (leftAdd.left() instanceof ImmediateExpr imm1 && right instanceof ImmediateExpr imm2) {
            return new BinaryOpExpr(Operator.ADD, new ImmediateExpr(imm1.value() + imm2.value()),
                    leftAdd.right());
        }
        if (leftAdd.right() instanceof ImmediateExpr imm1 && right instanceof ImmediateExpr imm2) {
            return new BinaryOpExpr(Operator.ADD, new ImmediateExpr(imm1.value() + imm2.value()),
                    leftAdd.left());
        }
        return null;
    }

    /**
     * Helper to extract structure from an ADD node, handling commutativity.
     */
    private Optional<JumpTableStructure> tryExtractFromAdd(BinaryOpExpr addExpr) {
        // Try left as Base, right as Offset
        var leftResult = tryExtractOffset(addExpr.left(), addExpr.right());
        if (leftResult.isPresent()) {
            return leftResult;
        }

        // Try right as Base, left as Offset (commutativity)
        return tryExtractOffset(addExpr.right(), addExpr.left());
    }

    /**
     * Attempts to extract Index and Scale from the offset expression.
     * Expected pattern: SHIFT_LEFT(Index, ShiftAmount) or MUL(Index, Scale)
     */
    private Optional<JumpTableStructure> tryExtractOffset(Expression base, Expression offset) {
        return switch (offset) {
            case BinaryOpExpr(Operator op, Expression left, Expression right) -> {
                if (op == Operator.SHIFT_LEFT) {
                    // Pattern: SHIFT_LEFT(Index, ShiftAmount) -> Scale = 1 << ShiftAmount
                    if (left instanceof RegisterExpr index && right instanceof ImmediateExpr shiftAmount) {
                        int scale = 1 << (int) shiftAmount.value();
                        yield Optional.of(new JumpTableStructure(base, index, scale));
                    }
                    // Check commutativity (though SHIFT_LEFT is not commutative, handle for
                    // robustness)
                    if (right instanceof RegisterExpr index && left instanceof ImmediateExpr shiftAmount) {
                        int scale = 1 << (int) shiftAmount.value();
                        yield Optional.of(new JumpTableStructure(base, index, scale));
                    }
                } else if (op == Operator.MUL) {
                    // Pattern: MUL(Index, Scale) or MUL(Scale, Index)
                    var scaleResult = tryExtractScale(left, right);
                    if (scaleResult.isPresent()) {
                        var entry = scaleResult.get();
                        yield Optional.of(new JumpTableStructure(base, entry.getKey(), entry.getValue()));
                    }
                }
                yield Optional.empty();
            }
            default -> Optional.empty();
        };
    }

    /**
     * Extracts Index and Scale from a MUL node, handling commutativity.
     */
    private Optional<AbstractMap.SimpleEntry<RegisterExpr, Integer>> tryExtractScale(Expression left,
            Expression right) {
        // MUL(Index, Scale)
        if (left instanceof RegisterExpr index && right instanceof ImmediateExpr scale) {
            return Optional.of(new AbstractMap.SimpleEntry<>(index, (int) scale.value()));
        }
        // MUL(Scale, Index)
        if (right instanceof RegisterExpr index && left instanceof ImmediateExpr scale) {
            return Optional.of(new AbstractMap.SimpleEntry<>(index, (int) scale.value()));
        }
        return Optional.empty();
    }

    /**
     * Recursively evaluates a constant expression.
     * Handles ImmediateExpr and constant-folded BinaryOpExpr (ADD, SUB, MUL,
     * SHIFT_LEFT).
     *
     * @param expr the expression to evaluate
     * @return Optional containing the constant value, empty if not constant
     */
    private Optional<Long> evaluateConstant(Expression expr) {
        return switch (expr) {
            case ImmediateExpr imm -> Optional.of(imm.value());
            case RegisterExpr reg -> {
                // Handle the zero register (x0) which always has value 0
                if (reg.name().equals("zero")) {
                    yield Optional.of(0L);
                }
                yield Optional.empty();
            }
            case BinaryOpExpr(Operator op, Expression left, Expression right) -> {
                Optional<Long> leftVal = evaluateConstant(left);
                Optional<Long> rightVal = evaluateConstant(right);
                if (leftVal.isEmpty() || rightVal.isEmpty()) {
                    yield Optional.empty();
                }
                long lv = leftVal.get();
                long rv = rightVal.get();
                yield switch (op) {
                    case ADD -> Optional.of(lv + rv);
                    case SUB -> Optional.of(lv - rv);
                    case MUL -> Optional.of(lv * rv);
                    case SHIFT_LEFT -> Optional.of(lv << rv);
                    case SHIFT_RIGHT -> Optional.of(lv >> rv);
                    case AND -> Optional.of(lv & rv);
                    case OR -> Optional.of(lv | rv);
                    case XOR -> Optional.of(lv ^ rv);
                    default -> Optional.empty();
                };
            }
            default -> Optional.empty();
        };
    }

    /**
     * Builds a state from the given jalr instruction by traversing backward.
     *
     * @param jalrInstruction the jalr instruction to start from
     * @param cfg             the discovered program
     * @return the state built from traversing the instructions
     */
    public State buildState(InstructionIr jalrInstruction, DiscoveredProgram cfg) {
        List<InstructionIr> path = new ArrayList<>();
        long currentAddr = jalrInstruction.address();

        // Backward traversal up to 20 instructions
        for (int i = 0; i < 20; i++) {
            long finalCurrentAddr = currentAddr;
            Optional<InstructionIr> predOpt = cfg.edges().stream()
                    .filter(e -> e.to() == finalCurrentAddr)
                    .map(e -> cfg.instructions().stream()
                            .filter(inst -> inst.address() == e.from())
                            .findFirst().orElse(null))
                    .filter(Objects::nonNull)
                    .findFirst();

            if (predOpt.isEmpty()) {
                break;
            }

            InstructionIr pred = predOpt.get();
            path.add(pred);
            currentAddr = pred.address();

            // Stop traversal at conditional branch (likely the bounds check)
            // This prevents substituting the index register with a constant loaded earlier.
            if (pred.controlFlowType() == org.hello.riscvdisassembler.core.decode.model.InstructionIr.ControlFlowType.CONDITIONAL_BRANCH) {
                break;
            }
        }

        // Reverse to get a forward linear path
        Collections.reverse(path);

        State state = new State();
        for (InstructionIr inst : path) {
            if (inst.semantic() != null && inst.semantic() instanceof AssignExpr assign
                    && assign.lhs() instanceof RegisterExpr lhs) {
                state = state.update(lhs.name(), State.substitute(assign.rhs(), state));
            }
        }
        return state;
    }

    /**
     * Performs bounded backward traversal to find the upper bound of the index
     * register.
     * Builds a predecessor map and traverses backward from the jalr instruction.
     *
     * @param indexReg        the index register from the jump table structure
     * @param jalrInstruction the jalr instruction to start from
     * @param cfg             the discovered program (partial CFG)
     * @return the upper bound if found, null if threshold exceeded or not found
     */
    public Long findUpperBound(RegisterExpr indexReg, InstructionIr jalrInstruction, DiscoveredProgram cfg) {
        Map<Long, List<InstructionIr>> predecessorMap = buildPredecessorMap(cfg);

        TraversalState state = initializeTraversalState(indexReg);

        return performBackwardTraversal(jalrInstruction, predecessorMap, state, cfg);
    }

    private Map<Long, List<InstructionIr>> buildPredecessorMap(DiscoveredProgram cfg) {
        Map<Long, List<InstructionIr>> predecessorMap = new HashMap<>();
        for (var edge : cfg.edges()) {
            predecessorMap.computeIfAbsent(edge.to(), k -> new ArrayList<>()).add(
                    cfg.instructions().stream()
                            .filter(inst -> inst.address() == edge.from())
                            .findFirst()
                            .orElse(null));
        }
        return predecessorMap;
    }

    private TraversalState initializeTraversalState(RegisterExpr indexReg) {
        Map<String, Expression> equivalenceClass = new HashMap<>();
        equivalenceClass.put(indexReg.name(), indexReg);

        Set<Long> visited = new HashSet<>();
        Deque<Long> worklist = new ArrayDeque<>();
        Map<String, Long> knownConstants = new HashMap<>();

        return new TraversalState(equivalenceClass, visited, worklist, knownConstants);
    }

    private Long performBackwardTraversal(InstructionIr jalrInstruction,
            Map<Long, List<InstructionIr>> predecessorMap,
            TraversalState state,
            DiscoveredProgram cfg) {
        state.worklist.add(jalrInstruction.address());
        state.visited.add(jalrInstruction.address());

        int instructionCount = 0;
        final int MAX_INSTRUCTIONS = 50;

        while (!state.worklist.isEmpty() && instructionCount < MAX_INSTRUCTIONS) {
            long currentAddr = state.worklist.removeFirst();
            List<InstructionIr> predecessors = predecessorMap.get(currentAddr);

            if (predecessors == null || predecessors.isEmpty()) {
                continue;
            }

            Long bound = processPredecessors(predecessors, currentAddr, state, cfg, instructionCount, MAX_INSTRUCTIONS);
            if (bound != null) {
                return bound;
            }

            instructionCount += predecessors.size();
        }

        return null;
    }

    private Long processPredecessors(List<InstructionIr> predecessors,
            long currentAddr,
            TraversalState state,
            DiscoveredProgram cfg,
            int instructionCount,
            int maxInstructions) {
        for (InstructionIr pred : predecessors) {
            if (pred == null || state.visited.contains(pred.address())) {
                continue;
            }

            
            state.visited.add(pred.address());

            instructionCount++;
            if (instructionCount > maxInstructions) {
                return null;
            }

            trackConstantAssignment(pred, state.knownConstants);
            updateEquivalenceClass(pred, state.equivalenceClass);

            boolean isFallthrough = currentAddr == pred.address() + 4;
            Long bound = checkBoundsCheck(pred, state.equivalenceClass, state.knownConstants, isFallthrough, cfg);
            if (bound != null) {
                return bound;
            }

            state.worklist.add(pred.address());
        }
        return null;
    }

    private void trackConstantAssignment(InstructionIr pred, Map<String, Long> knownConstants) {
        if (pred.semantic() != null && pred.semantic().lhs() instanceof RegisterExpr lhs) {
            Optional<Long> constVal = evaluateConstant(pred.semantic().rhs());
            if (constVal.isPresent()) {
                knownConstants.put(lhs.name(), constVal.get());
            }
        }
    }

    private static class TraversalState {
        final Map<String, Expression> equivalenceClass;
        final Set<Long> visited;
        final Deque<Long> worklist;
        final Map<String, Long> knownConstants;

        TraversalState(Map<String, Expression> equivalenceClass, Set<Long> visited,
                Deque<Long> worklist, Map<String, Long> knownConstants) {
            this.equivalenceClass = equivalenceClass;
            this.visited = visited;
            this.worklist = worklist;
            this.knownConstants = knownConstants;
        }
    }

    /**
     * Updates the equivalence class when encountering register move operations.
     * If instruction is AssignExpr(lhs, rhs) where either lhs or rhs is in EC,
     * add the other to EC (bidirectional).
     */
    private void updateEquivalenceClass(InstructionIr instruction, Map<String, Expression> equivalenceClass) {
        if (instruction.semantic() == null) {
            return;
        }

        AssignExpr assign = instruction.semantic();
        if (assign.lhs() instanceof RegisterExpr lhs && assign.rhs() instanceof RegisterExpr rhs) {
            // Bidirectional: if either register is in EC, add the other
            if (equivalenceClass.containsKey(lhs.name())) {
                equivalenceClass.put(rhs.name(), rhs);
            }
            if (equivalenceClass.containsKey(rhs.name())) {
                equivalenceClass.put(lhs.name(), lhs);
            }
        }
    }

    /**
     * Checks if an instruction is a conditional branch that compares a register in
     * the
     * equivalence class with an immediate value (bounds check).
     *
     * @param instruction      the instruction to check
     * @param equivalenceClass the current equivalence class
     * @param knownConstants   the known constants
     * @param isFallthrough    whether the path taken was the fallthrough edge
     * @param cfg              the discovered program for resolving constants
     * @return the immediate bound value if found, null otherwise
     */
    private Long checkBoundsCheck(InstructionIr instruction, Map<String, Expression> equivalenceClass,
            Map<String, Long> knownConstants, boolean isFallthrough, DiscoveredProgram cfg) {
        if (instruction.semantic() == null) {
            return null;
        }

        AssignExpr assign = instruction.semantic();
        if (!(assign.lhs() instanceof RegisterExpr lhs) || !lhs.name().equals("$cond")) {
            return null;
        }

        if (!(assign.rhs() instanceof BinaryOpExpr rhs)) {
            return null;
        }

        Operator op = rhs.op();
        // Check if operator is a comparison
        if (!isComparisonOperator(op)) {
            return null;
        }

        if (isFallthrough) {
            op = invertOperator(op);
        }

        return switch (rhs) {
            case BinaryOpExpr(Operator origOp, Expression left, Expression right) -> {
                Long bound = null;
                // Index OP Constant
                if (left instanceof RegisterExpr reg && equivalenceClass.containsKey(reg.name())) {
                    Long constVal = getConstant(right, knownConstants, instruction.address(), cfg);
                    if (constVal != null) {
                        if (op == Operator.LESS_THAN || op == Operator.LESS_THAN_UNSIGNED) {
                            bound = constVal;
                        } else if (op == Operator.GREATER_EQUAL || op == Operator.GREATER_EQUAL_UNSIGNED) {
                            // When inverted (fallthrough), GREATER_EQUAL becomes the upper bound
                            bound = constVal;
                        } else if (op == Operator.EQUAL) {
                            bound = constVal + 1;
                        }
                    }
                }
                // Constant OP Index
                else if (right instanceof RegisterExpr reg && equivalenceClass.containsKey(reg.name())) {
                    Long constVal = getConstant(left, knownConstants, instruction.address(), cfg);
                    if (constVal != null) {
                        if (op == Operator.LESS_THAN || op == Operator.LESS_THAN_UNSIGNED) {
                            // not an upper bound
                        } else if (op == Operator.GREATER_EQUAL || op == Operator.GREATER_EQUAL_UNSIGNED) {
                            bound = constVal + 1;
                        } else if (op == Operator.EQUAL) {
                            bound = constVal + 1;
                        }
                    }
                }
                yield bound;
            }
        };
    }

    private Long getConstant(Expression expr, Map<String, Long> knownConstants, long currentAddr,
            DiscoveredProgram cfg) {
        if (expr instanceof ImmediateExpr imm) {
            return imm.value();
        } else if (expr instanceof RegisterExpr reg) {
            if (knownConstants.containsKey(reg.name())) {
                return knownConstants.get(reg.name());
            }
            return findConstantBackward(reg, currentAddr, cfg);
        }
        return null;
    }

    private Long findConstantBackward(RegisterExpr reg, long startAddr, DiscoveredProgram cfg) {
        long curr = startAddr;
        for (int i = 0; i < 10; i++) {
            final long fCurr = curr;
            InstructionIr pred = cfg.edges().stream().filter(e -> e.to() == fCurr)
                    .map(e -> cfg.instructions().stream().filter(inst -> inst.address() == e.from()).findFirst()
                            .orElse(null))
                    .filter(Objects::nonNull).findFirst().orElse(null);
            if (pred == null)
                return null;
            if (pred.semantic() != null && pred.semantic().lhs() instanceof RegisterExpr lhs
                    && lhs.name().equals(reg.name())) {
                // Try to evaluate the RHS as a constant expression (handles addi t0, zero, 4)
                Optional<Long> constVal = evaluateConstant(pred.semantic().rhs());
                if (constVal.isPresent())
                    return constVal.get();
                return null;
            }
            curr = pred.address();
        }
        return null;
    }

    private Operator invertOperator(Operator op) {
        return switch (op) {
            case LESS_THAN -> Operator.GREATER_EQUAL;
            case GREATER_EQUAL -> Operator.LESS_THAN;
            case LESS_THAN_UNSIGNED -> Operator.GREATER_EQUAL_UNSIGNED;
            case GREATER_EQUAL_UNSIGNED -> Operator.LESS_THAN_UNSIGNED;
            case EQUAL -> Operator.NOT_EQUAL;
            case NOT_EQUAL -> Operator.EQUAL;
            default -> op;
        };
    }

    /**
     * Checks if an operator is a comparison operator.
     */
    private boolean isComparisonOperator(Operator op) {
        return op == Operator.LESS_THAN || op == Operator.LESS_THAN_UNSIGNED
                || op == Operator.GREATER_EQUAL || op == Operator.GREATER_EQUAL_UNSIGNED
                || op == Operator.EQUAL || op == Operator.NOT_EQUAL;
    }

    /**
     * Resolves jump targets by iterating through the jump table and reading memory.
     *
     * @param structure  the jump table structure
     * @param upperBound the upper bound of the index
     * @param image      the binary image for memory reads
     * @return list of resolved target addresses
     */
    public List<Long> resolveTargets(JumpTableStructure structure, long upperBound, BinaryImage image) {
        List<Long> targets = new ArrayList<>();
        Optional<Long> baseOpt = evaluateConstant(structure.base());

        if (baseOpt.isEmpty()) {
            return targets; // Cannot evaluate base address
        }

        long base = baseOpt.get();
        int scale = structure.scale();

        for (long i = 0; i < upperBound; i++) {
            long targetAddress = base + (i * scale);
            long target = readWord(image, targetAddress);
            targets.add(target);
        }

        return targets;
    }

    /**
     * Reads a 32-bit little-endian word from the binary image at the given address.
     *
     * @param image   the binary image
     * @param address the virtual address to read from
     * @return the 32-bit value read
     */
    private long readWord(BinaryImage image, long address) {
        byte[] bytes = image.bytes();

        // Find the section containing this address
        for (var section : image.sections()) {
            if (address >= section.address() && address < section.address() + section.size()) {
                long offset = address - section.address() + section.offset();
                if (offset + 4 <= bytes.length) {
                    // Little-endian read
                    return (bytes[(int) offset] & 0xFFL)
                            | ((bytes[(int) offset + 1] & 0xFFL) << 8)
                            | ((bytes[(int) offset + 2] & 0xFFL) << 16)
                            | ((bytes[(int) offset + 3] & 0xFFL) << 24);
                }
            }
        }

        throw new MemoryAccessException("Address " + address + " not found in any section");
    }
}
