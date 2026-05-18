package org.hello.riscvdisassembler.core.decode.model.ast;

/**
 * Expression representing an assignment operation.
 * Wraps the complete semantics of an instruction as lhs = rhs.
 */
public record AssignExpr(RegisterExpr lhs, Expression rhs) {
}
