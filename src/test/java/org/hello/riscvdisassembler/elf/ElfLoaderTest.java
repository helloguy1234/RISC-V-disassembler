package org.hello.riscvdisassembler.elf;

import org.hello.riscvdisassembler.TestPaths;
import org.hello.riscvdisassembler.elf.model.ElfFile;
import org.hello.riscvdisassembler.elf.model.ElfHeader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ElfLoaderTest {
    private final ElfLoader loader = new ElfLoader();

    @Test
    void loadParsesSampleElf() throws IOException {
        ElfFile elfFile = loader.load(TestPaths.sampleElf());

        assertEquals(0L, elfFile.header().entryPoint());
        assertEquals(6, elfFile.sections().size());
        assertTrue(elfFile.symbols().size() >= 4);
    }

    @Test
    void loadHeaderParsesHeaderInLenientMode() throws IOException {
        ElfHeader header = loader.loadHeader(TestPaths.sampleElf());

        assertEquals(1, header.fileClass());
        assertEquals(1, header.dataEncoding());
        assertEquals(243, header.machine());
        assertEquals(0L, header.entryPoint());
        assertEquals(6, header.sectionHeaderCount());
    }

    @Test
    void loadRejectsInvalidMagic(@TempDir Path tempDir) throws IOException {
        Path invalidFile = tempDir.resolve("invalid.elf");
        Files.write(invalidFile, new byte[64]);

        ElfParsingException ex = assertThrows(ElfParsingException.class, () -> loader.load(invalidFile));
        assertEquals("Invalid ELF magic", ex.getMessage());
    }

    @Test
    void loadHeaderStillParsesInvalidFile(@TempDir Path tempDir) throws IOException {
        Path invalidFile = tempDir.resolve("invalid.elf");
        Files.write(invalidFile, new byte[64]);

        ElfHeader header = loader.loadHeader(invalidFile);
        assertEquals(0, header.fileClass());
        assertEquals(0, header.machine());
        assertEquals(0, header.sectionHeaderCount());
    }
}
