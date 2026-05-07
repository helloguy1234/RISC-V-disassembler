package org.hello.riscvdisassembler.core.binary.model;

/**
 * Canonical section model used by core pipeline stages independently of input format.
 */
public final class BinarySection {
    private final int index;
    private final String name;
    private final long address;
    private final long offset;
    private final long size;
    private final boolean executable;

    /**
     * Creates a canonical binary section.
     *
     * @param index section index in the source format
     * @param name section name
     * @param address virtual address
     * @param offset file offset
     * @param size size in bytes
     * @param executable whether the section should be treated as executable
     */
    public BinarySection(int index, String name, long address, long offset, long size, boolean executable) {
        this.index = index;
        this.name = name;
        this.address = address;
        this.offset = offset;
        this.size = size;
        this.executable = executable;
    }

    public int index() {
        return index;
    }

    public String name() {
        return name;
    }

    public long address() {
        return address;
    }

    public long offset() {
        return offset;
    }

    public long size() {
        return size;
    }

    public boolean executable() {
        return executable;
    }
}

