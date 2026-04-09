package org.hello.riscvdisassembler.elf;

/**
 * Signals that an input file is malformed or unsupported as an ELF file.
 */
public final class ElfParsingException extends RuntimeException {
    /**
     * Creates an exception with a human-readable parsing error message.
     *
     * @param message description of the parsing failure
     */
    public ElfParsingException(String message) {
        super(message);
    }
}
