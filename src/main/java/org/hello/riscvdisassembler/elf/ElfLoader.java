package org.hello.riscvdisassembler.elf;

import org.hello.riscvdisassembler.elf.model.ElfFile;
import org.hello.riscvdisassembler.elf.model.ElfHeader;
import org.hello.riscvdisassembler.elf.model.SectionHeader;
import org.hello.riscvdisassembler.elf.model.SymbolEntry;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Loads a 32-bit little-endian RISC-V ELF file into the project's in-memory model.
 *
 * <p>This loader validates the ELF identity fields, parses the ELF header, extracts
 * section headers, and reads symbol table entries needed by later stages.</p>
 */
public final class ElfLoader {
    private static final int ELFCLASS32 = 1;
    private static final int ELFDATA2LSB = 1;
    private static final int EM_RISCV = 243;
    private static final long SHT_SYMTAB = 2;
    private static final long SHT_DYNSYM = 11;

    /**
     * Reads only the ELF header from disk using lenient parsing.
     *
     * <p>This mode is useful for debugging unsupported or malformed files because it skips
     * the strict identity and machine checks required by full disassembly.</p>
     *
     * @param path path to the file on disk
     * @return parsed header fields interpreted using the ELF32 layout
     * @throws IOException if the file cannot be read
     * @throws ElfParsingException if the file is too small to contain an ELF32 header
     */
    public ElfHeader loadHeader(Path path) throws IOException {
        byte[] bytes = Files.readAllBytes(path);
        return parseHeader(bytes, false);
    }

    /**
     * Reads an ELF file from disk and converts it into an {@link ElfFile} model.
     *
     * @param path path to the ELF file on disk
     * @return parsed ELF representation including header, sections, symbols, and raw bytes
     * @throws IOException if the file cannot be read
     * @throws ElfParsingException if the file is malformed or uses unsupported ELF features
     */
    public ElfFile load(Path path) throws IOException {
        byte[] bytes = Files.readAllBytes(path);
        validateElf(bytes);

        ElfHeader header = parseHeader(bytes, true);
        List<SectionHeader> sections = parseSections(bytes, header);
        List<SymbolEntry> symbols = parseSymbols(bytes, sections);
        return new ElfFile(header, bytes, sections, symbols);
    }

    /**
     * Verifies the basic ELF identity fields before deeper parsing begins.
     *
     * @param bytes complete file contents
     * @throws ElfParsingException if the file is too small, lacks ELF magic, or is not
     *                              ELF32 little-endian
     */
    private void validateElf(byte[] bytes) {
        if (bytes.length < 52) {
            throw new ElfParsingException("File is too small to be a valid ELF32 file");
        }
        if (bytes[0] != 0x7F || bytes[1] != 'E' || bytes[2] != 'L' || bytes[3] != 'F') {
            throw new ElfParsingException("Invalid ELF magic");
        }
        if (u8(bytes, 4) != ELFCLASS32) {
            throw new ElfParsingException("Only ELF32 is supported");
        }
        if (u8(bytes, 5) != ELFDATA2LSB) {
            throw new ElfParsingException("Only little-endian ELF files are supported");
        }
    }

    /**
     * Parses the fixed-size ELF header from the file contents.
     *
     * @param bytes complete file contents
     * @return parsed ELF header information
     * @throws ElfParsingException if the file is not a RISC-V ELF or the header region
     *                              is unreadable
     */
    private ElfHeader parseHeader(byte[] bytes, boolean requireSupportedMachine) {
        ensureReadable(bytes, 0, 52, "ELF header");
        int fileClass = u8(bytes, 4);
        int dataEncoding = u8(bytes, 5);
        int type = u16(bytes, 16);
        int machine = u16(bytes, 18);
        long entryPoint = u32(bytes, 24);
        long sectionHeaderOffset = u32(bytes, 32);
        int sectionHeaderEntrySize = u16(bytes, 46);
        int sectionHeaderCount = u16(bytes, 48);
        int sectionNameTableIndex = u16(bytes, 50);

        if (requireSupportedMachine && machine != EM_RISCV) {
            throw new ElfParsingException("Unsupported machine type: " + machine + ". Expected RISC-V (243)");
        }

        return new ElfHeader(
                fileClass,
                dataEncoding,
                type,
                machine,
                entryPoint,
                sectionHeaderOffset,
                sectionHeaderEntrySize,
                sectionHeaderCount,
                sectionNameTableIndex
        );
    }

    /**
     * Parses all section headers and resolves their names through the section-name string table.
     *
     * @param bytes complete file contents
     * @param header previously parsed ELF header
     * @return ordered list of section headers
     * @throws ElfParsingException if the section header table or string table is invalid
     */
    private List<SectionHeader> parseSections(byte[] bytes, ElfHeader header) {
        List<RawSection> rawSections = new ArrayList<>();
        int shoff = Math.toIntExact(header.sectionHeaderOffset());
        if (header.sectionHeaderCount() == 0) {
            return Collections.emptyList();
        }
        if (header.sectionHeaderEntrySize() <= 0) {
            throw new ElfParsingException("Invalid section header entry size");
        }
        // The section header table is a fixed-size array of entries starting at e_shoff.
        ensureReadable(bytes, shoff, header.sectionHeaderCount() * header.sectionHeaderEntrySize(), "section header table");
        for (int i = 0; i < header.sectionHeaderCount(); i++) {
            int base = shoff + i * header.sectionHeaderEntrySize();
            // Read the raw ELF fields first; section names are resolved in a second pass
            // because sh_name is only an offset into the section-name string table.
            // sh là section header
            rawSections.add(new RawSection(
                    i,
                    (int) u32(bytes, base),     //sh_name , chú ý đây là offset vào shstrtab
                    u32(bytes, base + 4),       //sh_type
                    u32(bytes, base + 8),       //sh_flags
                    u32(bytes, base + 12),      //sh_addr
                    u32(bytes, base + 16),      //sh_offset
                    u32(bytes, base + 20),      //sh_size
                    (int) u32(bytes, base + 24),    //sh_link
                    (int) u32(bytes, base + 28),    //sh_info
                    u32(bytes, base + 36)       //sh_entsize
            ));
        }

        if (header.sectionNameTableIndex() < 0 || header.sectionNameTableIndex() >= rawSections.size()) {
            throw new ElfParsingException("Invalid section name table index");
        }
        RawSection stringTableSection = rawSections.get(header.sectionNameTableIndex());
        // e_shstrndx points to the section whose contents store the names of all sections.
        byte[] stringTable = slice(bytes, stringTableSection.offset, stringTableSection.size);

        List<SectionHeader> sections = new ArrayList<>();
        for (RawSection raw : rawSections) {
            sections.add(new SectionHeader(
                    raw.index,
                    readNullTerminatedString(stringTable, raw.nameOffset),
                    raw.type,
                    raw.flags,
                    raw.address,
                    raw.offset,
                    raw.size,
                    raw.link,
                    raw.info,
                    raw.entrySize
            ));
        }
        return sections;
    }

    /**
     * Parses all symbol-table sections found in the ELF file.
     *
     * @param bytes complete file contents
     * @param sections previously parsed section headers
     * @return list of decoded symbol entries
     * @throws ElfParsingException if a symbol table region cannot be read safely
     */
    private List<SymbolEntry> parseSymbols(byte[] bytes, List<SectionHeader> sections) {
        List<SymbolEntry> symbols = new ArrayList<>();
        for (SectionHeader section : sections) {
            //Chỉ xử lý 
            // SHT_SYMTAB: symbol table đầy đủ
            // SHT_DYNSYM: dynamic symbol table
            if (section.type() != SHT_SYMTAB && section.type() != SHT_DYNSYM) {
                continue;
            }
            // Kiểm tra symbol table có usable không:
            // - entrySize > 0: mỗi symbol entry phải có kích thước cố định
            // - link hợp lệ: sh_link phải trỏ tới section string table chứa tên symbol
            // Nếu thiếu 1 trong các điều kiện này thì bỏ qua section đó.
            if (section.entrySize() <= 0 || section.link() < 0 || section.link() >= sections.size()) {
                continue;
            }

            SectionHeader stringTableSection = sections.get(section.link());
            byte[] stringTable = slice(bytes, stringTableSection.offset(), stringTableSection.size());
            int symbolCount = Math.toIntExact(section.size() / section.entrySize());
            int base = Math.toIntExact(section.offset());
            ensureReadable(bytes, base, Math.toIntExact(section.size()), "symbol table");

            for (int i = 0; i < symbolCount; i++) {
                int entryBase = base + Math.toIntExact(i * section.entrySize());
                // In ELF32, each symbol entry stores the name as an offset into the linked
                // string table plus the symbol value/size/metadata fields.
                int nameOffset = (int) u32(bytes, entryBase);   //st_name
                long value = u32(bytes, entryBase + 4);         //st_value
                long size = u32(bytes, entryBase + 8);          //st_size
                int info = u8(bytes, entryBase + 12);           //st_info
                int other = u8(bytes, entryBase + 13);          //st_other
                int sectionIndex = u16(bytes, entryBase + 14);  //st_shndx
                
                String name = readNullTerminatedString(stringTable, nameOffset);
                symbols.add(new SymbolEntry(name, value, size, info, other, sectionIndex));
            }
        }
        return symbols;
    }

    /**
     * Ensures that a byte range is fully readable within the source array.
     *
     * @param bytes source byte array
     * @param offset start offset of the region
     * @param length length of the region
     * @param regionName human-readable name used in error messages
     * @throws ElfParsingException if the requested range lies outside the array bounds
     */
    private static void ensureReadable(byte[] bytes, int offset, int length, String regionName) {
        if (offset < 0 || length < 0 || offset > bytes.length - length) {
            throw new ElfParsingException("Invalid " + regionName + " boundaries");
        }
    }

    /**
     * Copies a range of bytes from the ELF file.
     *
     * @param source source byte array
     * @param offset starting offset within {@code source}
     * @param size number of bytes to copy
     * @return copied slice
     * @throws ElfParsingException if the requested range is invalid
     */
    private static byte[] slice(byte[] source, long offset, long size) {
        int start = Math.toIntExact(offset);
        int end = Math.toIntExact(offset + size);
        if (start < 0 || end > source.length || start > end) {
            throw new ElfParsingException("Invalid ELF slice boundaries");
        }
        byte[] result = new byte[end - start];
        System.arraycopy(source, start, result, 0, result.length);
        return result;
    }

    /**
     * Reads a null-terminated UTF-8 string from a string table.
     *
     * @param bytes string-table bytes
     * @param start starting offset of the string
     * @return decoded string, or an empty string when the offset is outside the array
     */
    private static String readNullTerminatedString(byte[] bytes, int start) {
        if (start < 0 || start >= bytes.length) {
            return "";
        }
        int end = start;
        while (end < bytes.length && bytes[end] != 0) {
            end++;
        }
        return new String(bytes, start, end - start, StandardCharsets.UTF_8);
    }

    /**
     * Reads one unsigned byte.
     *
     * @param bytes source byte array
     * @param offset byte offset
     * @return value in the range {@code 0..255}
     */
    private static int u8(byte[] bytes, int offset) {
        return Byte.toUnsignedInt(bytes[offset]);
    }

    /**
     * Reads one unsigned 16-bit little-endian value.
     *
     * @param bytes source byte array
     * @param offset first byte offset
     * @return unsigned 16-bit value widened to {@code int}
     */
    private static int u16(byte[] bytes, int offset) {
        return u8(bytes, offset) | (u8(bytes, offset + 1) << 8);
    }

    /**
     * Reads one unsigned 32-bit little-endian value.
     *
     * @param bytes source byte array
     * @param offset first byte offset
     * @return unsigned 32-bit value widened to {@code long}
     */
    private static long u32(byte[] bytes, int offset) {
        return Integer.toUnsignedLong(
                u8(bytes, offset)
                        | (u8(bytes, offset + 1) << 8)
                        | (u8(bytes, offset + 2) << 16)
                | (u8(bytes, offset + 3) << 24)
        );
    }

    /**
     * Temporary section representation used before section names are resolved.
     */
    private static final class RawSection {
        private final int index;
        private final int nameOffset;
        private final long type;
        private final long flags;
        private final long address;
        private final long offset;
        private final long size;
        private final int link;
        private final int info;
        private final long entrySize;

        /**
         * Creates a raw section record from directly parsed ELF fields.
         *
         * @param index section index within the section header table
         * @param nameOffset offset into the section-name string table
         * @param type ELF section type
         * @param flags ELF section flags
         * @param address virtual address of the section
         * @param offset file offset of the section contents
         * @param size section size in bytes
         * @param link auxiliary link field whose meaning depends on {@code type}
         * @param info auxiliary info field whose meaning depends on {@code type}
         * @param entrySize size of each fixed-size entry for table-like sections
         */
        private RawSection(int index, int nameOffset, long type, long flags, long address, long offset,
                           long size, int link, int info, long entrySize) {
            this.index = index;
            this.nameOffset = nameOffset;
            this.type = type;
            this.flags = flags;
            this.address = address;
            this.offset = offset;
            this.size = size;
            this.link = link;
            this.info = info;
            this.entrySize = entrySize;
        }
    }
}
