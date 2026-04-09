package org.hello.riscvdisassembler.elf.model;

import java.util.Arrays;
import java.util.List;

/**
 * Aggregate model representing a parsed ELF file.
 *
 * <p>This object keeps together the ELF header, raw file bytes, parsed sections, and
 * parsed symbols so later pipeline stages can access what they need without reparsing.</p>
 */
public final class ElfFile {
    private final ElfHeader header;
    private final byte[] bytes;
    private final List<SectionHeader> sections;
    private final List<SymbolEntry> symbols;

    /**
     * Creates a complete ELF model.
     *
     * @param header parsed ELF header
     * @param bytes raw file bytes
     * @param sections parsed section headers
     * @param symbols parsed symbol entries
     */
    public ElfFile(ElfHeader header, byte[] bytes, List<SectionHeader> sections, List<SymbolEntry> symbols) {
        this.header = header;
        this.bytes = bytes;
        this.sections = sections;
        this.symbols = symbols;
    }

    /**
     * Returns the parsed ELF header.
     *
     * @return ELF header model
     */
    public ElfHeader header() {
        return header;
    }

    /**
     * Returns the raw file contents.
     *
     * @return backing byte array for the ELF file
     */
    public byte[] bytes() {
        return bytes;
    }

    /**
     * Returns all parsed section headers.
     *
     * @return section list in table order
     */
    public List<SectionHeader> sections() {
        return sections;
    }

    /**
     * Returns all parsed symbol entries.
     *
     * @return symbol list collected from symbol-table sections
     */
    public List<SymbolEntry> symbols() {
        return symbols;
    }

    /**
     * Extracts the raw bytes that belong to a given section.
     *
     * @param section section whose byte range should be copied
     * @return a new byte array containing that section's contents
     */
    public byte[] slice(SectionHeader section) {
        int start = Math.toIntExact(section.offset());
        int end = Math.toIntExact(section.offset() + section.size());
        return Arrays.copyOfRange(bytes, start, end);
    }
}
