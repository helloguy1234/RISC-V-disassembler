package org.hello.riscvdisassembler.ui;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.hello.riscvdisassembler.pipeline.DisassemblyPipeline;
import org.hello.riscvdisassembler.pipeline.DisassemblyRequest;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * JavaFX desktop user interface for the RISC-V disassembler.
 *
 * <p>The UI allows users to choose an input file, select an output format, enable
 * debugging/disassembly flags, and run the same pipeline that the CLI uses.</p>
 */
public final class JavaFxDisassemblerApp extends Application {
    private static DisassemblyRequest initialRequest;

    private final DisassemblyPipeline pipeline = new DisassemblyPipeline();

    /**
     * Stores an initial request before JavaFX bootstraps the application instance.
     *
     * @param request initial request used to prepopulate the UI controls
     */
    public static void prepare(DisassemblyRequest request) {
        initialRequest = request;
    }

    /**
     * Builds and shows the main application window.
     *
     * @param stage primary JavaFX stage
     */
    @Override
    public void start(Stage stage) {
        TextField inputField = new TextField(valueOf(initialRequest != null ? initialRequest.input() : null));
        TextField outputField = new TextField(valueOf(initialRequest != null ? initialRequest.output() : null));

        ComboBox<String> formatBox = new ComboBox<>();
        formatBox.getItems().addAll("asm", "json", "cfg");
        formatBox.setValue(initialRequest != null ? initialRequest.format() : "asm");

        CheckBox headerOnlyBox = new CheckBox("Header only");
        headerOnlyBox.setSelected(initialRequest != null && initialRequest.headerOnly());

        CheckBox disassembleAllBox = new CheckBox("Disassemble all sections");
        disassembleAllBox.setSelected(initialRequest != null && initialRequest.disassembleAll());

        CheckBox debugBox = new CheckBox("Debug stack trace");
        debugBox.setSelected(initialRequest != null && initialRequest.debug());

        TextArea outputArea = new TextArea();
        outputArea.setEditable(false);
        outputArea.setWrapText(false);

        Button browseInputButton = new Button("Browse...");
        browseInputButton.setOnAction(event -> chooseInputFile(stage, inputField));

        Button browseOutputButton = new Button("Save As...");
        browseOutputButton.setOnAction(event -> chooseOutputFile(stage, outputField));

        Button runButton = new Button("Run");
        runButton.setDefaultButton(true);
        runButton.setOnAction(event -> executeRequest(
                inputField,
                outputField,
                formatBox,
                headerOnlyBox,
                disassembleAllBox,
                debugBox,
                outputArea
        ));

        headerOnlyBox.selectedProperty().addListener((observable, oldValue, newValue) ->
                formatBox.setDisable(newValue));

        GridPane form = new GridPane();
        form.setHgap(10);
        form.setVgap(10);
        form.add(new Label("Input"), 0, 0);
        form.add(inputField, 1, 0);
        form.add(browseInputButton, 2, 0);
        form.add(new Label("Format"), 0, 1);
        form.add(formatBox, 1, 1);
        form.add(new Label("Output"), 0, 2);
        form.add(outputField, 1, 2);
        form.add(browseOutputButton, 2, 2);
        form.add(headerOnlyBox, 1, 3);
        form.add(disassembleAllBox, 1, 4);
        form.add(debugBox, 1, 5);
        GridPane.setHgrow(inputField, Priority.ALWAYS);
        GridPane.setHgrow(outputField, Priority.ALWAYS);

        HBox actions = new HBox(10, runButton);
        VBox root = new VBox(12, form, actions, outputArea);
        root.setPadding(new Insets(14));
        VBox.setVgrow(outputArea, Priority.ALWAYS);

        if (headerOnlyBox.isSelected()) {
            formatBox.setDisable(true);
        }

        stage.setTitle("RISC-V Disassembler");
        stage.setScene(new Scene(root, 820, 560));
        stage.show();
    }

    /**
     * Opens a file chooser and fills the input field with the selected file path.
     *
     * @param stage owner stage
     * @param inputField destination text field
     */
    private void chooseInputFile(Stage stage, TextField inputField) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose ELF File");
        java.io.File chosen = chooser.showOpenDialog(stage);
        if (chosen != null) {
            inputField.setText(chosen.getAbsolutePath());
        }
    }

    /**
     * Opens a file chooser and fills the output field with the selected destination path.
     *
     * @param stage owner stage
     * @param outputField destination text field
     */
    private void chooseOutputFile(Stage stage, TextField outputField) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose Output File");
        java.io.File chosen = chooser.showSaveDialog(stage);
        if (chosen != null) {
            outputField.setText(chosen.getAbsolutePath());
        }
    }

    /**
     * Reads UI state, executes the shared pipeline, and renders the result in the output area.
     *
     * @param inputField input file control
     * @param outputField output file control
     * @param formatBox format selector
     * @param headerOnlyBox header-only toggle
     * @param disassembleAllBox disassemble-all toggle
     * @param debugBox debug toggle
     * @param outputArea result area
     */
    private void executeRequest(TextField inputField, TextField outputField, ComboBox<String> formatBox,
                                CheckBox headerOnlyBox, CheckBox disassembleAllBox, CheckBox debugBox,
                                TextArea outputArea) {
        String inputValue = inputField.getText().trim();
        if (inputValue.isEmpty()) {
            showError("Input file is required.");
            return;
        }

        Path input = Path.of(inputValue);
        Path output = outputField.getText().trim().isEmpty() ? null : Path.of(outputField.getText().trim());
        String format = headerOnlyBox.isSelected() ? "asm" : formatBox.getValue();

        DisassemblyRequest request = new DisassemblyRequest(
                input,
                format,
                output,
                debugBox.isSelected(),
                headerOnlyBox.isSelected(),
                disassembleAllBox.isSelected(),
                true
        );

        try {
            String result = pipeline.execute(request);
            if (output != null) {
                Files.write(output, result.getBytes(StandardCharsets.UTF_8));
            }
            outputArea.setText(result);
        } catch (IOException | RuntimeException ex) {
            outputArea.setText(buildErrorText(ex, debugBox.isSelected()));
        }
    }

    /**
     * Builds a user-facing error report for the output area.
     *
     * @param ex caught exception
     * @param debug whether the stack trace should be included
     * @return formatted error text
     */
    private String buildErrorText(Exception ex, boolean debug) {
        StringBuilder sb = new StringBuilder();
        sb.append("Disassembly failed: ");
        if (ex.getMessage() == null || ex.getMessage().trim().isEmpty()) {
            sb.append(ex.getClass().getSimpleName());
        } else {
            sb.append(ex.getClass().getSimpleName()).append(": ").append(ex.getMessage());
        }
        if (debug) {
            StringWriter stringWriter = new StringWriter();
            ex.printStackTrace(new PrintWriter(stringWriter));
            sb.append(System.lineSeparator()).append(System.lineSeparator()).append(stringWriter);
        }
        return sb.toString();
    }

    /**
     * Shows a modal error dialog for simple validation failures.
     *
     * @param message message shown to the user
     */
    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("RISC-V Disassembler");
        alert.setHeaderText("Unable to run disassembly");
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * Converts a path to a string safe for preloading text fields.
     *
     * @param path path value, possibly {@code null}
     * @return path text or an empty string
     */
    private static String valueOf(Path path) {
        return path == null ? "" : path.toString();
    }
}
