package org.hello.riscvdisassembler;

import org.hello.riscvdisassembler.core.binary.model.BinaryImage;
import org.hello.riscvdisassembler.adapters.input.elf.ElfBinaryImageAdapter;
import org.hello.riscvdisassembler.adapters.input.elf.ElfLoader;

import java.io.IOException;
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

    /**
     * Returns the sample ELF file that embeds data inside the text section.
     *
     * @return path to {@code samples/code_and_data_together}
     */
    public static Path codeAndDataTogetherElf() {
        return Path.of("samples", "code_and_data_together");
    }

    /**
     * Loads the sample ELF and adapts it into the canonical binary image model.
     *
     * @return canonical binary image for {@code samples/sample.elf}
     * @throws IOException if the sample cannot be read
     */
    public static BinaryImage sampleBinaryImage() throws IOException {
        return new ElfBinaryImageAdapter().adapt(new ElfLoader().load(sampleElf()));
    }

    /**
     * Loads the inline-data sample and adapts it into the canonical binary image model.
     *
     * @return canonical binary image for {@code samples/code_and_data_together}
     * @throws IOException if the sample cannot be read
     */
    public static BinaryImage codeAndDataTogetherBinaryImage() throws IOException {
        return new ElfBinaryImageAdapter().adapt(new ElfLoader().load(codeAndDataTogetherElf()));
    }
}

