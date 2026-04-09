package org.hello.riscvdisassembler;

import org.hello.riscvdisassembler.cli.DisassemblerCli;

/**
 * Application entry point for the RISC-V disassembler.
 *
 * <p>
 * This class delegates command-line parsing and execution to
 * {@link DisassemblerCli}
 * and only converts the returned status into a process exit code when needed.
 * </p>
 */
public final class Main {
    /**
     * Prevents instantiation of this utility entry-point class.
     */
    private Main() {
    }

    /**
     * Launches the disassembler from the command line.
     *
     * @param args raw command-line arguments passed by the JVM
     */
    public static void main(String[] args) {
        int exitCode = new DisassemblerCli().run(args);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }
}
