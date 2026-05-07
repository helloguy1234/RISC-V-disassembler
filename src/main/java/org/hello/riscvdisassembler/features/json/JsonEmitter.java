package org.hello.riscvdisassembler.features.json;

import org.hello.riscvdisassembler.core.discover.ControlFlowEdge;
import org.hello.riscvdisassembler.core.discover.DiscoveredProgram;
import org.hello.riscvdisassembler.core.discover.DiscoveredRegion;
import org.hello.riscvdisassembler.core.binary.model.BinarySymbol;
import org.hello.riscvdisassembler.core.decode.model.InstructionIr;

import java.util.Map;

/**
 * Renders the disassembly result as JSON.
 */
public final class JsonEmitter {
    /**
     * Converts a discovered program into JSON text.
     *
     * @param program discovered program metadata and decoded instructions to serialize
     * @return JSON document as a string
     */
    public String emit(DiscoveredProgram program) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"discoveryMode\": ").append(quote(program.mode().name())).append(",\n");
        sb.append("  \"entryPoint\": ").append(quote(hex(program.resolvedProgram().binaryImage().entryPoint()))).append(",\n");
        sb.append("  \"sections\": [\n");

        for (int i = 0; i < program.resolvedProgram().executableSections().size(); i++) {
            org.hello.riscvdisassembler.core.binary.model.BinarySection section = program.resolvedProgram()
                    .executableSections().get(i);
            sb.append("    {\n");
            sb.append("      \"name\": ").append(quote(section.name())).append(",\n");
            sb.append("      \"address\": ").append(quote(hex(section.address()))).append(",\n");
            sb.append("      \"size\": ").append(section.size()).append("\n");
            sb.append("    }");
            sb.append(i == program.resolvedProgram().executableSections().size() - 1 ? "\n" : ",\n");
        }

        sb.append("  ],\n");
        sb.append("  \"symbols\": [\n");
        int symbolIndex = 0;
        for (Map.Entry<Long, BinarySymbol> entry : program.resolvedProgram().symbolsByAddress().entrySet()) {
            BinarySymbol symbol = entry.getValue();
            sb.append("    {\n");
            sb.append("      \"name\": ").append(quote(symbol.name())).append(",\n");
            sb.append("      \"address\": ").append(quote(hex(symbol.value()))).append(",\n");
            sb.append("      \"size\": ").append(symbol.size()).append("\n");
            sb.append("    }");
            sb.append(symbolIndex++ == program.resolvedProgram().symbolsByAddress().size() - 1 ? "\n" : ",\n");
        }
        sb.append("  ],\n");
        sb.append("  \"regions\": [\n");
        for (int i = 0; i < program.regions().size(); i++) {
            DiscoveredRegion region = program.regions().get(i);
            sb.append("    {\n");
            sb.append("      \"section\": ").append(quote(region.sectionName())).append(",\n");
            sb.append("      \"start\": ").append(quote(hex(region.start()))).append(",\n");
            sb.append("      \"end\": ").append(quote(hex(region.end()))).append(",\n");
            sb.append("      \"kind\": ").append(quote(region.kind().name())).append(",\n");
            sb.append("      \"reason\": ").append(quote(region.reason())).append("\n");
            sb.append("    }");
            sb.append(i == program.regions().size() - 1 ? "\n" : ",\n");
        }
        sb.append("  ],\n");
        sb.append("  \"edges\": [\n");
        for (int i = 0; i < program.edges().size(); i++) {
            ControlFlowEdge edge = program.edges().get(i);
            sb.append("    {\n");
            sb.append("      \"from\": ").append(quote(hex(edge.from()))).append(",\n");
            sb.append("      \"to\": ").append(quote(hex(edge.to()))).append("\n");
            sb.append("    }");
            sb.append(i == program.edges().size() - 1 ? "\n" : ",\n");
        }
        sb.append("  ],\n");
        sb.append("  \"instructions\": [\n");

        for (int i = 0; i < program.instructions().size(); i++) {
            InstructionIr instruction = program.instructions().get(i);
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
            sb.append(i == program.instructions().size() - 1 ? "\n" : ",\n");
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

