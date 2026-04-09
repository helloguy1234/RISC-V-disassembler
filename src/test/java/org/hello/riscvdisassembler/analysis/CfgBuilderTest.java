package org.hello.riscvdisassembler.analysis;

import org.hello.riscvdisassembler.TestPaths;
import org.hello.riscvdisassembler.decoder.Rv32iDecoder;
import org.hello.riscvdisassembler.elf.ElfLoader;
import org.hello.riscvdisassembler.ir.InstructionIr;
import org.hello.riscvdisassembler.resolver.ResolvedProgram;
import org.hello.riscvdisassembler.resolver.SectionSymbolResolver;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CfgBuilderTest {
    private final ElfLoader loader = new ElfLoader();
    private final SectionSymbolResolver resolver = new SectionSymbolResolver();
    private final Rv32iDecoder decoder = new Rv32iDecoder();
    private final CfgBuilder cfgBuilder = new CfgBuilder();

    @Test
    void buildCreatesTwoBasicBlocksForSampleLoop() throws IOException {
        ResolvedProgram program = resolver.resolve(loader.load(TestPaths.sampleElf()));
        List<InstructionIr> instructions = decoder.decode(program);

        List<BasicBlock> blocks = cfgBuilder.build(instructions);

        assertEquals(2, blocks.size());
        assertEquals(0L, blocks.get(0).startAddress());
        assertEquals(8L, blocks.get(0).endAddress());
        assertEquals(List.of(12L), blocks.get(0).successors());
        assertEquals(List.of(12L), blocks.get(1).successors());
    }
}
