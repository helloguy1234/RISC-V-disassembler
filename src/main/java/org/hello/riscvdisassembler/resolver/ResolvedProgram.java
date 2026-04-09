package org.hello.riscvdisassembler.resolver;

import org.hello.riscvdisassembler.elf.model.ElfFile;
import org.hello.riscvdisassembler.elf.model.SectionHeader;
import org.hello.riscvdisassembler.elf.model.SymbolEntry;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;

/**
 * Prepared program view used by decoding and output stages.
 *
 * <p>This type narrows the full ELF model down to the information that later stages
 * commonly need: executable sections and address-indexed symbols.</p>
 */
public final class ResolvedProgram {
    private final ElfFile elfFile;
    private final List<SectionHeader> executableSections;
    private final NavigableMap<Long, SymbolEntry> symbolsByAddress;
    private final Map<String, NavigableMap<Long, SymbolEntry>> symbolsBySectionName;

    /**
     * Creates a resolved program view.
     *
     * @param elfFile original parsed ELF model
     * @param executableSections executable sections sorted by address
     * @param symbolsByAddress global map from symbol address to symbol metadata
     * @param symbolsBySectionName per-section symbol maps used to avoid label collisions
     */
    public ResolvedProgram(ElfFile elfFile, List<SectionHeader> executableSections,
                           NavigableMap<Long, SymbolEntry> symbolsByAddress,
                           Map<String, NavigableMap<Long, SymbolEntry>> symbolsBySectionName) {
        this.elfFile = elfFile;
        this.executableSections = executableSections;
        this.symbolsByAddress = symbolsByAddress;
        this.symbolsBySectionName = symbolsBySectionName;
    }

    /** @return original parsed ELF file */
    public ElfFile elfFile() {
        return elfFile;
    }

    /** @return executable sections selected for disassembly */
    public List<SectionHeader> executableSections() {
        return executableSections;
    }

    /** @return address-indexed symbol map for label lookup */
    public NavigableMap<Long, SymbolEntry> symbolsByAddress() {
        return symbolsByAddress;
    }

    /**
     * Looks up a symbol using both section name and address.
     *
     * <p>This avoids false label matches when multiple sections share the same virtual
     * address, which commonly happens when the {@code --disassemble-all} option is enabled.</p>
     *
     * @param sectionName section name of the instruction being rendered
     * @param address instruction address
     * @return symbol at that section/address pair, or {@code null} when none exists
     */
    public SymbolEntry findSymbol(String sectionName, long address) {
        NavigableMap<Long, SymbolEntry> sectionSymbols = symbolsBySectionName.getOrDefault(sectionName, Collections.emptyNavigableMap());
        return sectionSymbols.get(address);
    }
}
