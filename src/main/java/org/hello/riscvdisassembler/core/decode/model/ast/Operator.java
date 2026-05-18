package org.hello.riscvdisassembler.core.decode.model.ast;

/**
 * Enumeration of binary operators supported in semantic expressions.
 */
public enum Operator {
    /** Arithmetic addition */
    ADD,
    /** Arithmetic subtraction */
    SUB,
    /** Arithmetic multiplication */
    MUL,
    /** Left shift */
    SHIFT_LEFT,
    /** Right shift */
    SHIFT_RIGHT,
    /** Bitwise AND */
    AND,
    /** Bitwise OR */
    OR,
    /** Bitwise XOR */
    XOR,
    /** Signed less-than comparison */
    LESS_THAN,
    /** Unsigned less-than comparison */
    LESS_THAN_UNSIGNED,
    /** Signed greater-or-equal comparison */
    GREATER_EQUAL,
    /** Unsigned greater-or-equal comparison */
    GREATER_EQUAL_UNSIGNED,
    /** Equality comparison */
    EQUAL,
    /** Inequality comparison */
    NOT_EQUAL
}
