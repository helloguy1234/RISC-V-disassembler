package org.hello.riscvdisassembler.features.json;

import org.hello.riscvdisassembler.core.binary.model.BinaryImage;
import org.hello.riscvdisassembler.core.binary.model.BinarySection;
import org.hello.riscvdisassembler.core.binary.model.BinarySymbol;
import org.hello.riscvdisassembler.core.decode.model.InstructionIr;
import org.hello.riscvdisassembler.core.discover.ControlFlowEdge;
import org.hello.riscvdisassembler.core.discover.DiscoveredProgram;
import org.hello.riscvdisassembler.core.discover.DiscoveredRegion;
import org.hello.riscvdisassembler.core.discover.DiscoveryMode;
import org.hello.riscvdisassembler.core.discover.RegionKind;
import org.hello.riscvdisassembler.core.resolve.ResolvedProgram;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class JsonEmitterTest {

    private JsonEmitter jsonEmitter;

    @BeforeEach
    void setUp() {
        jsonEmitter = new JsonEmitter();
    }

    @Test
    void testEmitCompleteProgram() {
        // Prepare dummy data
        BinarySection section = new BinarySection(1, ".text", 0x1000L, 0L, 16L, true);
        BinaryImage image = new BinaryImage(0x1000L, new byte[16], List.of(section), List.of());
        
        // Symbols
        BinarySymbol symbol = new BinarySymbol("main", 0x1000L, 4L, 0, 0, 1);
        NavigableMap<Long, BinarySymbol> symbolsByAddress = new TreeMap<>();
        symbolsByAddress.put(0x1000L, symbol);
        
        ResolvedProgram resolvedProgram = new ResolvedProgram(
                image, 
                List.of(section), 
                symbolsByAddress, 
                Map.of(".text", symbolsByAddress)
        );
        
        DiscoveredRegion region = new DiscoveredRegion(".text", 0x1000, 0x1004, RegionKind.CODE, "Entry point");
        ControlFlowEdge edge = new ControlFlowEdge(0x1000, 0x1004);
        
        InstructionIr instruction = new InstructionIr(
                0x1000L, 0x00000013, "nop", List.of(), "I",
                InstructionIr.ControlFlowType.NORMAL, null, ".text"
        );

        DiscoveredProgram program = new DiscoveredProgram(
                resolvedProgram, List.of(instruction), List.of(edge),
                List.of(region), DiscoveryMode.LINEAR
        );

        String json = jsonEmitter.emit(program);

        assertNotNull(json);
        assertTrue(json.contains("\"discoveryMode\": \"LINEAR\""));
        assertTrue(json.contains("\"entryPoint\": \"0x00001000\""));
        assertTrue(json.contains("\"name\": \".text\""));
        assertTrue(json.contains("\"address\": \"0x00001000\""));
        assertTrue(json.contains("\"name\": \"main\""));
        assertTrue(json.contains("\"kind\": \"CODE\""));
        assertTrue(json.contains("\"from\": \"0x00001000\""));
        assertTrue(json.contains("\"to\": \"0x00001004\""));
        assertTrue(json.contains("\"mnemonic\": \"nop\""));
        assertTrue(json.contains("\"branchTarget\": null"));
    }
    
    @Test
    void testEmitEmptyProgram() {
        BinaryImage image = new BinaryImage(0x0L, new byte[0], List.of(), List.of());
        ResolvedProgram resolvedProgram = new ResolvedProgram(image, List.of(), new TreeMap<>(), Map.of());
        DiscoveredProgram program = new DiscoveredProgram(
                resolvedProgram, List.of(), List.of(),
                List.of(), DiscoveryMode.LINEAR
        );

        String json = jsonEmitter.emit(program);

        assertNotNull(json);
        assertTrue(json.contains("\"discoveryMode\": \"LINEAR\""));
        assertTrue(json.contains("\"sections\": [\n  ]"));
        assertTrue(json.contains("\"symbols\": [\n  ]"));
        assertTrue(json.contains("\"regions\": [\n  ]"));
        assertTrue(json.contains("\"edges\": [\n  ]"));
        assertTrue(json.contains("\"instructions\": [\n  ]"));
    }
}
