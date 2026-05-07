package org.hello.riscvdisassembler.adapters.input.elf;

import org.hello.riscvdisassembler.core.binary.model.BinaryImage;
import org.hello.riscvdisassembler.core.binary.model.BinarySection;
import org.hello.riscvdisassembler.core.binary.model.BinarySymbol;
import org.hello.riscvdisassembler.adapters.input.elf.model.ElfFile;
import org.hello.riscvdisassembler.adapters.input.elf.model.SectionHeader;
import org.hello.riscvdisassembler.adapters.input.elf.model.SymbolEntry;

import java.util.List;

/**
 * Adapts ELF-specific loader output into the canonical binary model used by core pipeline stages.
 */
public final class ElfBinaryImageAdapter {
    /**
     * Converts an ELF file model into the canonical binary image model.
     *
     * @param elfFile parsed ELF model
     * @return canonical binary image
     */
    public BinaryImage adapt(ElfFile elfFile) {
        List<BinarySection> sections = elfFile.sections().stream()
                .map(this::adaptSection)
                .toList();
        List<BinarySymbol> symbols = elfFile.symbols().stream()
                .map(this::adaptSymbol)
                .toList();
        return new BinaryImage(elfFile.header().entryPoint(), elfFile.bytes(), sections, symbols);
    }

    private BinarySection adaptSection(SectionHeader section) {
        return new BinarySection(
                section.index(),
                section.name(),
                section.address(),
                section.offset(),
                section.size(),
                section.isExecutable()
        );
    }

    private BinarySymbol adaptSymbol(SymbolEntry symbol) {
        return new BinarySymbol(
                symbol.name(),
                symbol.value(),
                symbol.size(),
                symbol.info(),
                symbol.other(),
                symbol.sectionIndex()
        );
    }
}

