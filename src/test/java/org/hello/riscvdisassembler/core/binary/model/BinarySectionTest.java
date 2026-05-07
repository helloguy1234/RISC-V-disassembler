package org.hello.riscvdisassembler.core.binary.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class BinarySectionTest {

    @Test
    void testBinarySectionGetters() {
        BinarySection section = new BinarySection(1, ".text", 0x80000000L, 0x1000, 1024, true);

        assertEquals(1, section.index());
        assertEquals(".text", section.name());
        assertEquals(0x80000000L, section.address());
        assertEquals(0x1000, section.offset());
        assertEquals(1024, section.size());
        assertTrue(section.executable());
    }
    
    @Test
    void testBinarySectionNonExecutable() {
        BinarySection section = new BinarySection(2, ".data", 0x80000400L, 0x1400, 512, false);
        assertFalse(section.executable());
    }
}
