package org.hello.riscvdisassembler.core.discover;

/**
 * One contiguous discovered region inside a section.
 *
 * @param sectionName owning section name
 * @param start start address, inclusive
 * @param end end address, exclusive
 * @param kind region classification
 * @param reason short explanation of the classification
 */
public record DiscoveredRegion(String sectionName, long start, long end, RegionKind kind, String reason) {
}

