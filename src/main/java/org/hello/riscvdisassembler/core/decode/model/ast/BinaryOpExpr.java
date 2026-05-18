package org.hello.riscvdisassembler.core.decode.model.ast;

/**
 * Expression representing a binary operation on two sub-expressions.
 */
public record BinaryOpExpr(Operator op, Expression left, Expression right) implements Expression {
}
