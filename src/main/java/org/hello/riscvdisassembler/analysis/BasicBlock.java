package org.hello.riscvdisassembler.analysis;

import java.util.List;

/**
 * Immutable representation of a basic block in the control-flow graph.
 */
public final class BasicBlock {
    private final long startAddress;
    private final long endAddress;
    private final List<Long> successors;

    /**
     * Creates a basic block description.
     *
     * @param startAddress address of the first instruction in the block
     * @param endAddress address of the last instruction in the block
     * @param successors addresses of successor block leaders
     */
    public BasicBlock(long startAddress, long endAddress, List<Long> successors) {
        this.startAddress = startAddress;
        this.endAddress = endAddress;
        this.successors = successors;
    }

    /** @return address of the first instruction in the block */
    public long startAddress() {
        return startAddress;
    }

    /** @return address of the last instruction in the block */
    public long endAddress() {
        return endAddress;
    }

    /** @return ordered list of successor block start addresses */
    public List<Long> successors() {
        return successors;
    }
}
