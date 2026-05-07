package org.hello.riscvdisassembler.core.binary.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class BinarySymbolTest {

    @Test
    void testBinarySymbolGetters() {
        BinarySymbol symbol = new BinarySymbol("_start", 0x80000000L, 16, 2, 0, 1);

        assertEquals("_start", symbol.name());
        assertEquals(0x80000000L, symbol.value());
        assertEquals(16, symbol.size());
        assertEquals(2, symbol.info());
        assertEquals(0, symbol.other());
        assertEquals(1, symbol.sectionIndex());
    }
}
