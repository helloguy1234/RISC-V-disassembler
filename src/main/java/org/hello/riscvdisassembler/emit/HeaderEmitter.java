package org.hello.riscvdisassembler.emit;

import org.hello.riscvdisassembler.elf.model.ElfHeader;

/**
 * Renders ELF header metadata as a human-readable text summary.
 */
public final class HeaderEmitter {
    /**
     * Converts an {@link ElfHeader} into a textual report.
     *
     * @param header parsed ELF header
     * @return formatted multi-line summary
     */
    public String emit(ElfHeader header) {
        StringBuilder sb = new StringBuilder();
        sb.append("ELF header (lenient parse)").append(System.lineSeparator());
        sb.append("Class: ").append(header.fileClass()).append(" (").append(decodeClass(header.fileClass())).append(")").append(System.lineSeparator());
        sb.append("Data: ").append(header.dataEncoding()).append(" (").append(decodeData(header.dataEncoding())).append(")").append(System.lineSeparator());
        sb.append("Type: ").append(header.type()).append(System.lineSeparator());
        sb.append("Machine: ").append(header.machine()).append(System.lineSeparator());
        sb.append("Entry point: ").append(hex(header.entryPoint())).append(System.lineSeparator());
        sb.append("Section header offset: ").append(header.sectionHeaderOffset()).append(System.lineSeparator());
        sb.append("Section header entry size: ").append(header.sectionHeaderEntrySize()).append(System.lineSeparator());
        sb.append("Section header count: ").append(header.sectionHeaderCount()).append(System.lineSeparator());
        sb.append("Section name table index: ").append(header.sectionNameTableIndex()).append(System.lineSeparator());
        return sb.toString();
    }

    /**
     * Formats a value as an 8-digit hexadecimal string.
     *
     * @param value numeric value to format
     * @return hexadecimal string prefixed with {@code 0x}
     */
    private static String hex(long value) {
        return String.format("0x%08x", value);
    }

    /**
     * Decodes the ELF class field.
     *
     * @param fileClass raw ELF class value
     * @return symbolic class name
     */
    private static String decodeClass(int fileClass) {
        if (fileClass == 1) {
            return "ELF32";
        }
        if (fileClass == 2) {
            return "ELF64";
        }
        return "unknown";
    }

    /**
     * Decodes the ELF data-encoding field.
     *
     * @param dataEncoding raw data encoding value
     * @return symbolic endianness name
     */
    private static String decodeData(int dataEncoding) {
        if (dataEncoding == 1) {
            return "little-endian";
        }
        if (dataEncoding == 2) {
            return "big-endian";
        }
        return "unknown";
    }
}
