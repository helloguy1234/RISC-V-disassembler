package org.hello.riscvdisassembler.features.text;

import org.hello.riscvdisassembler.core.binary.model.BinarySection;
import org.hello.riscvdisassembler.core.discover.DiscoveredProgram;
import org.hello.riscvdisassembler.core.discover.DiscoveredRegion;
import org.hello.riscvdisassembler.core.discover.RegionKind;
import org.hello.riscvdisassembler.core.binary.model.BinarySymbol;
import org.hello.riscvdisassembler.core.decode.model.InstructionIr;

import java.util.List;

/**
 * Renders decoded instructions as human-readable assembly text.
 */
public final class TextEmitter {
    /**
     * Converts a discovered program into text assembly output.
     *
     * @param program discovered program used for entry point, symbol lookup, and instructions
     * @return multi-line textual disassembly
     */
    public String emit(DiscoveredProgram program) {
        StringBuilder sb = new StringBuilder();
        sb.append("; mode = ").append(program.mode().name().toLowerCase()).append(System.lineSeparator());
        sb.append("; entry = ").append(hex(program.resolvedProgram().binaryImage().entryPoint())).append(System.lineSeparator());
        sb.append(System.lineSeparator());

        String currentSection = null;
        for (DiscoveredRegion region : program.regions()) {
            if (!region.sectionName().equals(currentSection)) {
                currentSection = region.sectionName();
                sb.append(region.sectionName()).append(":").append(System.lineSeparator());
            }

            if (region.kind() == RegionKind.CODE) {
                renderCodeRegion(program, region, sb);
            } else {
                renderDataRegion(program, region, sb);
            }
        }

        return sb.toString();
    }

    private static void renderCodeRegion(DiscoveredProgram program, DiscoveredRegion region, StringBuilder sb) {
        for (InstructionIr instruction : program.instructions()) {
            if (!instruction.sectionName().equals(region.sectionName())
                    || instruction.address() < region.start()
                    || instruction.address() >= region.end()) {
                continue;
            }

            BinarySymbol symbol = program.resolvedProgram().findSymbol(instruction.sectionName(), instruction.address());
            if (symbol != null) {
                sb.append(symbol.name()).append(":").append(System.lineSeparator());
            }

            sb.append("  ")
                    .append(hex(instruction.address()))
                    .append("  ")
                    .append(hex(Integer.toUnsignedLong(instruction.rawInstruction())))
                    .append("  ")
                    .append(instruction.mnemonic());

            if (!instruction.operands().isEmpty()) {
                sb.append(" ").append(String.join(", ", instruction.operands()));
            }
            sb.append(System.lineSeparator());
        }
    }

    private static void renderDataRegion(DiscoveredProgram program, DiscoveredRegion region, StringBuilder sb) {
        sb.append("  ; ").append(region.kind().name().toLowerCase()).append(" region (").append(region.reason()).append(")")
                .append(System.lineSeparator());

        BinarySection section = program.resolvedProgram().findSectionContaining(region.start());
        if (section == null) {
            return;
        }

        long address = region.start();
        while (address + 3 < region.end()) {
            BinarySymbol symbol = program.resolvedProgram().findSymbol(region.sectionName(), address);
            if (symbol != null) {
                sb.append(symbol.name()).append(":").append(System.lineSeparator());
            }

            long word = readWord(program, section, address);
            sb.append("  ")
                    .append(hex(address))
                    .append("  ")
                    .append(hex(word))
                    .append("  .word ")
                    .append(hex(word))
                    .append(System.lineSeparator());
            address += 4;
        }

        if (address < region.end()) {
            BinarySymbol symbol = program.resolvedProgram().findSymbol(region.sectionName(), address);
            if (symbol != null) {
                sb.append(symbol.name()).append(":").append(System.lineSeparator());
            }
            sb.append("  ").append(hex(address)).append("  .byte ").append(renderBytes(program, section, address, region.end()))
                    .append(System.lineSeparator());
        }
    }

    private static long readWord(DiscoveredProgram program, BinarySection section, long address) {
        byte[] bytes = program.resolvedProgram().binaryImage().slice(section);
        int offset = Math.toIntExact(address - section.address());
        int word = Byte.toUnsignedInt(bytes[offset])
                | (Byte.toUnsignedInt(bytes[offset + 1]) << 8)
                | (Byte.toUnsignedInt(bytes[offset + 2]) << 16)
                | (Byte.toUnsignedInt(bytes[offset + 3]) << 24);
        return Integer.toUnsignedLong(word);
    }

    private static String renderBytes(DiscoveredProgram program, BinarySection section, long start, long end) {
        byte[] bytes = program.resolvedProgram().binaryImage().slice(section);
        int offset = Math.toIntExact(start - section.address());
        int count = Math.toIntExact(end - start);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(String.format("0x%02x", Byte.toUnsignedInt(bytes[offset + i])));
        }
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
}

