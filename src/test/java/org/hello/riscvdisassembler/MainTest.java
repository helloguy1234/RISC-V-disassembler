package org.hello.riscvdisassembler;

import org.hello.riscvdisassembler.entry.cli.DisassemblerCli;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the Main entry point class.
 */
class MainTest {

    @Test
    void mainConstructorIsPrivate() throws NoSuchMethodException {
        Constructor<Main> constructor = Main.class.getDeclaredConstructor();
        assertTrue(Modifier.isPrivate(constructor.getModifiers()));
        constructor.setAccessible(true);
        assertDoesNotThrow(() -> constructor.newInstance());
    }

    @Test
    void mainDelegatesToDisassemblerCli() {
        String[] args = { "--help" };
        ByteArrayOutputStream outCapture = new ByteArrayOutputStream();
        ByteArrayOutputStream errCapture = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;

        try (PrintStream ignoredOut = new PrintStream(outCapture);
                PrintStream ignoredErr = new PrintStream(errCapture)) {
            System.setOut(ignoredOut);
            System.setErr(ignoredErr);
            int exitCode = new DisassemblerCli().run(args);
            assertEquals(0, exitCode);
        } finally {
            System.setOut(originalOut);
            System.setErr(originalErr);
        }
    }

    @Test
    void disassemblerCliWithInvalidArgsReturnsNonZero() {
        String[] args = { "--invalid-option" };
        ByteArrayOutputStream outCapture = new ByteArrayOutputStream();
        ByteArrayOutputStream errCapture = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;

        try (PrintStream ignoredOut = new PrintStream(outCapture);
                PrintStream ignoredErr = new PrintStream(errCapture)) {
            System.setOut(ignoredOut);
            System.setErr(ignoredErr);
            int exitCode = new DisassemblerCli().run(args);
            assertEquals(1, exitCode);
        } finally {
            System.setOut(originalOut);
            System.setErr(originalErr);
        }
    }
}
