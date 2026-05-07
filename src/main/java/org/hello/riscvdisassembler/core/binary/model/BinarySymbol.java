package org.hello.riscvdisassembler.core.binary.model;

/**
 * Canonical symbol model shared by core stages independently of input adapter format.
 */
public final class BinarySymbol {
    private final String name;
    private final long value;
    private final long size;
    private final int info;
    private final int other;
    private final int sectionIndex;

    /**
     * Creates a canonical binary symbol.
     *
     * @param name symbol name
     * @param value symbol value/address
     * @param size symbol size in bytes
     * @param info raw symbol info field
     * @param other raw symbol auxiliary field
     * @param sectionIndex owning section index
     */
    public BinarySymbol(String name, long value, long size, int info, int other, int sectionIndex) {
        this.name = name;
        this.value = value;
        this.size = size;
        this.info = info;
        this.other = other;
        this.sectionIndex = sectionIndex;
    }

    public String name() {
        return name;
    }

    public long value() {
        return value;
    }

    public long size() {
        return size;
    }

    public int info() {
        return info;
    }

    public int other() {
        return other;
    }

    public int sectionIndex() {
        return sectionIndex;
    }
}

