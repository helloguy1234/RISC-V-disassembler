package org.hello.riscvdisassembler.core.discover;

/**
 * High-level classification of one discovered region inside an executable section.
 */
public enum RegionKind {
    CODE,
    DATA,
    UNKNOWN,
    ALIGNMENT
}

