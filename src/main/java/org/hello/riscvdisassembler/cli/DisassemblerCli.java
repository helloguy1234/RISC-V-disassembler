package org.hello.riscvdisassembler.cli;

import org.hello.riscvdisassembler.pipeline.DisassemblyPipeline;

import java.io.IOException;
import java.nio.file.NoSuchFileException;
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
            String output;
            if (options.headerOnly()) {
                output = new DisassemblyPipeline().executeHeader(options.input());
            } else {
                output = new DisassemblyPipeline().execute(options.input(), options.format());
            }
            if (options.output() == null) {
                System.out.println(output);
            } else {
                Files.write(options.output(), output.getBytes(StandardCharsets.UTF_8));
            }
            return 0;
        } catch (IOException | RuntimeException ex) {
            System.err.println("Disassembly failed: " + formatError(options.input(), ex));
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
        System.out.println("  java -jar target/riscv-disassembler.jar --input <file> --format <asm|json|cfg> [--output <file>] [--debug]");
        System.out.println("  java -jar target/riscv-disassembler.jar --input <file> --header-only [--output <file>] [--debug]");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  --input   Path to ELF file");
        System.out.println("  --format  Output format: asm, json, cfg");
        System.out.println("  --header-only  Parse and print only the ELF header (lenient mode)");
        System.out.println("  --output  Optional destination file");
        System.out.println("  --debug   Print full stack trace when an error occurs");
        System.out.println("  --help    Show this help");
    }

    /**
     * Formats a user-facing error message from a caught exception.
     *
     * @param input input file path that the user requested
     * @param ex caught exception
     * @return human-readable error message
     */
    private String formatError(Path input, Exception ex) {
        if (ex instanceof NoSuchFileException) {
            return "Input file not found: " + input;
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

        /**
         * Creates a new validated set of CLI options.
         *
         * @param input path to the input ELF file; may be {@code null} when only help is requested
         * @param format requested output format such as {@code asm}, {@code json}, or {@code cfg}
         * @param output optional output path; {@code null} means print to standard output
         * @param help whether the user requested help text
         * @param debug whether full stack traces should be printed on failure
         * @param headerOnly whether only the ELF header should be parsed and printed
         */
        private CliOptions(Path input, String format, Path output, boolean help, boolean debug, boolean headerOnly) {
            this.input = input;
            this.format = format;
            this.output = output;
            this.help = help;
            this.debug = debug;
            this.headerOnly = headerOnly;
        }

        /**
         * Returns the input ELF path.
         *
         * @return input file path, or {@code null} when help mode is active
         */
        private Path input() {
            return input;
        }

        /**
         * Returns the requested output format.
         *
         * @return normalized format name
         */
        private String format() {
            return format;
        }

        /**
         * Returns the optional output destination.
         *
         * @return output file path, or {@code null} when writing to standard output
         */
        private Path output() {
            return output;
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
         * Indicates whether the CLI should parse only the ELF header.
         *
         * @return {@code true} when header-only mode is enabled
         */
        private boolean headerOnly() {
            return headerOnly;
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

            if (!help && input == null) {
                throw new IllegalArgumentException("Missing required argument --input");
            }
            if (!help && !headerOnly && !format.equals("asm") && !format.equals("json") && !format.equals("cfg")) {
                throw new IllegalArgumentException("Unsupported format: " + format);
            }

            return new CliOptions(input, format, output, help, debug, headerOnly);
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
