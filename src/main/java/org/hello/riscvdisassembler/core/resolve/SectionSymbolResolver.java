package org.hello.riscvdisassembler.core.resolve;

import org.hello.riscvdisassembler.core.binary.model.BinaryImage;
import org.hello.riscvdisassembler.core.binary.model.BinarySection;
import org.hello.riscvdisassembler.core.binary.model.BinarySymbol;

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
     * @param binaryImage canonical binary image
     * @return resolved program containing executable sections and usable code
     *         symbols
     */
    public ResolvedProgram resolve(BinaryImage binaryImage) {
        return resolve(binaryImage, false);
    }

    /**
     * Builds a simplified program view from the raw ELF model, ready to be disassemble
     *
     * @param binaryImage        canonical binary image
     * @param disassembleAll whether all sections should be treated as executable
     * @return resolved program containing executable sections and usable code
     *         symbols
     */
    public ResolvedProgram resolve(BinaryImage binaryImage, boolean disassembleAll) {
        // Select the sections that later stages should treat as code. In normal mode this
        // means only SHF_EXECINSTR sections; in --disassemble-all mode every section is kept.
        List<BinarySection> executableSections = binaryImage.sections().stream()
                .filter(section -> disassembleAll || section.executable())
                .sorted(Comparator.comparingLong(BinarySection::address))
                .collect(Collectors.toList());

        TreeMap<Long, BinarySymbol> symbolsByAddress = new TreeMap<>();
        Map<String, NavigableMap<Long, BinarySymbol>> symbolsBySectionName = new HashMap<>();
        for (BinarySymbol symbol : binaryImage.symbols()) {
            if (symbol.name() == null || symbol.name().trim().isEmpty()) {
                continue;
            }
            if (symbol.sectionIndex() <= 0 || symbol.sectionIndex() >= binaryImage.sections().size()) {
                continue;
            }
            BinarySection ownerSection = binaryImage.sections().get(symbol.sectionIndex());
            // Keep symbol lookup in sync with the selected sections so emitters do not attach
            // labels from non-decoded sections unless --disassemble-all was requested.
            if (!disassembleAll && !ownerSection.executable()) {
                continue;
            }
            symbolsByAddress.put(symbol.value(), symbol);
            symbolsBySectionName
                    .computeIfAbsent(ownerSection.name(), key -> new TreeMap<>())
                    .put(symbol.value(), symbol);
        }

        return new ResolvedProgram(binaryImage, executableSections, symbolsByAddress, symbolsBySectionName);
    }
}

