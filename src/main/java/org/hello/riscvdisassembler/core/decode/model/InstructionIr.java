package org.hello.riscvdisassembler.core.decode.model;

import org.hello.riscvdisassembler.core.decode.model.ast.AssignExpr;

import java.util.List;

/**
 * Intermediate representation of one decoded instruction.
 *
 * <p>
 * This abstraction decouples bit-level decoding from output and analysis
 * stages.
 * Downstream components consume a normalized structure instead of
 * reinterpreting raw bits.
 * </p>
 */
public final class InstructionIr {
    private final long address;
    private final int rawInstruction;
    private final String mnemonic;
    private final List<String> operands;
    private final String format;
    private final ControlFlowType controlFlowType;
    private final Long branchTarget;
    private final String sectionName;
    private final AssignExpr semantic;

    /**
     * Creates a decoded instruction IR node.
     *
     * @param address         instruction address
     * @param rawInstruction  raw 32-bit machine word
     * @param mnemonic        decoded mnemonic such as {@code addi} or {@code jal}
     * @param operands        textual operand list in display order
     * @param format          instruction encoding family such as {@code R},
     *                        {@code I}, or {@code RAW}
     * @param controlFlowType semantic control-flow category for CFG construction
     * @param branchTarget    resolved direct branch or jump target when statically
     *                        known; otherwise {@code null}
     * @param sectionName     name of the section that owns this instruction
     * @param semantic        semantic AST representing the instruction's effect, or
     *                        {@code null} if unknown
     */
    public InstructionIr(long address, int rawInstruction, String mnemonic, List<String> operands,
            String format, ControlFlowType controlFlowType, Long branchTarget,
            String sectionName, AssignExpr semantic) {
        this.address = address;
        this.rawInstruction = rawInstruction;
        this.mnemonic = mnemonic;
        this.operands = operands;
        this.format = format;
        this.controlFlowType = controlFlowType;
        this.branchTarget = branchTarget;
        this.sectionName = sectionName;
        this.semantic = semantic;
    }

    /** @return instruction address */
    public long address() {
        return address;
    }

    /** @return raw encoded 32-bit instruction word */
    public int rawInstruction() {
        return rawInstruction;
    }

    /** @return decoded mnemonic */
    public String mnemonic() {
        return mnemonic;
    }

    /** @return textual operand list */
    public List<String> operands() {
        return operands;
    }

    /** @return encoding family label */
    public String format() {
        return format;
    }

    /** @return control-flow classification used by analysis stages */
    public ControlFlowType controlFlowType() {
        return controlFlowType;
    }

    /**
     * @return direct branch target address, or {@code null} when unknown or not
     *         applicable
     */
    public Long branchTarget() {
        return branchTarget;
    }

    /** @return owning section name */
    public String sectionName() {
        return sectionName;
    }

    /**
     * @return semantic AST representing the instruction's effect, or {@code null}
     *         if unknown
     */
    public AssignExpr semantic() {
        return semantic;
    }

    /**
     * Control-flow categories recognized by the decoder and consumed by CFG
     * analysis.
     */
    public enum ControlFlowType {
        /**
         * Ordinary instruction that falls through to the next sequential instruction.
         */
        NORMAL,
        /** Conditional branch with a taken edge and a fall-through edge. */
        CONDITIONAL_BRANCH,
        /** Unconditional transfer of control, typically a direct jump. */
        UNCONDITIONAL_JUMP,
        /** Procedure call instruction. */
        CALL,
        /** Procedure return instruction. */
        RETURN,
        /**
         * Instruction that terminates local execution such as {@code ecall} or
         * {@code ebreak}.
         */
        TERMINATOR
    }
}
