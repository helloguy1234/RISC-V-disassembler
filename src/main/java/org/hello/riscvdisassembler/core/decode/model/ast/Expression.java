package org.hello.riscvdisassembler.core.decode.model.ast;

/**
 * Sealed interface for all expression nodes in the semantic AST.
 * Expressions represent values computed during instruction execution.
 */
public sealed interface Expression permits RegisterExpr, ImmediateExpr, UnknownExpr, BinaryOpExpr, MemoryLoadExpr {
}
