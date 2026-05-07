package org.hello.riscvdisassembler.core.discover;

import org.hello.riscvdisassembler.core.binary.model.BinarySection;
import org.hello.riscvdisassembler.core.binary.model.BinarySymbol;
import org.hello.riscvdisassembler.core.decode.InstructionDecoder;
import org.hello.riscvdisassembler.core.decode.model.InstructionIr;
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

import static org.hello.riscvdisassembler.core.decode.model.InstructionIr.ControlFlowType;

/**
 * Discovers which instruction addresses should be decoded before emitters render output.
 */
public final class CodeDiscoveryEngine {
    private final InstructionDecoder decoder;
    private final RegionClassifier regionClassifier = new RegionClassifier();

    /**
     * Creates a discovery engine backed by a concrete instruction decoder.
     *
     * @param decoder instruction decoder used for address-level decoding
     */
    public CodeDiscoveryEngine(InstructionDecoder decoder) {
        this.decoder = decoder;
    }

    /**
     * Discovers instructions according to the requested traversal mode.
     *
     * @param program resolved program metadata
     * @param mode traversal strategy
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

            long currentAddress = seed.address();
            while (program.containsExecutableAddress(section.name(), currentAddress)
                    && !instructionsByAddress.containsKey(currentAddress)) {
                InstructionIr instruction = decoder.decodeAt(program, section, currentAddress);
                instructionsByAddress.put(currentAddress, instruction);

                Long target = instruction.branchTarget();
                if (target != null && program.containsExecutableAddress(section.name(), target)) {
                    edges.add(new ControlFlowEdge(instruction.address(), target));
                }

                if (instruction.controlFlowType() == ControlFlowType.CONDITIONAL_BRANCH) {
                    enqueue(worklist, queuedAddresses, new Seed(section.name(), instruction.address() + 4));
                    if (target != null && program.containsExecutableAddress(section.name(), target)) {
                        enqueue(worklist, queuedAddresses, new Seed(section.name(), target));
                    }
                    break;
                }
                if (instruction.controlFlowType() == ControlFlowType.UNCONDITIONAL_JUMP) {
                    if (target != null && program.containsExecutableAddress(section.name(), target)) {
                        enqueue(worklist, queuedAddresses, new Seed(section.name(), target));
                    }
                    break;
                }
                if (instruction.controlFlowType() == ControlFlowType.CALL) {
                    if (target != null && program.findSectionContaining(target) != null) {
                        BinarySection targetSection = program.findSectionContaining(target);
                        enqueue(worklist, queuedAddresses, new Seed(targetSection.name(), target));
                    }
                    long fallthrough = instruction.address() + 4;
                    if (program.containsExecutableAddress(section.name(), fallthrough)) {
                        edges.add(new ControlFlowEdge(instruction.address(), fallthrough));
                        currentAddress = fallthrough;
                        continue;
                    }
                    break;
                }
                if (instruction.controlFlowType() == ControlFlowType.RETURN
                        || instruction.controlFlowType() == ControlFlowType.TERMINATOR) {
                    break;
                }

                long fallthrough = instruction.address() + 4;
                if (program.containsExecutableAddress(section.name(), fallthrough)) {
                    edges.add(new ControlFlowEdge(instruction.address(), fallthrough));
                    currentAddress = fallthrough;
                    continue;
                }
                break;
            }
        }

        List<InstructionIr> instructions = new ArrayList<>(instructionsByAddress.values());
        instructions.sort(Comparator.comparing(InstructionIr::sectionName).thenComparingLong(InstructionIr::address));

        return new DiscoveredProgram(program, instructions, new ArrayList<>(edges),
                regionClassifier.classify(program, instructions, DiscoveryMode.RECURSIVE),
                DiscoveryMode.RECURSIVE);
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

    private static boolean isTrustedCodeSeed(BinarySection section, BinarySymbol symbol, long address, long entryPoint) {
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

