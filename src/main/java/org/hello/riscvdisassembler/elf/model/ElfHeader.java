package org.hello.riscvdisassembler.elf.model;

/**
 * Immutable representation of the ELF file header.
 */
public final class ElfHeader {
    private final int fileClass;
    private final int dataEncoding;
    private final int type;
    private final int machine;
    private final long entryPoint;
    private final long sectionHeaderOffset;
    private final int sectionHeaderEntrySize;
    private final int sectionHeaderCount;
    private final int sectionNameTableIndex;

    /**
     * Creates an ELF header model.
     *
     * @param fileClass ELF class value, for example {@code 1} for ELF32
     * @param dataEncoding endianness descriptor from the ELF identification bytes
     * @param type ELF file type
     * @param machine target machine identifier
     * @param entryPoint program entry address
     * @param sectionHeaderOffset file offset of the section header table
     * @param sectionHeaderEntrySize size in bytes of one section header entry
     * @param sectionHeaderCount number of section headers
     * @param sectionNameTableIndex section index of the section-name string table
     */
    public ElfHeader(int fileClass, int dataEncoding, int type, int machine, long entryPoint,
                     long sectionHeaderOffset, int sectionHeaderEntrySize, int sectionHeaderCount,
                     int sectionNameTableIndex) {
        this.fileClass = fileClass;
        this.dataEncoding = dataEncoding;
        this.type = type;
        this.machine = machine;
        this.entryPoint = entryPoint;
        this.sectionHeaderOffset = sectionHeaderOffset;
        this.sectionHeaderEntrySize = sectionHeaderEntrySize;
        this.sectionHeaderCount = sectionHeaderCount;
        this.sectionNameTableIndex = sectionNameTableIndex;
    }

    /** @return ELF class value */
    public int fileClass() {
        return fileClass;
    }

    /** @return ELF data encoding / endianness flag */
    public int dataEncoding() {
        return dataEncoding;
    }

    /** @return ELF file type */
    public int type() {
        return type;
    }

    /** @return target machine identifier */
    public int machine() {
        return machine;
    }

    /** @return program entry point address */
    public long entryPoint() {
        return entryPoint;
    }

    /** @return file offset of the section header table */
    public long sectionHeaderOffset() {
        return sectionHeaderOffset;
    }

    /** @return size of one section header entry in bytes */
    public int sectionHeaderEntrySize() {
        return sectionHeaderEntrySize;
    }

    /** @return number of section headers in the file */
    public int sectionHeaderCount() {
        return sectionHeaderCount;
    }

    /** @return index of the section-name string table */
    public int sectionNameTableIndex() {
        return sectionNameTableIndex;
    }
}
