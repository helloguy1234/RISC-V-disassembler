package org.hello.riscvdisassembler.core.resolve;

import org.hello.riscvdisassembler.core.binary.model.BinaryImage;
import org.hello.riscvdisassembler.core.binary.model.BinarySection;
import org.hello.riscvdisassembler.core.binary.model.BinarySymbol;

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
    private final BinaryImage binaryImage;
    private final List<BinarySection> executableSections;
    private final NavigableMap<Long, BinarySymbol> symbolsByAddress;
    private final Map<String, NavigableMap<Long, BinarySymbol>> symbolsBySectionName;

    /**
     * Creates a resolved program view.
     *
     * @param binaryImage canonical binary image
     * @param executableSections executable sections sorted by address
     * @param symbolsByAddress global map from symbol address to symbol metadata
     * @param symbolsBySectionName per-section symbol maps used to avoid label collisions
     */
    public ResolvedProgram(BinaryImage binaryImage, List<BinarySection> executableSections,
                           NavigableMap<Long, BinarySymbol> symbolsByAddress,
                           Map<String, NavigableMap<Long, BinarySymbol>> symbolsBySectionName) {
        this.binaryImage = binaryImage;
        this.executableSections = executableSections;
        this.symbolsByAddress = symbolsByAddress;
        this.symbolsBySectionName = symbolsBySectionName;
    }

    /** @return canonical binary image */
    public BinaryImage binaryImage() {
        return binaryImage;
    }

    /** @return executable sections selected for disassembly */
    public List<BinarySection> executableSections() {
        return executableSections;
    }

    /** @return address-indexed symbol map for label lookup */
    public NavigableMap<Long, BinarySymbol> symbolsByAddress() {
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
    public BinarySymbol findSymbol(String sectionName, long address) {
        NavigableMap<Long, BinarySymbol> sectionSymbols = symbolsBySectionName.getOrDefault(sectionName, Collections.emptyNavigableMap());
        return sectionSymbols.get(address);
    }

    /**
     * Finds the executable section that contains the given address.
     *
     * @param address candidate code address
     * @return containing executable section, or {@code null} when none matches
     */
    public BinarySection findSectionContaining(long address) {
        for (BinarySection section : executableSections) {
            if (address >= section.address() && address < section.address() + section.size()) {
                return section;
            }
        }
        return null;
    }

    /**
     * Checks whether a section contains an executable address.
     *
     * @param sectionName section name to match
     * @param address candidate address
     * @return {@code true} when the address lies inside the named executable section
     */
    public boolean containsExecutableAddress(String sectionName, long address) {
        for (BinarySection section : executableSections) {
            if (section.name().equals(sectionName)
                    && address >= section.address()
                    && address < section.address() + section.size()) {
                return true;
            }
        }
        return false;
    }
}

