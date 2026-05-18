package org.hello.riscvdisassembler.core.resolve;

import org.hello.riscvdisassembler.core.binary.model.BinaryImage;
import org.hello.riscvdisassembler.core.binary.model.BinarySection;
import org.hello.riscvdisassembler.core.binary.model.BinarySymbol;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResolvedProgramTest {

    private ResolvedProgram resolvedProgram;
    private BinarySection textSection;
    private BinarySymbol startSymbol;

    @BeforeEach
    void setUp() {
        textSection = new BinarySection(1, ".text", 0x1000, 0x100, 0x50, true);
        BinarySection dataSection = new BinarySection(2, ".data", 0x2000, 0x200, 0x30, false);
        startSymbol = new BinarySymbol("_start", 0x1000, 0, 0, 0, 1);
        
        BinaryImage image = new BinaryImage(0x1000, new byte[0], List.of(textSection, dataSection), List.of(startSymbol));
        
        NavigableMap<Long, BinarySymbol> symbolsByAddress = new TreeMap<>();
        symbolsByAddress.put(0x1000L, startSymbol);
        
        NavigableMap<Long, BinarySymbol> textSymbols = new TreeMap<>();
        textSymbols.put(0x1000L, startSymbol);
        Map<String, NavigableMap<Long, BinarySymbol>> symbolsBySectionName = Map.of(".text", textSymbols);
        
        resolvedProgram = new ResolvedProgram(image, List.of(textSection), symbolsByAddress, symbolsBySectionName);
    }

    @Test
    void testFindSymbol() {
        assertEquals(startSymbol, resolvedProgram.findSymbol(".text", 0x1000L));
        assertNull(resolvedProgram.findSymbol(".text", 0x1004L));
        assertNull(resolvedProgram.findSymbol(".data", 0x1000L));
    }

    @Test
    void testFindSectionContaining() {
        assertEquals(textSection, resolvedProgram.findSectionContaining(0x1000L));
        assertEquals(textSection, resolvedProgram.findSectionContaining(0x1020L));
        
        // Out of bounds or not in executable sections
        assertNull(resolvedProgram.findSectionContaining(0x0FFF));
        assertNull(resolvedProgram.findSectionContaining(0x1050));
        assertNull(resolvedProgram.findSectionContaining(0x2000L));
    }

    @Test
    void testContainsExecutableAddress() {
        assertTrue(resolvedProgram.containsExecutableAddress(".text", 0x1000L));
        assertTrue(resolvedProgram.containsExecutableAddress(".text", 0x104F));
        
        assertFalse(resolvedProgram.containsExecutableAddress(".text", 0x0FFF));
        assertFalse(resolvedProgram.containsExecutableAddress(".text", 0x1050));
        assertFalse(resolvedProgram.containsExecutableAddress(".data", 0x1000L));
    }
}
