package org.hello.riscvdisassembler.elf.model;

/**
 * Immutable representation of one ELF symbol-table entry.
 */
public final class SymbolEntry {
    private final String name;
    private final long value;
    private final long size;
    private final int info;
    private final int other;
    private final int sectionIndex;

    /**
     * Creates a symbol-table entry model.
     *
     * @param name symbol name resolved from the linked string table
     * @param value symbol value, typically an address for code/data symbols
     * @param size symbol size in bytes
     * @param info ELF symbol info byte
     * @param other ELF symbol visibility / auxiliary byte
     * @param sectionIndex section index referenced by the symbol
     */
    public SymbolEntry(String name, long value, long size, int info, int other, int sectionIndex) {
        this.name = name;
        this.value = value;
        this.size = size;
        this.info = info;
        this.other = other;
        this.sectionIndex = sectionIndex;
    }

    /** @return symbol name */
    public String name() {
        return name;
    }

    /** @return symbol value, usually an address */
    public long value() {
        return value;
    }

    /** @return symbol size in bytes */
    public long size() {
        return size;
    }

    /** @return raw ELF info byte */
    public int info() {
        return info;
    }

    /** @return raw ELF auxiliary byte */
    public int other() {
        return other;
    }

    /** @return owning section index */
    public int sectionIndex() {
        return sectionIndex;
    }
}
