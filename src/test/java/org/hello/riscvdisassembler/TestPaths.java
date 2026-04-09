package org.hello.riscvdisassembler;

import java.nio.file.Path;

/**
 * Shared path helpers for tests.
 */
public final class TestPaths {
    private TestPaths() {
    }

    /**
     * Returns the sample ELF file shipped with the repository.
     *
     * @return path to {@code samples/sample.elf}
     */
    public static Path sampleElf() {
        return Path.of("samples", "sample.elf");
    }
}
