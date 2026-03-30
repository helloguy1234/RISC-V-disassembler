package org.hello.riscvdisassembler.pipeline;

import org.hello.riscvdisassembler.analysis.CfgBuilder;
import org.hello.riscvdisassembler.decoder.Rv32iDecoder;
import org.hello.riscvdisassembler.elf.ElfLoader;
import org.hello.riscvdisassembler.elf.model.ElfHeader;
import org.hello.riscvdisassembler.elf.model.ElfFile;
import org.hello.riscvdisassembler.emit.HeaderEmitter;
import org.hello.riscvdisassembler.emit.JsonEmitter;
import org.hello.riscvdisassembler.emit.TextEmitter;
import org.hello.riscvdisassembler.ir.InstructionIr;
import org.hello.riscvdisassembler.resolver.ResolvedProgram;
import org.hello.riscvdisassembler.resolver.SectionSymbolResolver;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Coordinates the full disassembly workflow from input ELF file to rendered output.
 *
 * <p>The pipeline loads the ELF file, resolves executable sections and symbols, decodes
 * machine words into instruction IR, and emits the final result in the requested format.</p>
 */
public final class DisassemblyPipeline {
    private final ElfLoader elfLoader = new ElfLoader();
    private final SectionSymbolResolver resolver = new SectionSymbolResolver();
    private final Rv32iDecoder decoder = new Rv32iDecoder();
    private final TextEmitter textEmitter = new TextEmitter();
    private final JsonEmitter jsonEmitter = new JsonEmitter();
    private final HeaderEmitter headerEmitter = new HeaderEmitter();
    private final CfgBuilder cfgBuilder = new CfgBuilder();

    /**
     * Executes the full disassembly pipeline for one input file.
     *
     * @param input path to the ELF file that should be processed
     * @param format output format selector: {@code asm}, {@code json}, or {@code cfg}
     * @return formatted disassembly output ready to print or write to a file
     * @throws IOException if the input file cannot be read
     * @throws IllegalArgumentException if {@code format} is not supported
     */
    public String execute(Path input, String format) throws IOException {
        ElfFile elfFile = elfLoader.load(input);
        ResolvedProgram resolvedProgram = resolver.resolve(elfFile);
        List<InstructionIr> instructions = decoder.decode(resolvedProgram);

        switch (format) {
            case "asm":
                return textEmitter.emit(resolvedProgram, instructions);
            case "json":
                return jsonEmitter.emit(resolvedProgram, instructions);
            case "cfg":
                return cfgBuilder.emit(resolvedProgram, instructions);
            default:
                throw new IllegalArgumentException("Unsupported format: " + format);
        }
    }

    /**
     * Parses and renders only the ELF header, without requiring the full file to pass
     * complete disassembly validation.
     *
     * @param input path to the file whose header should be parsed
     * @return formatted header summary
     * @throws IOException if the input file cannot be read
     */
    public String executeHeader(Path input) throws IOException {
        ElfHeader header = elfLoader.loadHeader(input);
        return headerEmitter.emit(header);
    }
}
