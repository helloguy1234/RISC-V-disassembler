package org.hello.riscvdisassembler.core.discover;

import org.hello.riscvdisassembler.core.decode.model.InstructionIr;
import org.hello.riscvdisassembler.core.resolve.ResolvedProgram;

import java.util.List;

/**
 * Output of the discovery phase: resolved metadata plus decoded reachable instructions.
 */
public final class DiscoveredProgram {
    private final ResolvedProgram resolvedProgram;
    private final List<InstructionIr> instructions;
    private final List<ControlFlowEdge> edges;
    private final List<DiscoveredRegion> regions;
    private final DiscoveryMode mode;

    /**
     * Creates a discovered-program view.
     *
     * @param resolvedProgram resolved metadata used during discovery
     * @param instructions decoded instructions retained by the traversal strategy
     * @param edges direct control-flow edges between retained instructions
     * @param regions discovered code/data regions
     * @param mode discovery strategy that produced this result
     */
    public DiscoveredProgram(ResolvedProgram resolvedProgram, List<InstructionIr> instructions, List<ControlFlowEdge> edges,
                             List<DiscoveredRegion> regions, DiscoveryMode mode) {
        this.resolvedProgram = resolvedProgram;
        this.instructions = List.copyOf(instructions);
        this.edges = List.copyOf(edges);
        this.regions = List.copyOf(regions);
        this.mode = mode;
    }

    /** @return underlying resolved program metadata */
    public ResolvedProgram resolvedProgram() {
        return resolvedProgram;
    }

    /** @return decoded instructions kept by discovery */
    public List<InstructionIr> instructions() {
        return instructions;
    }

    /** @return direct control-flow edges collected during discovery */
    public List<ControlFlowEdge> edges() {
        return edges;
    }

    /** @return discovered code/data regions */
    public List<DiscoveredRegion> regions() {
        return regions;
    }

    /** @return traversal strategy that produced this discovered program */
    public DiscoveryMode mode() {
        return mode;
    }
}

