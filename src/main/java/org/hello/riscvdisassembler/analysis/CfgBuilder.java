package org.hello.riscvdisassembler.analysis;

import org.hello.riscvdisassembler.ir.InstructionIr;
import org.hello.riscvdisassembler.resolver.ResolvedProgram;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.hello.riscvdisassembler.ir.InstructionIr.ControlFlowType;

/**
 * Builds a lightweight control-flow graph from decoded instructions.
 */
public final class CfgBuilder {
    /**
     * Builds the CFG and emits a textual summary.
     *
     * @param program resolved program used for entry-point reporting
     * @param instructions decoded instructions from which the CFG is derived
     * @return human-readable CFG summary
     */
    public String emit(ResolvedProgram program, List<InstructionIr> instructions) {
        List<BasicBlock> blocks = build(instructions);
        StringBuilder sb = new StringBuilder();
        sb.append("CFG summary").append(System.lineSeparator());
        sb.append("Entry point: ").append(hex(program.elfFile().header().entryPoint())).append(System.lineSeparator());
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
     * @param instructions decoded instructions in address order
     * @return list of basic blocks in traversal order
     */
    public List<BasicBlock> build(List<InstructionIr> instructions) {
        if (instructions.isEmpty()) {
            return Collections.emptyList();
        }

        TreeSet<Long> leaders = new TreeSet<>();
        leaders.add(instructions.get(0).address());

        for (int i = 0; i < instructions.size(); i++) {
            InstructionIr instruction = instructions.get(i);
            if (instruction.branchTarget() != null) {
                leaders.add(instruction.branchTarget());
            }

            boolean hasNext = i + 1 < instructions.size();
            if (!hasNext) {
                continue;
            }

            long nextAddress = instructions.get(i + 1).address();
            if (!isSequentialSuccessor(instruction, instructions.get(i + 1))
                    || instruction.controlFlowType() == ControlFlowType.CONDITIONAL_BRANCH
                    || instruction.controlFlowType() == ControlFlowType.UNCONDITIONAL_JUMP
                    || instruction.controlFlowType() == ControlFlowType.RETURN
                    || instruction.controlFlowType() == ControlFlowType.TERMINATOR
                    || instruction.controlFlowType() == ControlFlowType.CALL) {
                leaders.add(nextAddress);
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
            Set<Long> successors = new LinkedHashSet<>();
            if (last.controlFlowType() == ControlFlowType.CONDITIONAL_BRANCH) {
                if (last.branchTarget() != null) {
                    successors.add(last.branchTarget());
                }
                if (endIndex + 1 < instructions.size() && isSequentialSuccessor(last, instructions.get(endIndex + 1))) {
                    successors.add(instructions.get(endIndex + 1).address());
                }
            } else if (last.controlFlowType() == ControlFlowType.UNCONDITIONAL_JUMP) {
                if (last.branchTarget() != null) {
                    successors.add(last.branchTarget());
                }
            } else if (last.controlFlowType() == ControlFlowType.CALL || last.controlFlowType() == ControlFlowType.NORMAL) {
                if (endIndex + 1 < instructions.size() && isSequentialSuccessor(last, instructions.get(endIndex + 1))) {
                    successors.add(instructions.get(endIndex + 1).address());
                }
            }

            blocks.add(new BasicBlock(first.address(), last.address(), new ArrayList<Long>(successors)));
            index = endIndex + 1;
        }

        return blocks;
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
