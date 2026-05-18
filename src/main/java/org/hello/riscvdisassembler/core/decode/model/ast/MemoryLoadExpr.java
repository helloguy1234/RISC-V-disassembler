package org.hello.riscvdisassembler.core.decode.model.ast;

/**
 * Expression representing a memory load operation.
 * The baseAddress expression computes the memory address to read from,
 * and sizeBytes specifies the number of bytes to load.
 */
public record MemoryLoadExpr(Expression baseAddress, int sizeBytes) implements Expression {
}
