package org.hello.riscvdisassembler.core.discover.indirect;

/**
 * Exception thrown when there is an error accessing memory during indirect branch resolution,
 * such as when a target address falls outside of any loaded binary section.
 */
public class MemoryAccessException extends RuntimeException {
    public MemoryAccessException(String message) {
        super(message);
    }
}
