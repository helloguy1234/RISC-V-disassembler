package org.hello.riscvdisassembler.core.discover;

/**
 * One directed control-flow edge between decoded instructions.
 *
 * @param from source instruction address
 * @param to target instruction address
 */
public record ControlFlowEdge(long from, long to) {
}

