package org.hello.riscvdisassembler.emit;

import org.hello.riscvdisassembler.elf.model.SymbolEntry;
import org.hello.riscvdisassembler.ir.InstructionIr;
import org.hello.riscvdisassembler.resolver.ResolvedProgram;

import java.util.List;
import java.util.Map;

/**
 * Renders the disassembly result as JSON.
 */
public final class JsonEmitter {
    /**
     * Converts the resolved program and decoded instructions into JSON text.
     *
     * @param program resolved program metadata used for entry point, sections, and symbols
     * @param instructions decoded instructions to serialize
     * @return JSON document as a string
     */
    public String emit(ResolvedProgram program, List<InstructionIr> instructions) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"entryPoint\": ").append(quote(hex(program.elfFile().header().entryPoint()))).append(",\n");
        sb.append("  \"sections\": [\n");

        for (int i = 0; i < program.executableSections().size(); i++) {
            org.hello.riscvdisassembler.elf.model.SectionHeader section = program.executableSections().get(i);
            sb.append("    {\n");
            sb.append("      \"name\": ").append(quote(section.name())).append(",\n");
            sb.append("      \"address\": ").append(quote(hex(section.address()))).append(",\n");
            sb.append("      \"size\": ").append(section.size()).append("\n");
            sb.append("    }");
            sb.append(i == program.executableSections().size() - 1 ? "\n" : ",\n");
        }

        sb.append("  ],\n");
        sb.append("  \"symbols\": [\n");
        int symbolIndex = 0;
        for (Map.Entry<Long, SymbolEntry> entry : program.symbolsByAddress().entrySet()) {
            SymbolEntry symbol = entry.getValue();
            sb.append("    {\n");
            sb.append("      \"name\": ").append(quote(symbol.name())).append(",\n");
            sb.append("      \"address\": ").append(quote(hex(symbol.value()))).append(",\n");
            sb.append("      \"size\": ").append(symbol.size()).append("\n");
            sb.append("    }");
            sb.append(symbolIndex++ == program.symbolsByAddress().size() - 1 ? "\n" : ",\n");
        }
        sb.append("  ],\n");
        sb.append("  \"instructions\": [\n");

        for (int i = 0; i < instructions.size(); i++) {
            InstructionIr instruction = instructions.get(i);
            sb.append("    {\n");
            sb.append("      \"address\": ").append(quote(hex(instruction.address()))).append(",\n");
            sb.append("      \"raw\": ").append(quote(hex(Integer.toUnsignedLong(instruction.rawInstruction())))).append(",\n");
            sb.append("      \"section\": ").append(quote(instruction.sectionName())).append(",\n");
            sb.append("      \"mnemonic\": ").append(quote(instruction.mnemonic())).append(",\n");
            sb.append("      \"format\": ").append(quote(instruction.format())).append(",\n");
            sb.append("      \"controlFlowType\": ").append(quote(instruction.controlFlowType().name())).append(",\n");
            sb.append("      \"operands\": [");
            for (int j = 0; j < instruction.operands().size(); j++) {
                sb.append(quote(instruction.operands().get(j)));
                if (j < instruction.operands().size() - 1) {
                    sb.append(", ");
                }
            }
            sb.append("],\n");
            sb.append("      \"branchTarget\": ")
                    .append(instruction.branchTarget() == null ? "null" : quote(hex(instruction.branchTarget())))
                    .append("\n");
            sb.append("    }");
            sb.append(i == instructions.size() - 1 ? "\n" : ",\n");
        }

        sb.append("  ]\n");
        sb.append("}\n");
        return sb.toString();
    }

    /**
     * Quotes and escapes a string for JSON output.
     *
     * @param value raw string value, or {@code null}
     * @return JSON string literal, or {@code null} when the input is {@code null}
     */
    private static String quote(String value) {
        if (value == null) {
            return "null";
        }
        return "\"" + escapeJson(value) + "\"";
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
     * Escapes characters that are special in JSON string literals.
     *
     * @param value raw string
     * @return escaped string content without surrounding quotes
     */
    private static String escapeJson(String value) {
        StringBuilder escaped = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '\\':
                    escaped.append("\\\\");
                    break;
                case '"':
                    escaped.append("\\\"");
                    break;
                case '\b':
                    escaped.append("\\b");
                    break;
                case '\f':
                    escaped.append("\\f");
                    break;
                case '\n':
                    escaped.append("\\n");
                    break;
                case '\r':
                    escaped.append("\\r");
                    break;
                case '\t':
                    escaped.append("\\t");
                    break;
                default:
                    if (ch < 0x20) {
                        escaped.append(String.format("\\u%04x", (int) ch));
                    } else {
                        escaped.append(ch);
                    }
                    break;
            }
        }
        return escaped.toString();
    }
}
