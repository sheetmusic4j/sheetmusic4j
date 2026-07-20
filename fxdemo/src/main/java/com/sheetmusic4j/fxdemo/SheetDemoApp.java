package com.sheetmusic4j.fxdemo;

import com.dlsc.pdfviewfx.PDFView;
import com.sheetmusic4j.core.io.ScoreFile;
import com.sheetmusic4j.core.model.Score;
import com.sheetmusic4j.fxviewer.SheetView;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextArea;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.BorderPane;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Standalone demo/testbed for the Sheet4j {@link SheetView}. Provides a File menu
 * to open MusicXML/MIDI scores, a live rendering area, and a debug panel showing
 * score metadata and statistics.
 *
 * <p>When the opened score has a companion PDF next to it (same base name, e.g.
 * {@code song.musicxml} &rarr; {@code song.pdf}), that PDF is shown side by side
 * so the engraving can be compared against a reference rendering.
 */
public final class SheetDemoApp extends Application {

    private final SheetView sheetView = new SheetView();
    private final PDFView pdfView = new PDFView();
    private final TextArea debugArea = new TextArea();
    private final Label statusLabel = new Label("Ready.");

    private SplitPane split;
    private BorderPane pdfPane;
    private BorderPane debugPane;
    private ScrollPane scoreScroll;

    private Stage stage;
    private Path currentFile;

    @Override
    public void start(Stage stage) {
        this.stage = stage;

        BorderPane root = new BorderPane();
        root.setTop(buildMenuBar());
        root.setCenter(buildContent());
        //root.setBottom(buildStatusBar());

        // Optional file argument for quick testing.
        List<String> args = getParameters().getRaw();
        if (!args.isEmpty()) {
            openFile(Path.of(args.get(0)));
        } else {
            updateDebug(null, Optional.empty());
        }

        Scene scene = new Scene(root, 1200, 700);
        stage.setTitle("Sheet4j Demo");
        stage.setScene(scene);
        stage.show();
    }

    private MenuBar buildMenuBar() {
        Menu fileMenu = new Menu("File");

        MenuItem open = new MenuItem("Open...");
        open.setAccelerator(new KeyCodeCombination(KeyCode.O, KeyCombination.SHORTCUT_DOWN));
        open.setOnAction(e -> chooseAndOpen());

        MenuItem reload = new MenuItem("Reload");
        reload.setAccelerator(new KeyCodeCombination(KeyCode.R, KeyCombination.SHORTCUT_DOWN));
        reload.setOnAction(e -> reload());

        MenuItem close = new MenuItem("Close");
        close.setOnAction(e -> clear());

        MenuItem exit = new MenuItem("Exit");
        exit.setAccelerator(new KeyCodeCombination(KeyCode.Q, KeyCombination.SHORTCUT_DOWN));
        exit.setOnAction(e -> Platform.exit());

        fileMenu.getItems().addAll(open, reload, close, new SeparatorMenuItem(), exit);

        Menu helpMenu = new Menu("Help");
        MenuItem about = new MenuItem("About");
        about.setOnAction(e -> showAbout());
        helpMenu.getItems().add(about);

        return new MenuBar(fileMenu, helpMenu);
    }

    private SplitPane buildContent() {
        scoreScroll = new ScrollPane(sheetView);
        scoreScroll.setFitToWidth(true);
        scoreScroll.setPannable(true);
        BorderPane scorePane = new BorderPane(scoreScroll);
        scorePane.setTop(sectionTitle("Sheet4j rendering"));

        pdfPane = new BorderPane(pdfView);
        pdfPane.setTop(sectionTitle("Reference PDF"));

        debugArea.setEditable(false);
        debugArea.setWrapText(false);
        debugArea.setStyle("-fx-font-family: 'monospaced';");
        debugPane = new BorderPane(debugArea);
        debugPane.setTop(sectionTitle("Debug info"));

        split = new SplitPane(scorePane, debugPane);
        split.setDividerPositions(0.72);
        return split;
    }

    private Label sectionTitle(String text) {
        Label label = new Label(text);
        label.setPadding(new Insets(4));
        label.setStyle("-fx-font-weight: bold;");
        return label;
    }

    private void chooseAndOpen() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Open score");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Score files", "*.musicxml", "*.xml", "*.mxl", "*.mid", "*.midi"),
                new FileChooser.ExtensionFilter("MusicXML", "*.musicxml", "*.xml", "*.mxl"),
                new FileChooser.ExtensionFilter("MIDI", "*.mid", "*.midi"),
                new FileChooser.ExtensionFilter("All files", "*.*"));
        var file = chooser.showOpenDialog(stage);
        if (file != null) {
            openFile(file.toPath());
        }
    }

    private void openFile(Path path) {
        try {
            Score score = ScoreFile.load(path);
            currentFile = path;
            sheetView.setScore(score);
            Optional<Path> pdf = PdfSibling.existingPathFor(path);
            showPdf(pdf);
            updateDebug(score, pdf);
            stage.setTitle("Sheet4j Demo - " + path.getFileName());
            statusLabel.setText("Loaded: " + path.toAbsolutePath()
                    + (pdf.isPresent() ? "  (PDF: " + pdf.get().getFileName() + ")" : ""));
        } catch (RuntimeException ex) {
            showError("Failed to open file", path + "\n\n" + ex.getMessage());
            statusLabel.setText("Error loading: " + path);
        }
    }

    private void showPdf(Optional<Path> pdf) {
        boolean shown = split.getItems().contains(pdfPane);
        if (pdf.isPresent()) {
            try (InputStream in = Files.newInputStream(pdf.get())) {
                pdfView.load(in);
                if (!shown) {
                    // Insert the PDF pane between the score and the debug pane.
                    split.getItems().add(1, pdfPane);
                    split.setDividerPositions(0.42, 0.80);
                }
            } catch (IOException | RuntimeException ex) {
                removePdf();
                statusLabel.setText("Could not display PDF: " + ex.getMessage());
            }
        } else {
            removePdf();
        }
    }

    private void removePdf() {
        split.getItems().remove(pdfPane);
        split.setDividerPositions(0.72);
    }

    private void reload() {
        if (currentFile != null) {
            openFile(currentFile);
        } else {
            statusLabel.setText("Nothing to reload.");
        }
    }

    private void clear() {
        currentFile = null;
        sheetView.setScore(null);
        removePdf();
        updateDebug(null, Optional.empty());
        stage.setTitle("Sheet4j Demo");
        statusLabel.setText("Closed.");
    }

    private void updateDebug(Score score, Optional<Path> pdf) {
        StringBuilder sb = new StringBuilder();
        sb.append("File: ").append(currentFile != null ? currentFile.toAbsolutePath() : "(none)").append('\n');
        sb.append("PDF : ").append(pdf.map(p -> p.toAbsolutePath().toString()).orElse("(none)")).append("\n\n");
        sb.append(ScoreInspector.describe(score));
        debugArea.setText(sb.toString());
    }

    private void showAbout() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("About Sheet4j Demo");
        alert.setHeaderText("Sheet4j Demo");
        alert.setContentText("""
                A JavaFX testbed for the Sheet4j sheet-music rendering library.
                Open MusicXML or MIDI files to preview how they are engraved.
                A companion PDF (same file name) is shown side by side when present.""");
        alert.showAndWait();
    }

    private void showError(String header, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(header);
        alert.setContentText(message);
        alert.showAndWait();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
