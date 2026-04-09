package org.hello.riscvdisassembler.pipeline;

import org.hello.riscvdisassembler.TestPaths;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DisassemblyPipelineTest {
    private final DisassemblyPipeline pipeline = new DisassemblyPipeline();

    @Test
    void executeAsmProducesAssemblyOutput() throws IOException {
        String output = pipeline.execute(TestPaths.sampleElf(), "asm");

        assertTrue(output.contains(".text:"));
        assertTrue(output.contains("addi ra, zero, 5"));
    }

    @Test
    void executeRequestSupportsHeaderOnlyAndDisassembleAll() throws IOException {
        String header = pipeline.execute(new DisassemblyRequest(TestPaths.sampleElf(), "asm", null, false, true, false, false));
        String allSections = pipeline.execute(new DisassemblyRequest(TestPaths.sampleElf(), "asm", null, false, false, true, false));

        assertTrue(header.contains("ELF header (lenient parse)"));
        assertTrue(allSections.contains(".symtab:"));
    }
}
