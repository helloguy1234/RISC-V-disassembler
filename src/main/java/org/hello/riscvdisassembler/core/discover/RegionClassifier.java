package org.hello.riscvdisassembler.core.discover;

import org.hello.riscvdisassembler.core.binary.model.BinarySection;
import org.hello.riscvdisassembler.core.decode.model.InstructionIr;
import org.hello.riscvdisassembler.core.resolve.ResolvedProgram;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Classifies executable-section address ranges into discovered regions.
 */
public final class RegionClassifier {
    /**
     * Builds region metadata for one discovered program.
     *
     * @param program resolved program metadata
     * @param instructions retained instructions
     * @param mode discovery strategy that produced the instructions
     * @return ordered discovered regions
     */
    public List<DiscoveredRegion> classify(ResolvedProgram program, List<InstructionIr> instructions, DiscoveryMode mode) {
        List<DiscoveredRegion> regions = new ArrayList<>();
        for (BinarySection section : program.executableSections()) {
            List<InstructionIr> sectionInstructions = instructions.stream()
                    .filter(instruction -> section.name().equals(instruction.sectionName()))
                    .sorted(Comparator.comparingLong(InstructionIr::address))
                    .toList();

            if (mode == DiscoveryMode.LINEAR) {
                if (section.size() > 0) {
                    regions.add(new DiscoveredRegion(section.name(), section.address(), section.address() + section.size(),
                            RegionKind.CODE, "linear-sweep"));
                }
                continue;
            }

            if (sectionInstructions.isEmpty()) {
                if (section.size() > 0) {
                    regions.add(new DiscoveredRegion(section.name(), section.address(), section.address() + section.size(),
                            RegionKind.DATA, "unreachable-section"));
                }
                continue;
            }

            long sectionEnd = section.address() + section.size();
            long codeRunStart = sectionInstructions.getFirst().address();
            long previousAddress = codeRunStart;

            if (section.address() < codeRunStart) {
                regions.add(new DiscoveredRegion(section.name(), section.address(), codeRunStart,
                        RegionKind.DATA, "unreachable-gap"));
            }

            for (int i = 1; i < sectionInstructions.size(); i++) {
                long currentAddress = sectionInstructions.get(i).address();
                if (currentAddress != previousAddress + 4) {
                    regions.add(new DiscoveredRegion(section.name(), codeRunStart, previousAddress + 4,
                            RegionKind.CODE, "reachable-code"));
                    regions.add(new DiscoveredRegion(section.name(), previousAddress + 4, currentAddress,
                            RegionKind.DATA, "unreachable-gap"));
                    codeRunStart = currentAddress;
                }
                previousAddress = currentAddress;
            }

            regions.add(new DiscoveredRegion(section.name(), codeRunStart, previousAddress + 4,
                    RegionKind.CODE, "reachable-code"));
            if (previousAddress + 4 < sectionEnd) {
                regions.add(new DiscoveredRegion(section.name(), previousAddress + 4, sectionEnd,
                        RegionKind.DATA, "unreachable-gap"));
            }
        }

        regions.sort(Comparator.comparing(DiscoveredRegion::sectionName).thenComparingLong(DiscoveredRegion::start));
        return regions;
    }
}

