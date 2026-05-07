package org.hello.riscvdisassembler.core.binary.model;

import java.util.Arrays;
import java.util.List;

/**
 * Canonical input model consumed by core pipeline stages independently of
 * source file format.
 */
public final class BinaryImage {
    private final long entryPoint;
    private final byte[] bytes;
    private final List<BinarySection> sections;
    private final List<BinarySymbol> symbols;

    /**
     * Creates a canonical binary image.
     *
     * @param entryPoint program entry point
     * @param bytes      raw file bytes
     * @param sections   canonical section list
     * @param symbols    canonical symbol list
     */
    public BinaryImage(long entryPoint, byte[] bytes, List<BinarySection> sections, List<BinarySymbol> symbols) {
        this.entryPoint = entryPoint;
        this.bytes = bytes;
        this.sections = List.copyOf(sections);
        this.symbols = List.copyOf(symbols);
    }

    public long entryPoint() {
        return entryPoint;
    }

    public byte[] bytes() {
        return bytes;
    }

    public List<BinarySection> sections() {
        return sections;
    }

    public List<BinarySymbol> symbols() {
        return symbols;
    }

    /**
     * Extracts raw bytes for one canonical section.
     *
     * @param section section whose bytes should be copied
     * @return section byte copy
     */
    public byte[] slice(BinarySection section) {
        int start = Math.toIntExact(section.offset());
        int end = Math.toIntExact(section.offset() + section.size());

        if (start < 0 || end > bytes.length || start > end) {
            throw new ArrayIndexOutOfBoundsException("Section bounds exceed binary image size");
        }

        return Arrays.copyOfRange(bytes, start, end);
    }
}
