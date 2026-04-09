package org.hello.riscvdisassembler.cli;

import org.hello.riscvdisassembler.TestPaths;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DisassemblerCliTest {
    @Test
    void runPrintsHelp() {
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        int exitCode = runWithCapturedStreams(new String[]{"--help"}, stdout, stderr);

        assertEquals(0, exitCode);
        assertTrue(stdout.toString(StandardCharsets.UTF_8).contains("Usage:"));
    }

    @Test
    void runReturnsUsageErrorWhenInputMissing() {
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        int exitCode = runWithCapturedStreams(new String[]{"--format", "asm"}, stdout, stderr);

        assertEquals(1, exitCode);
        assertTrue(stderr.toString(StandardCharsets.UTF_8).contains("Missing required argument --input"));
    }

    @Test
    void runSupportsHeaderOnlyMode() {
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        int exitCode = runWithCapturedStreams(new String[]{"--input", TestPaths.sampleElf().toString(), "--header-only"}, stdout, stderr);

        assertEquals(0, exitCode);
        assertTrue(stdout.toString(StandardCharsets.UTF_8).contains("ELF header (lenient parse)"));
    }

    @Test
    void runPrintsDebugStackTraceForFailures() {
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        int exitCode = runWithCapturedStreams(new String[]{"--input", ".\\missing.elf", "--format", "asm", "--debug"}, stdout, stderr);

        assertEquals(2, exitCode);
        String errorText = stderr.toString(StandardCharsets.UTF_8);
        assertTrue(errorText.contains("Input file not found"));
        assertTrue(errorText.contains("NoSuchFileException"));
    }

    private int runWithCapturedStreams(String[] args, ByteArrayOutputStream stdout, ByteArrayOutputStream stderr) {
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;
        try {
            System.setOut(new PrintStream(stdout, true, StandardCharsets.UTF_8));
            System.setErr(new PrintStream(stderr, true, StandardCharsets.UTF_8));
            return new DisassemblerCli().run(args);
        } finally {
            System.setOut(originalOut);
            System.setErr(originalErr);
        }
    }
}
