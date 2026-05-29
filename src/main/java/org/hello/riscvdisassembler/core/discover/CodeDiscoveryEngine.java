package org.hello.riscvdisassembler.core.discover;

import org.hello.riscvdisassembler.core.binary.model.BinarySection;
import org.hello.riscvdisassembler.core.binary.model.BinarySymbol;
import org.hello.riscvdisassembler.core.decode.InstructionDecoder;
import org.hello.riscvdisassembler.core.decode.model.InstructionIr;
import org.hello.riscvdisassembler.core.decode.model.ast.Expression;
import org.hello.riscvdisassembler.core.discover.indirect.IndirectBranchResolver;
import org.hello.riscvdisassembler.core.discover.indirect.domain.State;
import org.hello.riscvdisassembler.core.resolve.ResolvedProgram;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;

/**
 * Discovers which instruction addresses should be decoded before emitters
 * render output.
 */
public final class CodeDiscoveryEngine {
    private final InstructionDecoder decoder;
    private final RegionClassifier regionClassifier = new RegionClassifier();
    private final IndirectBranchResolver indirectBranchResolver;

    /**
     * Creates a discovery engine backed by a concrete instruction decoder.
     *
     * @param decoder instruction decoder used for address-level decoding
     */
    public CodeDiscoveryEngine(InstructionDecoder decoder) {
        this(decoder, new IndirectBranchResolver());
    }

    // Visible for testing
    CodeDiscoveryEngine(InstructionDecoder decoder, IndirectBranchResolver indirectBranchResolver) {
        this.decoder = decoder;
        this.indirectBranchResolver = indirectBranchResolver;
    }

    /**
     * Discovers instructions according to the requested traversal mode.
     *
     * @param program resolved program metadata
     * @param mode    traversal strategy
     * @return discovered reachable instructions and edges
     */
    public DiscoveredProgram discover(ResolvedProgram program, DiscoveryMode mode) {
        if (mode == DiscoveryMode.LINEAR) {
            return discoverLinear(program);
        }
        return discoverRecursive(program);
    }

    private DiscoveredProgram discoverLinear(ResolvedProgram program) {
        List<InstructionIr> instructions = new ArrayList<>();
        List<ControlFlowEdge> edges = new ArrayList<>();

        for (BinarySection section : program.executableSections()) {
            for (long address = section.address(); address + 3 < section.address() + section.size(); address += 4) {
                InstructionIr instruction = decoder.decodeAt(program, section, address);
                instructions.add(instruction);
            }
        }

        for (int i = 0; i + 1 < instructions.size(); i++) {
            InstructionIr current = instructions.get(i);
            InstructionIr next = instructions.get(i + 1);
            if (isSequentialSuccessor(current, next)) {
                edges.add(new ControlFlowEdge(current.address(), next.address()));
            }
            if (current.branchTarget() != null) {
                edges.add(new ControlFlowEdge(current.address(), current.branchTarget()));
            }
        }
        if (!instructions.isEmpty() && instructions.getLast().branchTarget() != null) {
            InstructionIr last = instructions.getLast();
            edges.add(new ControlFlowEdge(last.address(), last.branchTarget()));
        }

        return new DiscoveredProgram(program, instructions, edges,
                regionClassifier.classify(program, instructions, DiscoveryMode.LINEAR),
                DiscoveryMode.LINEAR);
    }

    private DiscoveredProgram discoverRecursive(ResolvedProgram program) {
        Map<Long, InstructionIr> instructionsByAddress = new LinkedHashMap<>();
        Set<ControlFlowEdge> edges = new LinkedHashSet<>();
        Deque<Seed> worklist = new ArrayDeque<>(collectSeeds(program));
        Set<Long> queuedAddresses = new LinkedHashSet<>();
        for (Seed seed : worklist) {
            queuedAddresses.add(seed.address());
        }

        while (!worklist.isEmpty()) {
            Seed seed = worklist.removeFirst();
            queuedAddresses.remove(seed.address());
            if (instructionsByAddress.containsKey(seed.address())) {
                continue;
            }

            BinarySection section = program.findSectionContaining(seed.address());
            if (section == null || !seed.sectionName().equals(section.name())) {
                continue;
            }

            processInstructionSequence(seed, section, program, instructionsByAddress, edges,
                    worklist, queuedAddresses);
        }

        List<InstructionIr> instructions = new ArrayList<>(instructionsByAddress.values());
        instructions.sort(Comparator.comparing(InstructionIr::sectionName).thenComparingLong(InstructionIr::address));

        return new DiscoveredProgram(program, instructions, new ArrayList<>(edges),
                regionClassifier.classify(program, instructions, DiscoveryMode.RECURSIVE),
                DiscoveryMode.RECURSIVE);
    }

    private void processInstructionSequence(Seed seed, BinarySection section, ResolvedProgram program,
            Map<Long, InstructionIr> instructionsByAddress, Set<ControlFlowEdge> edges,
            Deque<Seed> worklist, Set<Long> queuedAddresses) {
        long currentAddress = seed.address();
        boolean shouldContinue = true;
        while (shouldContinue && program.containsExecutableAddress(section.name(), currentAddress)
                && !instructionsByAddress.containsKey(currentAddress)) {
            InstructionIr instruction = decoder.decodeAt(program, section, currentAddress);
            instructionsByAddress.put(currentAddress, instruction);

            Long target = instruction.branchTarget();
            if (target != null && program.containsExecutableAddress(section.name(), target)) {
                edges.add(new ControlFlowEdge(instruction.address(), target));
            }

            if (instruction.mnemonic().equals("jalr") && instruction.semantic() != null) {
                handleIndirectBranch(instruction, program, instructionsByAddress, edges,
                        worklist, queuedAddresses);
            }

            shouldContinue = processControlFlow(instruction, target, section, program,
                    edges, worklist, queuedAddresses);
            if (shouldContinue) {
                currentAddress = instruction.address() + 4;
            }
        }
    }

    private void handleIndirectBranch(InstructionIr instruction, ResolvedProgram program,
            Map<Long, InstructionIr> instructionsByAddress, Set<ControlFlowEdge> edges,
            Deque<Seed> worklist, Set<Long> queuedAddresses) {
        DiscoveredProgram snapshot = new DiscoveredProgram(
                program,
                new ArrayList<>(instructionsByAddress.values()),
                new ArrayList<>(edges),
                List.of(),
                DiscoveryMode.RECURSIVE);
        State state = indirectBranchResolver.buildState(instruction, snapshot);
        Expression rawTarget = instruction.semantic().rhs();
        Expression jalrAst = State.substitute(rawTarget, state);
        System.out.println(
                "DEBUG SJA: jalr at " + Long.toHexString(instruction.address()) + " has AST: " + jalrAst);
        var structureOpt = indirectBranchResolver.extractStructure(jalrAst);
        System.out.println("DEBUG SJA: structureOpt.isPresent=" + structureOpt.isPresent());
        if (structureOpt.isPresent()) {
            var structure = structureOpt.get();
            Long bound = indirectBranchResolver.findUpperBound(structure.index(), instruction, snapshot);
            System.out.println("DEBUG SJA: findUpperBound bound=" + bound);
            if (bound != null) {
                List<Long> targets = indirectBranchResolver.resolveTargets(
                        structure, bound, program.binaryImage());
                for (Long resolvedTarget : targets) {
                    edges.add(new ControlFlowEdge(instruction.address(), resolvedTarget));
                    BinarySection targetSection = program.findSectionContaining(resolvedTarget);
                    if (targetSection != null) {
                        enqueue(worklist, queuedAddresses,
                                new Seed(targetSection.name(), resolvedTarget));
                    }
                }
            }
        }
    }

    private boolean processControlFlow(InstructionIr instruction, Long target,
            BinarySection section, ResolvedProgram program,
            Set<ControlFlowEdge> edges, Deque<Seed> worklist, Set<Long> queuedAddresses) {
        return switch (instruction.controlFlowType()) {
            case CONDITIONAL_BRANCH ->
                processConditionalBranch(instruction, target, section, program, edges, worklist, queuedAddresses);
            case UNCONDITIONAL_JUMP -> processUnconditionalJump(target, section, program, worklist, queuedAddresses);
            case CALL -> processCall(instruction, target, section, program, edges, worklist, queuedAddresses);
            case RETURN, TERMINATOR -> false;
            default -> processFallthrough(instruction, section, program, edges);
        };
    }

    private boolean processConditionalBranch(InstructionIr instruction, Long target,
            BinarySection section, ResolvedProgram program,
            Set<ControlFlowEdge> edges, Deque<Seed> worklist, Set<Long> queuedAddresses) {
        long fallthrough = instruction.address() + 4;
        if (program.containsExecutableAddress(section.name(), fallthrough)) {
            edges.add(new ControlFlowEdge(instruction.address(), fallthrough));
            enqueue(worklist, queuedAddresses, new Seed(section.name(), fallthrough));
        }
        if (target != null && program.containsExecutableAddress(section.name(), target)) {
            enqueue(worklist, queuedAddresses, new Seed(section.name(), target));
        }
        return false;
    }

    private boolean processUnconditionalJump(Long target, BinarySection section,
            ResolvedProgram program, Deque<Seed> worklist, Set<Long> queuedAddresses) {
        if (target != null && program.containsExecutableAddress(section.name(), target)) {
            enqueue(worklist, queuedAddresses, new Seed(section.name(), target));
        }
        return false;
    }

    private boolean processCall(InstructionIr instruction, Long target,
            BinarySection section, ResolvedProgram program,
            Set<ControlFlowEdge> edges, Deque<Seed> worklist, Set<Long> queuedAddresses) {
        if (target != null && program.findSectionContaining(target) != null) {
            BinarySection targetSection = program.findSectionContaining(target);
            enqueue(worklist, queuedAddresses, new Seed(targetSection.name(), target));
        }
        long fallthrough = instruction.address() + 4;
        if (program.containsExecutableAddress(section.name(), fallthrough)) {
            edges.add(new ControlFlowEdge(instruction.address(), fallthrough));
            return true;
        }
        return false;
    }

    private boolean processFallthrough(InstructionIr instruction, BinarySection section,
            ResolvedProgram program, Set<ControlFlowEdge> edges) {
        long fallthrough = instruction.address() + 4;
        if (program.containsExecutableAddress(section.name(), fallthrough)) {
            edges.add(new ControlFlowEdge(instruction.address(), fallthrough));
            return true;
        }
        return false;
    }

    private List<Seed> collectSeeds(ResolvedProgram program) {
        LinkedHashMap<Long, Seed> seeds = new LinkedHashMap<>();

        long entryPoint = program.binaryImage().entryPoint();
        BinarySection entrySection = program.findSectionContaining(entryPoint);
        if (entrySection != null) {
            seeds.put(entryPoint, new Seed(entrySection.name(), entryPoint));
        }

        for (BinarySection section : program.executableSections()) {
            NavigableMap<Long, BinarySymbol> sectionSymbols = program.symbolsByAddress();
            for (Map.Entry<Long, BinarySymbol> entry : sectionSymbols.entrySet()) {
                long address = entry.getKey();
                BinarySymbol symbol = entry.getValue();
                if (program.containsExecutableAddress(section.name(), address)
                        && isTrustedCodeSeed(section, symbol, address, entryPoint)) {
                    seeds.putIfAbsent(address, new Seed(section.name(), address));
                }
            }
        }

        if (seeds.isEmpty()) {
            for (BinarySection section : program.executableSections()) {
                seeds.put(section.address(), new Seed(section.name(), section.address()));
            }
        }
        return new ArrayList<>(seeds.values());
    }

    private static boolean isTrustedCodeSeed(BinarySection section, BinarySymbol symbol, long address,
            long entryPoint) {
        if (address == entryPoint || address == section.address()) {
            return true;
        }

        String name = symbol.name();
        if (name == null || name.isBlank()) {
            return false;
        }
        if (name.startsWith("$") || name.startsWith(".")) {
            return false;
        }

        int type = symbol.info() & 0x0F;
        int binding = (symbol.info() >>> 4) & 0x0F;

        // Trust explicit function symbols first; otherwise accept exported code labels.
        return type == 2 || binding == 1 || binding == 2;
    }

    private static void enqueue(Deque<Seed> worklist, Set<Long> queuedAddresses, Seed seed) {
        if (queuedAddresses.add(seed.address())) {
            worklist.addLast(seed);
        }
    }

    private static boolean isSequentialSuccessor(InstructionIr current, InstructionIr next) {
        return current.sectionName().equals(next.sectionName())
                && next.address() == current.address() + 4;
    }

    private record Seed(String sectionName, long address) {
    }
}
