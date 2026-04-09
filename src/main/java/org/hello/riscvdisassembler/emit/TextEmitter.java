package org.hello.riscvdisassembler.emit;

import org.hello.riscvdisassembler.elf.model.SymbolEntry;
import org.hello.riscvdisassembler.ir.InstructionIr;
import org.hello.riscvdisassembler.resolver.ResolvedProgram;

import java.util.List;

/**
 * Renders decoded instructions as human-readable assembly text.
 */
public final class TextEmitter {
    /**
     * Converts the resolved program and decoded instructions into text assembly output.
     *
     * @param program resolved program metadata used for entry point and symbol lookup
     * @param instructions decoded instructions in display order
     * @return multi-line textual disassembly
     */
    public String emit(ResolvedProgram program, List<InstructionIr> instructions) {
        StringBuilder sb = new StringBuilder();
        sb.append("; entry = ").append(hex(program.elfFile().header().entryPoint())).append(System.lineSeparator());
        sb.append(System.lineSeparator());

        String currentSection = null;
        for (InstructionIr instruction : instructions) {
            if (!instruction.sectionName().equals(currentSection)) {
                currentSection = instruction.sectionName();
                sb.append(instruction.sectionName()).append(":").append(System.lineSeparator());
            }

            SymbolEntry symbol = program.findSymbol(instruction.sectionName(), instruction.address());
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
