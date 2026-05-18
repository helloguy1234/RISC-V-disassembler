package org.hello.riscvdisassembler.core.decode.model.ast;

/**
 * Expression representing an unknown or uncomputable value (Top/⊤).
 * Used as a safe fallback for instructions that cannot be modeled.
 */
public record UnknownExpr() implements Expression {
}
