package org.hello.riscvdisassembler.core.decode.model.ast;

/**
 * Expression representing a constant immediate value.
 */
public record ImmediateExpr(long value) implements Expression {
}
