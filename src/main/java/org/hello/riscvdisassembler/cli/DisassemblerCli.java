package org.hello.riscvdisassembler.cli;

import org.hello.riscvdisassembler.pipeline.DisassemblyRequest;
import org.hello.riscvdisassembler.pipeline.DisassemblyPipeline;
import org.hello.riscvdisassembler.ui.UiLauncher;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Command-line front end for the disassembler application.
 *
 * <p>This class parses user options, prints usage/help text, invokes the disassembly
 * pipeline, and routes the generated output to standard output or a destination file.</p>
 */
public final class DisassemblerCli {
    /**
     * Parses the provided arguments and executes the requested disassembly command.
     *
     * @param args command-line arguments such as {@code --input}, {@code --format},
     *             and {@code --output}
     * @return {@code 0} on success, {@code 1} for argument or usage errors, or {@code 2}
     * for runtime processing failures
     */
    public int run(String[] args) {
        CliOptions options;
        try {
            options = CliOptions.parse(args);
        } catch (IllegalArgumentException ex) {
            System.err.println(ex.getMessage());
            printUsage();
            return 1;
        }

        if (options.help()) {
            printUsage();
            return 0;
        }

        try {
            DisassemblyRequest request = options.toRequest();
            if (request.uiMode()) {
                new UiLauncher().launch(request);
                return 0;
            }

            String output = new DisassemblyPipeline().execute(request);
            if (request.output() == null) {
                System.out.println(output);
            } else {
                Files.write(request.output(), output.getBytes(StandardCharsets.UTF_8));
            }
            return 0;
        } catch (IOException | RuntimeException ex) {
            System.err.println("Disassembly failed: " + formatError(options.toRequest(), ex));
            if (options.debug()) {
                ex.printStackTrace(System.err);
            }
            return 2;
        }
    }

    /**
     * Prints a short usage guide describing supported command-line options.
     */
    private void printUsage() {
        System.out.println("Usage:");
        System.out.println("  java -jar target/riscv-disassembler.jar --input <file> --format <asm|json|cfg> [--output <file>] [--disassemble-all] [--debug]");
        System.out.println("  java -jar target/riscv-disassembler.jar --input <file> --header-only [--output <file>] [--debug]");
        System.out.println("  java -jar target/riscv-disassembler.jar --ui [--input <file>] [--format <asm|json|cfg>] [--disassemble-all] [--debug]");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  --input   Path to ELF file");
        System.out.println("  --format  Output format: asm, json, cfg");
        System.out.println("  --header-only  Parse and print only the ELF header (lenient mode)");
        System.out.println("  --disassemble-all  Treat every section as executable");
        System.out.println("  --output  Optional destination file");
        System.out.println("  --ui      Launch the JavaFX user interface");
        System.out.println("  --debug   Print full stack trace when an error occurs");
        System.out.println("  --help    Show this help");
    }

    /**
     * Formats a user-facing error message from a caught exception.
     *
     * @param request request that the user asked to run
     * @param ex caught exception
     * @return human-readable error message
     */
    private String formatError(DisassemblyRequest request, Exception ex) {
        if (request.input() != null && !java.nio.file.Files.exists(request.input())) {
            return "Input file not found: " + request.input();
        }
        if (ex.getMessage() == null || ex.getMessage().trim().isEmpty()) {
            return ex.getClass().getSimpleName();
        }
        return ex.getClass().getSimpleName() + ": " + ex.getMessage();
    }

    /**
     * Immutable holder for parsed CLI options.
     */
    private static final class CliOptions {
        private final Path input;
        private final String format;
        private final Path output;
        private final boolean help;
        private final boolean debug;
        private final boolean headerOnly;
        private final boolean disassembleAll;
        private final boolean ui;

        /**
         * Creates a new validated set of CLI options.
         *
         * @param input path to the input ELF file; may be {@code null} when only help is requested
         * @param format requested output format such as {@code asm}, {@code json}, or {@code cfg}
         * @param output optional output path; {@code null} means print to standard output
         * @param help whether the user requested help text
         * @param debug whether full stack traces should be printed on failure
         * @param headerOnly whether only the ELF header should be parsed and printed
         * @param disassembleAll whether every section should be treated as executable
         * @param ui whether the JavaFX UI should be launched
         */
        private CliOptions(java.nio.file.Path input, String format, java.nio.file.Path output, boolean help,
                           boolean debug, boolean headerOnly, boolean disassembleAll, boolean ui) {
            this.input = input;
            this.format = format;
            this.output = output;
            this.help = help;
            this.debug = debug;
            this.headerOnly = headerOnly;
            this.disassembleAll = disassembleAll;
            this.ui = ui;
        }

        /**
         * Indicates whether help text should be shown instead of running the pipeline.
         *
         * @return {@code true} if the user requested help
         */
        private boolean help() {
            return help;
        }

        /**
         * Indicates whether debug stack traces should be printed for failures.
         *
         * @return {@code true} when debug output is enabled
         */
        private boolean debug() {
            return debug;
        }

        /**
         * Converts CLI options into a request understood by the shared pipeline and UI layer.
         *
         * @return immutable disassembly request
         */
        private DisassemblyRequest toRequest() {
            return new DisassemblyRequest(input, format, output, debug, headerOnly, disassembleAll, ui);
        }

        /**
         * Parses raw command-line arguments into a validated {@link CliOptions} instance.
         *
         * @param args raw command-line arguments
         * @return parsed and validated options
         * @throws IllegalArgumentException if arguments are unknown, missing required values,
         *                                  or specify an unsupported format
         */
        private static CliOptions parse(String[] args) {
            Path input = null;
            Path output = null;
            String format = "asm";
            boolean help = false;
            boolean debug = false;
            boolean headerOnly = false;
            boolean disassembleAll = false;
            boolean ui = false;

            for (int i = 0; i < args.length; i++) {
                String arg = args[i];
                switch (arg) {
                    case "--input":
                        input = Paths.get(requireValue(args, ++i, "--input"));
                        break;
                    case "--format":
                        format = requireValue(args, ++i, "--format").toLowerCase();
                        break;
                    case "--output":
                        output = Paths.get(requireValue(args, ++i, "--output"));
                        break;
                    case "--header-only":
                        headerOnly = true;
                        break;
                    case "--disassemble-all":
                        disassembleAll = true;
                        break;
                    case "--ui":
                        ui = true;
                        break;
                    case "--debug":
                        debug = true;
                        break;
                    case "--help":
                    case "-h":
                        help = true;
                        break;
                    default:
                        throw new IllegalArgumentException("Unknown argument: " + arg);
                }
            }

            if (!help && !ui && input == null) {
                throw new IllegalArgumentException("Missing required argument --input");
            }
            if (!help && !headerOnly && !format.equals("asm") && !format.equals("json") && !format.equals("cfg")) {
                throw new IllegalArgumentException("Unsupported format: " + format);
            }

            return new CliOptions(input, format, output, help, debug, headerOnly, disassembleAll, ui);
        }

        /**
         * Reads the value associated with an option that requires an argument.
         *
         * @param args full command-line argument array
         * @param index index where the option value is expected
         * @param option option name used for error reporting
         * @return the option value at {@code index}
         * @throws IllegalArgumentException if the value is missing
         */
        private static String requireValue(String[] args, int index, String option) {
            if (index >= args.length) {
                throw new IllegalArgumentException("Missing value for " + option);
            }
            return args[index];
        }
    }
}
