package org.hello.riscvdisassembler.core.binary.model;

/**
 * Canonical symbol model shared by core stages independently of input adapter
 * format.
 *
 * @param name         symbol name
 * @param value        symbol value/address
 * @param size         symbol size in bytes
 * @param info         raw symbol info field
 * @param other        raw symbol auxiliary field
 * @param sectionIndex owning section index
 */
public record BinarySymbol(
        String name,
        long value,
        long size,
        int info,
        int other,
        int sectionIndex) {
}
