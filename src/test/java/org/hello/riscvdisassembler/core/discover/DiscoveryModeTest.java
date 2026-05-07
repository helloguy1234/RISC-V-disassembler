package org.hello.riscvdisassembler.core.discover;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DiscoveryModeTest {

    @Test
    void testEnumValues() {
        DiscoveryMode[] values = DiscoveryMode.values();
        assertEquals(2, values.length, "Should have exactly 2 discovery modes");
        
        assertNotNull(DiscoveryMode.valueOf("LINEAR"));
        assertNotNull(DiscoveryMode.valueOf("RECURSIVE"));
    }
}
