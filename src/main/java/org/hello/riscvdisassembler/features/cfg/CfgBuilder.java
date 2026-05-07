package org.hello.riscvdisassembler.features.cfg;

import org.hello.riscvdisassembler.core.discover.DiscoveredProgram;
import org.hello.riscvdisassembler.core.discover.ControlFlowEdge;
import org.hello.riscvdisassembler.core.decode.model.InstructionIr;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Builds a lightweight control-flow graph from decoded instructions.
 */
public final class CfgBuilder {
    /**
     * Builds the CFG and emits a textual summary.
     *
     * @param program discovered program used for entry-point reporting and CFG construction
     * @return human-readable CFG summary
     */
    public String emit(DiscoveredProgram program) {
        List<BasicBlock> blocks = build(program);
        StringBuilder sb = new StringBuilder();
        sb.append("CFG summary").append(System.lineSeparator());
        sb.append("Discovery mode: ").append(program.mode().name().toLowerCase()).append(System.lineSeparator());
        sb.append("Entry point: ").append(hex(program.resolvedProgram().binaryImage().entryPoint())).append(System.lineSeparator());
        sb.append("Basic blocks: ").append(blocks.size()).append(System.lineSeparator());
        sb.append(System.lineSeparator());

        for (BasicBlock block : blocks) {
            sb.append("Block ")
                    .append(hex(block.startAddress()))
                    .append(" -> ")
                    .append(hex(block.endAddress()))
                    .append(" | successors: ")
                    .append(block.successors().stream().map(CfgBuilder::hex).collect(Collectors.joining(", ")))
                    .append(System.lineSeparator());
        }
        return sb.toString();
    }

    /**
     * Partitions the instruction stream into basic blocks and computes successor edges.
     *
     * @param program discovered program containing instructions and control-flow edges
     * @return list of basic blocks in traversal order
     */
    public List<BasicBlock> build(DiscoveredProgram program) {
        List<InstructionIr> instructions = program.instructions();
        if (instructions.isEmpty()) {
            return Collections.emptyList();
        }

        Map<Long, List<Long>> outgoingEdges = buildOutgoingEdges(program.edges());
        Map<Long, InstructionIr> instructionsByAddress = buildInstructionMap(instructions);
        TreeSet<Long> leaders = new TreeSet<>();
        leaders.add(instructions.get(0).address());
        for (ControlFlowEdge edge : program.edges()) {
            InstructionIr fromInstruction = instructionsByAddress.get(edge.from());
            InstructionIr toInstruction = instructionsByAddress.get(edge.to());
            if (fromInstruction == null || toInstruction == null || !isSequentialSuccessor(fromInstruction, toInstruction)) {
                leaders.add(edge.to());
            }
        }

        for (int i = 0; i < instructions.size(); i++) {
            InstructionIr instruction = instructions.get(i);
            boolean hasNext = i + 1 < instructions.size();
            if (!hasNext) {
                continue;
            }

            InstructionIr nextInstruction = instructions.get(i + 1);
            List<Long> outgoing = outgoingEdges.getOrDefault(instruction.address(), Collections.emptyList());
            boolean hasSequentialEdge = outgoing.contains(nextInstruction.address()) && isSequentialSuccessor(instruction, nextInstruction);

            if (!isSequentialSuccessor(instruction, nextInstruction)
                    || outgoing.isEmpty()
                    || outgoing.size() > 1
                    || !hasSequentialEdge) {
                leaders.add(nextInstruction.address());
            }
        }

        List<BasicBlock> blocks = new ArrayList<>();
        int index = 0;
        while (index < instructions.size()) {
            InstructionIr first = instructions.get(index);
            int endIndex = index;
            while (endIndex + 1 < instructions.size()
                    && !leaders.contains(instructions.get(endIndex + 1).address())) {
                endIndex++;
            }

            InstructionIr last = instructions.get(endIndex);
            Set<Long> successors = new LinkedHashSet<>(outgoingEdges.getOrDefault(last.address(), Collections.emptyList()));

            blocks.add(new BasicBlock(first.address(), last.address(), new ArrayList<Long>(successors)));
            index = endIndex + 1;
        }

        return blocks;
    }

    private static Map<Long, List<Long>> buildOutgoingEdges(List<ControlFlowEdge> edges) {
        Map<Long, List<Long>> outgoingEdges = new HashMap<>();
        for (ControlFlowEdge edge : edges) {
            outgoingEdges.computeIfAbsent(edge.from(), ignored -> new ArrayList<>()).add(edge.to());
        }
        return outgoingEdges;
    }

    private static Map<Long, InstructionIr> buildInstructionMap(List<InstructionIr> instructions) {
        Map<Long, InstructionIr> instructionsByAddress = new HashMap<>();
        for (InstructionIr instruction : instructions) {
            instructionsByAddress.put(instruction.address(), instruction);
        }
        return instructionsByAddress;
    }

    /**
     * Formats a value as an 8-digit hexadecimal string.
     *
     * @param value numeric value to format
     * @return hexadecimal string prefixed with {@code 0x}
     */
    private static String hex(long value) {
        return String.format("0x%08x", value);
    }

    /**
     * Determines whether {@code next} is the fall-through instruction after {@code current}.
     *
     * @param current current instruction
     * @param next candidate successor instruction
     * @return {@code true} when both instructions are in the same section and their
     * addresses differ by 4 bytes
     */
    private static boolean isSequentialSuccessor(InstructionIr current, InstructionIr next) {
        return current.sectionName().equals(next.sectionName())
                && next.address() == current.address() + 4;
    }
}

