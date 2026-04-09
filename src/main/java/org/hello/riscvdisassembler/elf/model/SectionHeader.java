package org.hello.riscvdisassembler.elf.model;

/**
 * Immutable representation of one ELF section header.
 */
public final class SectionHeader {
    private final int index;
    private final String name;
    private final long type;
    private final long flags;
    private final long address;
    private final long offset;
    private final long size;
    private final int link;
    private final int info;
    private final long entrySize;

    /**
     * Creates a section-header model.
     *
     * @param index section index within the ELF section table
     * @param name resolved section name
     * @param type ELF section type
     * @param flags ELF section flags bitmask
     * @param address virtual address where the section is loaded
     * @param offset file offset of section contents
     * @param size section size in bytes
     * @param link auxiliary link field
     * @param info auxiliary info field
     * @param entrySize entry size for table-like sections, or {@code 0} otherwise
     */
    public SectionHeader(int index, String name, long type, long flags, long address, long offset,
                         long size, int link, int info, long entrySize) {
        this.index = index;
        this.name = name;
        this.type = type;
        this.flags = flags;
        this.address = address;
        this.offset = offset;
        this.size = size;
        this.link = link;
        this.info = info;
        this.entrySize = entrySize;
    }

    /** @return section index */
    public int index() {
        return index;
    }

    /** @return resolved section name */
    public String name() {
        return name;
    }

    /** @return ELF section type */
    public long type() {
        return type;
    }

    /** @return ELF section flags bitmask */
    public long flags() {
        return flags;
    }

    /** @return virtual load address of the section */
    public long address() {
        return address;
    }

    /** @return file offset where the section contents begin */
    public long offset() {
        return offset;
    }

    /** @return section size in bytes */
    public long size() {
        return size;
    }

    /** @return auxiliary link field */
    public int link() {
        return link;
    }

    /** @return auxiliary info field */
    public int info() {
        return info;
    }

    /** @return table entry size for sections containing fixed-size entries */
    public long entrySize() {
        return entrySize;
    }

    /**
     * Indicates whether the section contains executable code.
     *
     * @return {@code true} when the executable flag is set and the section is non-empty
     */
    public boolean isExecutable() {
        return (flags & 0x4L) != 0 && size > 0;
    }
}
