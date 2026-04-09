package org.hello.riscvdisassembler.resolver;

import org.hello.riscvdisassembler.TestPaths;
import org.hello.riscvdisassembler.elf.ElfLoader;
import org.hello.riscvdisassembler.elf.model.ElfFile;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SectionSymbolResolverTest {
    private final ElfLoader loader = new ElfLoader();
    private final SectionSymbolResolver resolver = new SectionSymbolResolver();

    @Test
    void resolveKeepsOnlyExecutableSectionsByDefault() throws IOException {
        ElfFile elfFile = loader.load(TestPaths.sampleElf());

        ResolvedProgram program = resolver.resolve(elfFile);

        assertEquals(1, program.executableSections().size());
        assertEquals(".text", program.executableSections().getFirst().name());
        assertNotNull(program.findSymbol(".text", 0L));
        assertEquals("_start", program.findSymbol(".text", 0L).name());
    }

    @Test
    void resolveCanTreatAllSectionsAsExecutable() throws IOException {
        ElfFile elfFile = loader.load(TestPaths.sampleElf());

        ResolvedProgram program = resolver.resolve(elfFile, true);

        assertTrue(program.executableSections().size() > 1);
        assertTrue(program.executableSections().stream().anyMatch(section -> ".text".equals(section.name())));
        assertEquals("_start", program.findSymbol(".text", 0L).name());
        assertNull(program.findSymbol(".symtab", 0L));
    }
}
