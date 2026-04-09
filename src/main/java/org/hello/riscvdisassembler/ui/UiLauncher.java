package org.hello.riscvdisassembler.ui;

import javafx.application.Application;
import org.hello.riscvdisassembler.pipeline.DisassemblyRequest;

/**
 * Launches the JavaFX user interface for the disassembler.
 */
public final class UiLauncher {
    /**
     * Starts the JavaFX application and passes the initial request options to it.
     *
     * @param request initial request used to prefill the UI
     */
    public void launch(DisassemblyRequest request) {
        JavaFxDisassemblerApp.prepare(request);
        Application.launch(JavaFxDisassemblerApp.class);
    }
}
