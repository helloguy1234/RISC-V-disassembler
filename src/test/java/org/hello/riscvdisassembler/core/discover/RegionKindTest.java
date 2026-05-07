package org.hello.riscvdisassembler.core.discover;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RegionKindTest {

    @Test
    void testEnumValues() {
        RegionKind[] values = RegionKind.values();
        assertEquals(4, values.length, "Should have exactly 4 region kinds");
        
        assertNotNull(RegionKind.valueOf("CODE"));
        assertNotNull(RegionKind.valueOf("DATA"));
        assertNotNull(RegionKind.valueOf("UNKNOWN"));
        assertNotNull(RegionKind.valueOf("ALIGNMENT"));
    }
}
