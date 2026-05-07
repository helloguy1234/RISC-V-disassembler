package org.hello.riscvdisassembler.core.discover;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DiscoveredRegionTest {

    @Test
    void testDiscoveredRegionCreation() {
        String sectionName = ".text";
        long start = 0x1000L;
        long end = 0x2000L;
        RegionKind kind = RegionKind.CODE;
        String reason = "Linear sweep";

        DiscoveredRegion region = new DiscoveredRegion(sectionName, start, end, kind, reason);

        assertEquals(sectionName, region.sectionName(), "Section name should match");
        assertEquals(start, region.start(), "Start address should match");
        assertEquals(end, region.end(), "End address should match");
        assertEquals(kind, region.kind(), "Region kind should match");
        assertEquals(reason, region.reason(), "Reason should match");
    }

    @Test
    void testEquality() {
        DiscoveredRegion region1 = new DiscoveredRegion(".text", 0x1000L, 0x2000L, RegionKind.CODE, "Linear");
        DiscoveredRegion region2 = new DiscoveredRegion(".text", 0x1000L, 0x2000L, RegionKind.CODE, "Linear");
        DiscoveredRegion region3 = new DiscoveredRegion(".data", 0x2000L, 0x3000L, RegionKind.DATA, "Data section");

        assertEquals(region1, region2, "Regions with same properties should be equal");
        assertNotEquals(region1, region3, "Regions with different properties should not be equal");
        assertEquals(region1.hashCode(), region2.hashCode(), "Hash codes should match for equal regions");
    }
}
