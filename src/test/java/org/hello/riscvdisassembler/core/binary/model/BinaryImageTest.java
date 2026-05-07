package org.hello.riscvdisassembler.core.binary.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BinaryImageTest {

    @Test
    void testBinaryImageCreationAndGetters() {
        BinarySection section = new BinarySection(1, ".text", 0x1000, 0x10, 8, true);
        BinarySymbol symbol = new BinarySymbol("main", 0x1000, 8, 0, 0, 1);
        byte[] bytes = new byte[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20};
        
        BinaryImage image = new BinaryImage(0x1000, bytes, List.of(section), List.of(symbol));
        
        assertEquals(0x1000, image.entryPoint());
        assertArrayEquals(bytes, image.bytes());
        assertEquals(1, image.sections().size());
        assertEquals(1, image.symbols().size());
        assertEquals(section, image.sections().getFirst());
        assertEquals(symbol, image.symbols().getFirst());
    }

    @Test
    void testSliceReturnsCorrectBytesForSection() {
        BinarySection section = new BinarySection(1, ".text", 0x1000, 2, 4, true);
        byte[] bytes = new byte[]{10, 20, 30, 40, 50, 60, 70, 80};
        
        BinaryImage image = new BinaryImage(0x1000, bytes, List.of(section), List.of());
        
        byte[] sliced = image.slice(section);
        assertArrayEquals(new byte[]{30, 40, 50, 60}, sliced);
    }
    
    @Test
    void testSliceThrowsExceptionForOutOfBounds() {
        BinarySection section = new BinarySection(1, ".text", 0x1000, 2, 10, true); // size 10 is out of bounds
        byte[] bytes = new byte[]{10, 20, 30, 40, 50, 60, 70, 80};
        
        BinaryImage image = new BinaryImage(0x1000, bytes, List.of(section), List.of());
        
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> image.slice(section));
    }
}
