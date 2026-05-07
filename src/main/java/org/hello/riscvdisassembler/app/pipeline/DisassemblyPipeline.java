package org.hello.riscvdisassembler.app.pipeline;

import org.hello.riscvdisassembler.features.cfg.CfgBuilder;
import org.hello.riscvdisassembler.adapters.input.elf.ElfBinaryImageAdapter;
import org.hello.riscvdisassembler.core.decode.Rv32iDecoder;
import org.hello.riscvdisassembler.core.discover.CodeDiscoveryEngine;
import org.hello.riscvdisassembler.core.discover.DiscoveredProgram;
import org.hello.riscvdisassembler.core.discover.DiscoveryMode;
import org.hello.riscvdisassembler.adapters.input.elf.ElfLoader;
import org.hello.riscvdisassembler.adapters.input.elf.model.ElfHeader;
import org.hello.riscvdisassembler.adapters.input.elf.model.ElfFile;
import org.hello.riscvdisassembler.features.header.HeaderEmitter;
import org.hello.riscvdisassembler.features.json.JsonEmitter;
import org.hello.riscvdisassembler.features.text.TextEmitter;
import org.hello.riscvdisassembler.core.resolve.ResolvedProgram;
import org.hello.riscvdisassembler.core.resolve.SectionSymbolResolver;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Coordinates the full disassembly workflow from input ELF file to rendered
 * output.
 *
 * <p>
 * The pipeline loads the ELF file, resolves executable sections and symbols,
 * decodes
 * machine words into instruction IR, and emits the final result in the
 * requested format.
 * </p>
 */
public final class DisassemblyPipeline {
    private final ElfLoader elfLoader = new ElfLoader();
    private final ElfBinaryImageAdapter binaryImageAdapter = new ElfBinaryImageAdapter();
    private final SectionSymbolResolver resolver = new SectionSymbolResolver();
    private final CodeDiscoveryEngine discoveryEngine = new CodeDiscoveryEngine(new Rv32iDecoder());
    private final TextEmitter textEmitter = new TextEmitter();
    private final JsonEmitter jsonEmitter = new JsonEmitter();
    private final HeaderEmitter headerEmitter = new HeaderEmitter();
    private final CfgBuilder cfgBuilder = new CfgBuilder();

    /**
     * Executes the full disassembly workflow described by a request object.
     *
     * @param request request describing input, output format, and execution flags
     * @return formatted disassembly output ready to print or write to a file
     * @throws IOException              if the input file cannot be read
     * @throws IllegalArgumentException if the requested format is not supported
     */
    public String execute(DisassemblyRequest request) throws IOException {
        if (request.headerOnly()) {
            return executeHeader(request.input());
        }

        ElfFile elfFile = elfLoader.load(request.input());
        ResolvedProgram resolvedProgram = resolver.resolve(binaryImageAdapter.adapt(elfFile), request.disassembleAll());
        DiscoveryMode mode = request.disassembleAll() ? DiscoveryMode.LINEAR : DiscoveryMode.RECURSIVE;
        DiscoveredProgram discoveredProgram = discoveryEngine.discover(resolvedProgram, mode);

        return emit(request.format(), discoveredProgram);
    }

    /**
     * Executes the full disassembly pipeline for one input file.
     *
     * @param input  path to the ELF file that should be processed
     * @param format output format selector: {@code asm}, {@code json}, or
     *               {@code cfg}
     * @return formatted disassembly output ready to print or write to a file
     * @throws IOException              if the input file cannot be read
     * @throws IllegalArgumentException if {@code format} is not supported
     */
    public String execute(Path input, String format) throws IOException {
        return execute(new DisassemblyRequest(input, format, null, false, false, false, false));
    }

    /**
     * Parses and renders only the ELF header, without requiring the full file to
     * pass
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

    /**
     * Routes decoded instructions to the requested emitter.
     *
     * @param format          output format selector
     * @param discoveredProgram discovered code and metadata
     * @return rendered output
     */
    private String emit(String format, DiscoveredProgram discoveredProgram) {
        switch (format) {
            case "asm":
                return textEmitter.emit(discoveredProgram);
            case "json":
                return jsonEmitter.emit(discoveredProgram);
            case "cfg":
                return cfgBuilder.emit(discoveredProgram);
            default:
                throw new IllegalArgumentException("Unsupported format: " + format);
        }
    }
}

