package org.hello.riscvdisassembler.pipeline;

import java.nio.file.Path;

/**
 * Immutable request object describing one disassembly action.
 *
 * <p>This type is shared by the CLI and the JavaFX UI so both presentation layers can
 * invoke the same pipeline with the same set of options.</p>
 */
public final class DisassemblyRequest {
    private final Path input;
    private final String format;
    private final Path output;
    private final boolean debug;
    private final boolean headerOnly;
    private final boolean disassembleAll;
    private final boolean uiMode;

    /**
     * Creates a new disassembly request.
     *
     * @param input input file path, or {@code null} when a UI is opened without a preselected file
     * @param format output format such as {@code asm}, {@code json}, or {@code cfg}
     * @param output optional output file path
     * @param debug whether full stack traces should be printed on failure
     * @param headerOnly whether only the ELF header should be parsed
     * @param disassembleAll whether every section should be treated as executable
     * @param uiMode whether the request should launch the JavaFX user interface
     */
    public DisassemblyRequest(Path input, String format, Path output, boolean debug,
                              boolean headerOnly, boolean disassembleAll, boolean uiMode) {
        this.input = input;
        this.format = format;
        this.output = output;
        this.debug = debug;
        this.headerOnly = headerOnly;
        this.disassembleAll = disassembleAll;
        this.uiMode = uiMode;
    }

    /** @return input file path, or {@code null} when not yet chosen */
    public Path input() {
        return input;
    }

    /** @return requested output format */
    public String format() {
        return format;
    }

    /** @return optional output destination */
    public Path output() {
        return output;
    }

    /** @return whether debug stack traces should be printed */
    public boolean debug() {
        return debug;
    }

    /** @return whether only the ELF header should be parsed */
    public boolean headerOnly() {
        return headerOnly;
    }

    /** @return whether all sections should be decoded as if executable */
    public boolean disassembleAll() {
        return disassembleAll;
    }

    /** @return whether the JavaFX UI should be launched */
    public boolean uiMode() {
        return uiMode;
    }
}
