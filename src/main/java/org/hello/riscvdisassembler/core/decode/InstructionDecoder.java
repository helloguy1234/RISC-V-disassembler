package org.hello.riscvdisassembler.core.decode;

import org.hello.riscvdisassembler.core.binary.model.BinarySection;
import org.hello.riscvdisassembler.core.decode.model.InstructionIr;
import org.hello.riscvdisassembler.core.resolve.ResolvedProgram;

/**
 * Decodes one instruction at a specific address inside a resolved program.
 */
public interface InstructionDecoder {
    /**
     * Decodes one instruction from the given section and address.
     *
     * @param program resolved program containing ELF bytes
     * @param section executable section that owns the address
     * @param address address to decode
     * @return decoded instruction IR
     */
    InstructionIr decodeAt(ResolvedProgram program, BinarySection section, long address);
}

