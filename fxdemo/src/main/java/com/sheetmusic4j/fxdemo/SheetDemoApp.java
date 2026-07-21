package com.sheetmusic4j.fxdemo;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import com.dlsc.pdfviewfx.PDFView;
import com.sheetmusic4j.core.io.ScoreFile;
import com.sheetmusic4j.core.model.Score;
import com.sheetmusic4j.engraving.Engraver;
import com.sheetmusic4j.engraving.LayoutOptions;
import com.sheetmusic4j.engraving.LayoutResult;
import com.sheetmusic4j.engraving.MarkingCategory;
import com.sheetmusic4j.fxdemo.reference.DiagnosticComparator;
import com.sheetmusic4j.fxdemo.reference.DiffReportWriter;
import com.sheetmusic4j.fxdemo.reference.ImageStack;
import com.sheetmusic4j.fxdemo.reference.PdfRasterizer;
import com.sheetmusic4j.fxviewer.SheetView;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckMenuItem;
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
import javafx.scene.layout.HBox;
import javafx.scene.web.WebView;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

/**
 * Standalone demo/testbed for the Sheet4j {@link SheetView}. Provides a File menu
 * to open MusicXML/MIDI scores, a live rendering area, a debug panel, an
 * optional PDF-companion pane, and a "Diff" pane that compares the Sheet4j
 * engraving against the rasterized sibling PDF.
 */
public final class SheetDemoApp extends Application {

    private static final int DIFF_WIDTH = 1000;

    private static final float PDF_DPI =
            (float) Double.parseDouble(
                    System.getProperty("sheetmusic4j.compare.pdf.dpi", "150"));

    private final SheetView sheetView = new SheetView();
    private final PDFView pdfView = new PDFView();
    private final TextArea debugArea = new TextArea();
    private final Label statusLabel = new Label("Ready.");

    private final WebView diffWebView = new WebView();
    private final Label diffStatus = new Label("Open a MusicXML file with a sibling PDF and click 'Compare against PDF'.");
    private final Button generateReferenceButton = new Button("Compare against PDF");

    private SplitPane split;
    private BorderPane pdfPane;
    private BorderPane debugPane;
    private BorderPane diffPane;
    private ScrollPane scoreScroll;
    private double lastViewportWidth = 0;

    private Stage stage;
    private Path currentFile;
    private Score currentScore;

    @Override
    public void start(Stage stage) {
        this.stage = stage;

        BorderPane root = new BorderPane();
        root.setTop(buildMenuBar());
        root.setCenter(buildContent());

        List<String> args = getParameters().getRaw();
        if (!args.isEmpty()) {
            openFile(Path.of(args.get(0)));
        } else {
            updateDebug(null, Optional.empty());
        }

        Scene scene = new Scene(root, 1400, 800);
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

        Menu viewMenu = new Menu("View");
        MenuItem showDiff = new MenuItem("Show Diff tab");
        showDiff.setOnAction(e -> showDiffTab());
        viewMenu.getItems().add(showDiff);

        Menu textMenu = new Menu("Text");
        CheckMenuItem showTitles = new CheckMenuItem("Show titles");
        showTitles.setSelected(true);
        showTitles.setOnAction(e -> toggleTextCategories(showTitles.isSelected(),
                MarkingCategory.TITLE, MarkingCategory.SUBTITLE));
        CheckMenuItem showCreators = new CheckMenuItem("Show composer / lyricist");
        showCreators.setSelected(true);
        showCreators.setOnAction(e -> toggleTextCategories(showCreators.isSelected(),
                MarkingCategory.CREATOR));
        CheckMenuItem showLyrics = new CheckMenuItem("Show lyrics");
        showLyrics.setSelected(true);
        showLyrics.setOnAction(e -> toggleTextCategories(showLyrics.isSelected(),
                MarkingCategory.LYRIC));
        CheckMenuItem showTempo = new CheckMenuItem("Show tempo");
        showTempo.setSelected(true);
        showTempo.setOnAction(e -> toggleTextCategories(showTempo.isSelected(),
                MarkingCategory.TEMPO));
        CheckMenuItem showDirections = new CheckMenuItem("Show directions");
        showDirections.setSelected(true);
        showDirections.setOnAction(e -> toggleTextCategories(showDirections.isSelected(),
                MarkingCategory.DIRECTION));
        CheckMenuItem showDynamics = new CheckMenuItem("Show dynamics");
        showDynamics.setSelected(true);
        showDynamics.setOnAction(e -> toggleTextCategories(showDynamics.isSelected(),
                MarkingCategory.DYNAMIC));
        CheckMenuItem showRehearsalMarks = new CheckMenuItem("Show rehearsal marks");
        showRehearsalMarks.setSelected(true);
        showRehearsalMarks.setOnAction(e -> toggleTextCategories(showRehearsalMarks.isSelected(),
                MarkingCategory.REHEARSAL));
        CheckMenuItem showChordSymbols = new CheckMenuItem("Show chord symbols");
        showChordSymbols.setSelected(true);
        showChordSymbols.setOnAction(e -> toggleTextCategories(showChordSymbols.isSelected(),
                MarkingCategory.CHORD_SYMBOL));
        CheckMenuItem showPartLabels = new CheckMenuItem("Show instrument labels");
        showPartLabels.setSelected(true);
        showPartLabels.setOnAction(e -> toggleTextCategories(showPartLabels.isSelected(),
                MarkingCategory.PART_LABEL));
        textMenu.getItems().addAll(showTitles, showCreators, showLyrics,
                showTempo, showDirections, showDynamics, showRehearsalMarks,
                showChordSymbols, showPartLabels);
        viewMenu.getItems().add(textMenu);

        Menu helpMenu = new Menu("Help");
        MenuItem about = new MenuItem("About");
        about.setOnAction(e -> showAbout());
        helpMenu.getItems().add(about);

        return new MenuBar(fileMenu, viewMenu, helpMenu);
    }

    private SplitPane buildContent() {
        scoreScroll = new ScrollPane(sheetView);
        // Do NOT fit the view to the viewport: SheetView is content-sized so
        // the ScrollPane can discover the real score dimensions and show
        // horizontal + vertical scrollbars whenever the content overflows.
        scoreScroll.setFitToWidth(false);
        scoreScroll.setFitToHeight(false);
        scoreScroll.setPannable(true);
        scoreScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scoreScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        // Reflow the engraved score to whatever width the viewport currently
        // offers so the user can drag the window and get more/fewer systems.
        scoreScroll.viewportBoundsProperty().addListener((obs, oldV, newV) -> {
            double w = Math.max(200, newV.getWidth() - 4);
            if (Math.abs(w - lastViewportWidth) > 0.5) {
                lastViewportWidth = w;
                sheetView.setSystemWidth(w);
                Optional<Path> pdf = currentFile != null
                        ? PdfSibling.existingPathFor(currentFile)
                        : Optional.empty();
                updateDebug(currentScore, pdf);
            }
        });
        BorderPane scorePane = new BorderPane(scoreScroll);
        scorePane.setTop(sectionTitle("Sheet4j rendering"));

        pdfPane = new BorderPane(pdfView);
        pdfPane.setTop(sectionTitle("Reference PDF"));

        debugArea.setEditable(false);
        debugArea.setWrapText(false);
        debugArea.setStyle("-fx-font-family: 'monospaced';");
        debugPane = new BorderPane(debugArea);
        debugPane.setTop(sectionTitle("Debug info"));

        diffPane = buildDiffPane();

        split = new SplitPane(scorePane, debugPane);
        split.setDividerPositions(0.72);
        return split;
    }

    private BorderPane buildDiffPane() {
        generateReferenceButton.setOnAction(e -> generateReferenceAsync());
        generateReferenceButton.setDisable(true);
        HBox toolbar = new HBox(8, generateReferenceButton, diffStatus);
        toolbar.setPadding(new Insets(4));

        BorderPane pane = new BorderPane(diffWebView);
        pane.setTop(new javafx.scene.layout.VBox(sectionTitle("Diff (Sheet4j vs sibling PDF)"), toolbar));
        return pane;
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
            currentScore = score;
            sheetView.setScore(score);
            Optional<Path> pdf = PdfSibling.existingPathFor(path);
            showPdf(pdf);
            updateDebug(score, pdf);
            stage.setTitle("Sheet4j Demo - " + path.getFileName());
            generateReferenceButton.setDisable(pdf.isEmpty());
            diffStatus.setText(pdf.isPresent()
                    ? "Click 'Compare against PDF' to render the diff report."
                    : "No sibling PDF for this score - diff disabled.");
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

    /**
     * Add or remove the given categories from the sheet view's hidden set,
     * based on whether their menu item is checked. Ticked = visible = remove
     * from hidden set; unticked = hidden = add to hidden set.
     */
    private void toggleTextCategories(boolean visible, MarkingCategory... categories) {
        var hidden = sheetView.hiddenTextCategoriesProperty();
        for (MarkingCategory category : categories) {
            if (visible) {
                hidden.remove(category);
            } else {
                hidden.add(category);
            }
        }
    }

    private void showDiffTab() {
        if (!split.getItems().contains(diffPane)) {
            split.getItems().add(diffPane);
            double[] positions = new double[split.getItems().size() - 1];
            for (int i = 0; i < positions.length; i++) {
                positions[i] = (i + 1.0) / (positions.length + 1.0);
            }
            split.setDividerPositions(positions);
        }
    }

    /**
     * Viewport-driven width used by the top "Sheet4j rendering" pane. The
     * diff comparison keeps the fixed {@link #DIFF_WIDTH} so per-fixture
     * similarity numbers stay comparable across runs.
     *
     * @return the current effective system width in pixels, or
     *         {@code DIFF_WIDTH} when the viewport has not resolved yet
     */
    private double currentViewportWidth() {
        return lastViewportWidth > 0 ? lastViewportWidth : DIFF_WIDTH;
    }

    private void generateReferenceAsync() {
        if (currentFile == null || currentScore == null) {
            diffStatus.setText("Open a MusicXML file first.");
            return;
        }
        Optional<Path> pdf = PdfSibling.existingPathFor(currentFile);
        if (pdf.isEmpty()) {
            diffStatus.setText("No sibling PDF for this score.");
            return;
        }
        showDiffTab();
        generateReferenceButton.setDisable(true);
        diffStatus.setText("Rasterizing sibling PDF...");

        String fileName = currentFile.getFileName().toString();
        Path pdfPath = pdf.get();
        Score score = currentScore;

        Task<Path> task = new Task<>() {
            @Override
            protected Path call() throws Exception {
                OptionalInt count = PdfRasterizer.pageCount(pdfPath);
                if (count.isEmpty()) {
                    throw new IllegalStateException(
                            "PDFBox unavailable; cannot rasterize " + pdfPath);
                }
                Optional<List<BufferedImage>> pages =
                        PdfRasterizer.rasterizeAllPages(pdfPath, PDF_DPI);
                if (pages.isEmpty()) {
                    throw new IllegalStateException("Failed to rasterize " + pdfPath);
                }
                BufferedImage reference = ImageStack.stackVertically(pages.get(), 8, Color.WHITE);

                LayoutResult layout = new Engraver().layout(score, layoutOptions());
                BufferedImage rendered = HeadlessScoreImage.render(score, DIFF_WIDTH);
                DiagnosticComparator.Diagnostic diagnostic =
                        new DiagnosticComparator().compare(rendered, reference, layout);

                Path outDir = Path.of(System.getProperty("java.io.tmpdir"), "sheet4j-diff",
                        stripExtension(fileName));
                return DiffReportWriter.write(outDir, stripExtension(fileName),
                        rendered, reference, count.getAsInt(), layout.systems().size(), diagnostic);
            }
        };
        task.setOnSucceeded(e -> {
            Path html = task.getValue();
            diffWebView.getEngine().load(html.toUri().toString());
            diffStatus.setText("Report: " + html);
            generateReferenceButton.setDisable(false);
        });
        task.setOnFailed(e -> {
            Throwable t = task.getException();
            diffStatus.setText("Failed: " + (t != null ? t.getMessage() : "unknown"));
            generateReferenceButton.setDisable(false);
        });
        new Thread(task, "sheet4j-diff-generator").start();
    }

    private static LayoutOptions layoutOptions() {
        LayoutOptions defaults = LayoutOptions.defaults();
        return new LayoutOptions(
                defaults.staffLineGap(),
                defaults.staffSpacing(),
                DIFF_WIDTH,
                defaults.leftMargin(),
                defaults.rightMargin(),
                defaults.topMargin(),
                defaults.measureMinWidth(),
                defaults.fontSize());
    }

    private static String stripExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot >= 0 ? fileName.substring(0, dot) : fileName;
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
        currentScore = null;
        sheetView.setScore(null);
        removePdf();
        updateDebug(null, Optional.empty());
        stage.setTitle("Sheet4j Demo");
        statusLabel.setText("Closed.");
        generateReferenceButton.setDisable(true);
    }

    private void updateDebug(Score score, Optional<Path> pdf) {
        StringBuilder sb = new StringBuilder();
        sb.append("File: ").append(currentFile != null ? currentFile.toAbsolutePath() : "(none)").append('\n');
        sb.append("PDF : ").append(pdf.map(p -> p.toAbsolutePath().toString()).orElse("(none)")).append('\n');
        sb.append("System width (viewport): ")
                .append(String.format(java.util.Locale.ROOT, "%.0f", currentViewportWidth()))
                .append("\n\n");
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
                A companion PDF (same file name) is shown side by side when present.
                Use View -> Show Diff tab to compare against the sibling PDF.""");
        alert.showAndWait();
    }

    private void showError(String header, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(header);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * Launches the JavaFX demo application.
     *
     * @param args command-line arguments passed to JavaFX
     */
    public static void main(String[] args) {
        launch(args);
    }
}
