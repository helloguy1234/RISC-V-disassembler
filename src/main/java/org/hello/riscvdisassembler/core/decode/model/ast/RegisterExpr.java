package org.hello.riscvdisassembler.core.decode.model.ast;

/**
 * Expression representing a register value.
 * Uses ABI register names (e.g., a0, sp, ra, x10).
 */
public record RegisterExpr(String name) implements Expression {
}
