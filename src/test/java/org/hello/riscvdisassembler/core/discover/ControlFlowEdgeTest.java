package org.hello.riscvdisassembler.core.discover;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ControlFlowEdgeTest {

    @Test
    void testControlFlowEdgeCreation() {
        long from = 0x1000L;
        long to = 0x1004L;
        ControlFlowEdge edge = new ControlFlowEdge(from, to);

        assertEquals(from, edge.from(), "From address should match");
        assertEquals(to, edge.to(), "To address should match");
    }

    @Test
    void testEquality() {
        ControlFlowEdge edge1 = new ControlFlowEdge(0x1000L, 0x1004L);
        ControlFlowEdge edge2 = new ControlFlowEdge(0x1000L, 0x1004L);
        ControlFlowEdge edge3 = new ControlFlowEdge(0x1004L, 0x1008L);

        assertEquals(edge1, edge2, "Edges with same properties should be equal");
        assertNotEquals(edge1, edge3, "Edges with different properties should not be equal");
        assertEquals(edge1.hashCode(), edge2.hashCode(), "Hash codes should match for equal edges");
    }
}
