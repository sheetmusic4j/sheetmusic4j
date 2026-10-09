package com.sheetmusic4j.fxdemo.ui;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Node;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.TextArea;
import javafx.scene.image.WritableImage;
import javafx.stage.Window;
import javax.imageio.ImageIO;

/**
 * "Report Visual Issue": captures the score view (and the preview, if
 * shown), asks for a description, and writes both images plus a
 * {@code report.json} sidecar into a fresh timestamped folder under
 * {@code reports/}, in a form that is easy to hand off for follow-up.
 */
public final class VisualIssueReporter {

    private final Window owner;

    public VisualIssueReporter(Window owner) {
        this.owner = owner;
    }

    /**
     * Run the report flow.
     *
     * @param scoreNode   the score view to capture
     * @param previewNode the preview to capture, or {@code null} when hidden
     * @param sourceFile  the loaded score file, or {@code null}
     * @param zoom        the current score zoom
     * @return the report folder, or empty when the user cancelled
     * @throws IOException when the report cannot be written
     */
    public Optional<Path> report(Node scoreNode, Node previewNode, Path sourceFile, double zoom)
            throws IOException {
        Optional<String> description = promptDescription();
        if (description.isEmpty()) {
            return Optional.empty();
        }
        Path outDir = createReportDirectory();
        Path scoreImage = saveSnapshot(scoreNode, outDir.resolve("score.png"));
        Path previewImage = previewNode != null ? saveSnapshot(previewNode, outDir.resolve("preview.png")) : null;
        writeReportJson(outDir, description.get(), sourceFile, zoom, scoreImage, previewImage);
        return Optional.of(outDir);
    }

    private Optional<String> promptDescription() {
        Dialog<String> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle("Report Visual Issue");
        dialog.setHeaderText("Describe the visual difference or issue you observed.\n"
                + "The current score view (and preview, if shown) will be saved alongside this text.");
        TextArea textArea = new TextArea();
        textArea.setPrefRowCount(6);
        textArea.setPrefColumnCount(40);
        textArea.setWrapText(true);
        dialog.getDialogPane().setContent(textArea);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.setResultConverter(button -> button == ButtonType.OK ? textArea.getText() : null);
        return dialog.showAndWait();
    }

    private static Path createReportDirectory() throws IOException {
        String stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS").format(LocalDateTime.now());
        Path dir = Path.of("reports", "report-" + stamp);
        Files.createDirectories(dir);
        return dir;
    }

    private static Path saveSnapshot(Node node, Path file) throws IOException {
        WritableImage image = node.snapshot(new SnapshotParameters(), null);
        BufferedImage buffered = SwingFXUtils.fromFXImage(image, null);
        ImageIO.write(buffered, "png", file.toFile());
        return file;
    }

    private static void writeReportJson(Path dir, String description, Path sourceFile, double zoom,
                                        Path scoreImage, Path previewImage) throws IOException {
        String json = "{\n"
                + "  \"timestamp\": " + jsonString(Instant.now().toString()) + ",\n"
                + "  \"sourceFile\": " + jsonString(sourceFile != null ? sourceFile.toAbsolutePath().toString() : null) + ",\n"
                + "  \"zoom\": " + zoom + ",\n"
                + "  \"description\": " + jsonString(description) + ",\n"
                + "  \"scoreImage\": " + jsonString(scoreImage.getFileName().toString()) + ",\n"
                + "  \"previewImage\": " + jsonString(previewImage != null ? previewImage.getFileName().toString() : null) + "\n"
                + "}\n";
        Files.writeString(dir.resolve("report.json"), json);
    }

    private static String jsonString(String value) {
        if (value == null) {
            return "null";
        }
        String escaped = value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
        return "\"" + escaped + "\"";
    }
}
