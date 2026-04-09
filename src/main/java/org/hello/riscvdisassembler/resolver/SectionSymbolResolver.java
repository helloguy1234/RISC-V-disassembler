package org.hello.riscvdisassembler.resolver;

import org.hello.riscvdisassembler.elf.model.ElfFile;
import org.hello.riscvdisassembler.elf.model.SectionHeader;
import org.hello.riscvdisassembler.elf.model.SymbolEntry;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Resolves which parts of the ELF file should be treated as executable program
 * code.
 *
 * <p>
 * The resolver filters executable sections and builds a fast lookup map from
 * code
 * addresses to named symbols.
 * </p>
 */
public final class SectionSymbolResolver {
    /**
     * Builds a simplified program view from the raw ELF model.
     *
     * @param elfFile parsed ELF file
     * @return resolved program containing executable sections and usable code
     *         symbols
     */
    public ResolvedProgram resolve(ElfFile elfFile) {
        return resolve(elfFile, false);
    }

    /**
     * Builds a simplified program view from the raw ELF model, ready to be disassemble
     *
     * @param elfFile        parsed ELF file
     * @param disassembleAll whether all sections should be treated as executable
     * @return resolved program containing executable sections and usable code
     *         symbols
     */
    public ResolvedProgram resolve(ElfFile elfFile, boolean disassembleAll) {
        // Select the sections that later stages should treat as code. In normal mode this
        // means only SHF_EXECINSTR sections; in --disassemble-all mode every section is kept.
        List<SectionHeader> executableSections = elfFile.sections().stream()
                .filter(section -> disassembleAll || section.isExecutable())
                .sorted(Comparator.comparingLong(SectionHeader::address))
                .collect(Collectors.toList());

        TreeMap<Long, SymbolEntry> symbolsByAddress = new TreeMap<>();
        Map<String, NavigableMap<Long, SymbolEntry>> symbolsBySectionName = new HashMap<>();
        for (SymbolEntry symbol : elfFile.symbols()) {
            if (symbol.name() == null || symbol.name().trim().isEmpty()) {
                continue;
            }
            if (symbol.sectionIndex() <= 0 || symbol.sectionIndex() >= elfFile.sections().size()) {
                continue;
            }
            SectionHeader ownerSection = elfFile.sections().get(symbol.sectionIndex());
            // Keep symbol lookup in sync with the selected sections so emitters do not attach
            // labels from non-decoded sections unless --disassemble-all was requested.
            if (!disassembleAll && !ownerSection.isExecutable()) {
                continue;
            }
            symbolsByAddress.put(symbol.value(), symbol);
            symbolsBySectionName
                    .computeIfAbsent(ownerSection.name(), key -> new TreeMap<>())
                    .put(symbol.value(), symbol);
        }

        return new ResolvedProgram(elfFile, executableSections, symbolsByAddress, symbolsBySectionName);
    }
}
