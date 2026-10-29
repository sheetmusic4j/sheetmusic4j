package com.sheetmusic4j.fxdemo;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.sheetmusic4j.core.io.ScoreFile;
import com.sheetmusic4j.fxdemo.ui.ScoreLoader;
import com.sheetmusic4j.core.io.ScoreFormat;
import com.sheetmusic4j.core.model.Score;
import com.sheetmusic4j.engraving.glyph.MarkingCategory;
import com.sheetmusic4j.fxdemo.inspector.ScoreInspector;
import com.sheetmusic4j.fxdemo.sibling.ImageSibling;
import com.sheetmusic4j.fxdemo.sibling.PdfSibling;
import com.sheetmusic4j.fxdemo.ui.PlaybackSimulator;
import com.sheetmusic4j.fxdemo.ui.PreviewPane;
import com.sheetmusic4j.fxdemo.ui.ReferenceDiffPane;
import com.sheetmusic4j.fxdemo.ui.ResourceTreePane;
import com.sheetmusic4j.fxdemo.ui.Ui;
import com.sheetmusic4j.fxdemo.ui.VisualIssueReporter;
import com.sheetmusic4j.fxviewer.SheetView;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextArea;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

/**
 * Standalone demo/testbed for the Sheetmusic4J {@link SheetView}. Provides a File menu
 * to open scores, a live rendering area, a debug panel, an optional
 * PDF-companion pane, and a "Diff" pane that compares the Sheetmusic4J
 * engraving against the rasterized sibling PDF.
 *
 * <p>The individual panes live in {@code com.sheetmusic4j.fxdemo.ui}; this
 * class wires them together and owns the currently loaded score.
 */
public final class SheetDemoApp extends Application {

    private final ScoreLoader loader = new ScoreLoader();

    private static final double TREE_DIVIDER = 0.18;

    private final SheetView sheetView = new SheetView();
    private final TextArea debugArea = new TextArea();
    private final Label statusLabel = new Label("Ready.");
    private final Label zoomLabel = new Label();
    private final PlaybackSimulator playback = new PlaybackSimulator(sheetView);

    private PreviewPane preview;
    private ReferenceDiffPane diff;
    private ResourceTreePane resources;
    private VisualIssueReporter issueReporter;

    private SplitPane split;
    private BorderPane scorePane;
    private BorderPane debugPane;
    private ScrollPane scoreScroll;
    private double lastViewportWidth = 0;

    private boolean previewVisible = false;
    private boolean debugVisible = false;
    private boolean diffVisible = false;

    private Stage stage;
    private Path currentFile;
    private Score currentScore;

    /**
     * Launches the JavaFX demo application.
     *
     * @param args command-line arguments passed to JavaFX
     */
    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        this.stage = stage;
        preview = new PreviewPane();
        diff = new ReferenceDiffPane(this::showDiffTab);
        resources = new ResourceTreePane(this::openFile);
        issueReporter = new VisualIssueReporter(stage);

        BorderPane root = new BorderPane();
        root.setTop(buildMenuBar());
        root.setCenter(buildContent());
        root.setBottom(statusLabel);
        statusLabel.setPadding(new Insets(2, 6, 2, 6));

        List<String> args = getParameters().getRaw();
        if (!args.isEmpty()) {
            openFile(Path.of(args.getFirst()));
        } else {
            updateDebug();
        }

        Scene scene = new Scene(root, 1400, 800);
        stage.setTitle("Sheetmusic4J Demo");
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

        MenuItem changeResourcesFolder = new MenuItem("Change Resources Folder...");
        changeResourcesFolder.setOnAction(e -> resources.chooseRoot(stage)
                .ifPresent(dir -> statusLabel.setText("Resources folder: " + dir.toAbsolutePath())));

        MenuItem exit = new MenuItem("Exit");
        exit.setAccelerator(new KeyCodeCombination(KeyCode.Q, KeyCombination.SHORTCUT_DOWN));
        exit.setOnAction(e -> Platform.exit());

        fileMenu.getItems().addAll(open, reload, close, new SeparatorMenuItem(),
                changeResourcesFolder, new SeparatorMenuItem(), exit);

        Menu viewMenu = new Menu("View");
        MenuItem showDiff = new MenuItem("Show Diff tab");
        showDiff.setOnAction(e -> showDiffTab());
        viewMenu.getItems().add(showDiff);

        CheckMenuItem showDebugInfo = new CheckMenuItem("Show debug info");
        showDebugInfo.setSelected(false);
        showDebugInfo.setOnAction(e -> {
            debugVisible = showDebugInfo.isSelected();
            rebuildSplit();
        });
        viewMenu.getItems().add(showDebugInfo);

        CheckMenuItem showBrackets = new CheckMenuItem("Show group brackets");
        showBrackets.setSelected(true);
        showBrackets.setOnAction(e -> sheetView.setBracketsVisible(showBrackets.isSelected()));
        viewMenu.getItems().add(showBrackets);

        CheckMenuItem simulatePlayback = new CheckMenuItem("Simulate playback (tint + background)");
        simulatePlayback.setOnAction(e -> togglePlaybackSimulation(simulatePlayback.isSelected()));
        viewMenu.getItems().add(simulatePlayback);

        viewMenu.getItems().add(new SeparatorMenuItem());
        MenuItem zoomIn = new MenuItem("Zoom In");
        zoomIn.setAccelerator(new KeyCodeCombination(KeyCode.EQUALS, KeyCombination.SHORTCUT_DOWN));
        zoomIn.setOnAction(e -> setZoom(sheetView.getZoom() * Ui.ZOOM_STEP));
        MenuItem zoomOut = new MenuItem("Zoom Out");
        zoomOut.setAccelerator(new KeyCodeCombination(KeyCode.MINUS, KeyCombination.SHORTCUT_DOWN));
        zoomOut.setOnAction(e -> setZoom(sheetView.getZoom() / Ui.ZOOM_STEP));
        MenuItem zoomReset = new MenuItem("Actual Size");
        zoomReset.setAccelerator(new KeyCodeCombination(KeyCode.DIGIT0, KeyCombination.SHORTCUT_DOWN));
        zoomReset.setOnAction(e -> setZoom(1.0));
        viewMenu.getItems().addAll(zoomIn, zoomOut, zoomReset);

        Menu textMenu = new Menu("Text");
        textMenu.getItems().addAll(
                textToggle("Show titles", MarkingCategory.TITLE, MarkingCategory.SUBTITLE),
                textToggle("Show composer / lyricist", MarkingCategory.CREATOR),
                textToggle("Show lyrics", MarkingCategory.LYRIC),
                textToggle("Show tempo", MarkingCategory.TEMPO),
                textToggle("Show directions", MarkingCategory.DIRECTION),
                textToggle("Show dynamics", MarkingCategory.DYNAMIC),
                textToggle("Show rehearsal marks", MarkingCategory.REHEARSAL),
                textToggle("Show chord symbols", MarkingCategory.CHORD_SYMBOL),
                textToggle("Show instrument labels", MarkingCategory.PART_LABEL));
        viewMenu.getItems().add(textMenu);

        Menu helpMenu = new Menu("Help");
        MenuItem about = new MenuItem("About");
        about.setOnAction(e -> showAbout());
        helpMenu.getItems().add(about);

        return new MenuBar(fileMenu, viewMenu, helpMenu);
    }

    /**
     * A checked menu item that shows/hides the given marking categories:
     * ticked = visible = removed from the view's hidden set.
     */
    private CheckMenuItem textToggle(String label, MarkingCategory... categories) {
        CheckMenuItem item = new CheckMenuItem(label);
        item.setSelected(true);
        item.setOnAction(e -> {
            var hidden = sheetView.hiddenTextCategoriesProperty();
            for (MarkingCategory category : categories) {
                if (item.isSelected()) {
                    hidden.remove(category);
                } else {
                    hidden.add(category);
                }
            }
        });
        return item;
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
                updateDebug();
            }
            updateScoreViewport();
        });
        // SheetView only ever materializes a Canvas window around whatever
        // viewportTop/viewportHeight say is visible (see its class javadoc),
        // so it must be told the scroll position - both when the user
        // scrolls and whenever the content's own height changes (which
        // changes how vvalue's 0..1 fraction maps to a pixel offset).
        scoreScroll.vvalueProperty().addListener((obs, oldV, newV) -> updateScoreViewport());
        sheetView.heightProperty().addListener((obs, oldV, newV) -> updateScoreViewport());
        scorePane = new BorderPane(scoreScroll);
        scorePane.setTop(buildScoreToolbar());

        debugArea.setEditable(false);
        debugArea.setWrapText(false);
        debugArea.setStyle("-fx-font-family: 'monospaced';");
        debugPane = new BorderPane(debugArea);
        debugPane.setTop(Ui.sectionTitle("Debug info"));

        split = new SplitPane();
        rebuildSplit();
        return split;
    }

    /**
     * Rebuilds the main {@link SplitPane}'s children from the current visibility
     * flags ({@link #previewVisible}, {@link #debugVisible}, {@link #diffVisible}).
     * The resource tree keeps a fixed {@link #TREE_DIVIDER} share; the remaining
     * width is split evenly across whichever content panes are currently shown.
     */
    private void rebuildSplit() {
        List<Node> items = new ArrayList<>();
        items.add(resources.node());
        items.add(scorePane);
        if (previewVisible) {
            items.add(preview.node());
        }
        if (debugVisible) {
            items.add(debugPane);
        }
        if (diffVisible) {
            items.add(diff.node());
        }
        split.getItems().setAll(items);

        int remaining = items.size() - 1;
        double[] positions = new double[remaining];
        if (remaining > 0) {
            positions[0] = TREE_DIVIDER;
            for (int k = 1; k < remaining; k++) {
                positions[k] = TREE_DIVIDER + k * (1.0 - TREE_DIVIDER) / remaining;
            }
        }
        split.setDividerPositions(positions);
    }

    private HBox buildScoreToolbar() {
        Label title = Ui.sectionTitle("Sheetmusic4J rendering");
        Button zoomOut = new Button("-");
        zoomOut.setOnAction(e -> setZoom(sheetView.getZoom() / Ui.ZOOM_STEP));
        Button zoomIn = new Button("+");
        zoomIn.setOnAction(e -> setZoom(sheetView.getZoom() * Ui.ZOOM_STEP));
        Button zoomReset = new Button("100%");
        zoomReset.setOnAction(e -> setZoom(1.0));
        zoomLabel.setPadding(new Insets(0, 8, 0, 0));
        zoomLabel.setText(Ui.zoomText(sheetView.getZoom()));

        Button reportButton = new Button("Report Visual Issue...");
        reportButton.setOnAction(e -> reportVisualIssue());

        HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox bar = new HBox(8, title, spacer, reportButton, new Separator(), zoomOut, zoomIn, zoomReset, zoomLabel);
        bar.setPadding(new Insets(4));
        return bar;
    }

    private void reportVisualIssue() {
        try {
            issueReporter.report(scoreScroll, previewVisible ? preview.node() : null, currentFile, sheetView.getZoom())
                    .ifPresent(dir -> statusLabel.setText("Report saved: " + dir.toAbsolutePath()));
        } catch (IOException ex) {
            showError("Failed to save report", ex.getMessage());
        }
    }

    private void chooseAndOpen() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Open score");
        List<String> all = ScoreFile.formats().stream().flatMap(f -> Ui.globs(f).stream()).toList();
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Score files", all));
        for (ScoreFormat format : ScoreFile.formats()) {
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(format.name(), Ui.globs(format)));
        }
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("All files", "*.*"));
        var file = chooser.showOpenDialog(stage);
        if (file != null) {
            openFile(file.toPath());
        }
    }

    /**
     * Load a score on a background thread - large files take a while to
     * parse - then show it.
     */
    private void openFile(Path path) {
        playback.stop();
        statusLabel.setText("Loading: " + path.toAbsolutePath() + " ...");
        loader.load(path, score -> showScore(path, score), ex -> {
            showError("Failed to open file", path + "\n\n" + (ex != null ? ex.getMessage() : "unknown error"));
            statusLabel.setText("Error loading: " + path);
        });
    }

    private void showScore(Path path, Score score) {
        currentFile = path;
        currentScore = score;
        sheetView.setScore(score);
        updateScoreViewport();
        Optional<Path> pdf = PdfSibling.referenceFor(path);
        Optional<Path> image = pdf.isPresent() ? Optional.empty() : ImageSibling.existingPathFor(path);
        if (pdf.isPresent()) {
            showPreview(() -> preview.showPdf(pdf.get()), "PDF " + pdf.get());
        } else if (image.isPresent()) {
            showPreview(() -> preview.showImage(image.get()), "image " + image.get());
        } else {
            setPreviewVisible(false);
        }
        diff.setSource(path, score, pdf, image);
        updateDebug();
        stage.setTitle("Sheetmusic4J Demo - " + path.getFileName());
        String referenceHint = pdf.map(p -> "  (PDF: " + p.getFileName() + ")")
                .or(() -> image.map(p -> "  (image: " + p.getFileName() + ")"))
                .orElse("");
        statusLabel.setText("Loaded: " + path.toAbsolutePath() + referenceHint);
    }

    private interface PreviewLoader {
        void load() throws IOException;
    }

    private void showPreview(PreviewLoader loader, String what) {
        try {
            loader.load();
            setPreviewVisible(true);
        } catch (IOException | RuntimeException ex) {
            setPreviewVisible(false);
            statusLabel.setText("Could not display " + what + ": " + ex.getMessage());
        }
    }

    private void setPreviewVisible(boolean visible) {
        previewVisible = visible;
        rebuildSplit();
    }

    private void showDiffTab() {
        diffVisible = true;
        rebuildSplit();
    }

    private void setZoom(double value) {
        double clamped = Ui.clampZoom(value);
        sheetView.setZoom(clamped);
        zoomLabel.setText(Ui.zoomText(clamped));
        statusLabel.setText(String.format(Locale.ROOT, "Zoom: %.0f%%", clamped * 100.0));
        updateDebug();
    }

    /**
     * Translates {@code scoreScroll}'s current scroll position/viewport size
     * into the local pixel units {@link SheetView} expects and pushes them
     * into {@link SheetView#viewportTopProperty()}/
     * {@link SheetView#viewportHeightProperty()}, so its internal Canvas
     * window follows scrolling for scores too tall to fit in one Canvas.
     */
    private void updateScoreViewport() {
        var bounds = scoreScroll.getViewportBounds();
        if (bounds == null || bounds.getHeight() <= 0) {
            return;
        }
        double viewportHeight = bounds.getHeight();
        double contentHeight = sheetView.getHeight();
        double maxScroll = Math.max(0, contentHeight - viewportHeight);
        double top = scoreScroll.getVvalue() * maxScroll;
        sheetView.setViewportHeight(viewportHeight);
        sheetView.setViewportTop(top);
    }

    private void reload() {
        if (currentFile != null) {
            openFile(currentFile);
        } else {
            statusLabel.setText("Nothing to reload.");
        }
    }

    private void clear() {
        playback.stop();
        currentFile = null;
        currentScore = null;
        sheetView.setScore(null);
        setPreviewVisible(false);
        diff.clear();
        updateDebug();
        stage.setTitle("Sheetmusic4J Demo");
        statusLabel.setText("Closed.");
    }

    private void togglePlaybackSimulation(boolean on) {
        if (!on) {
            playback.stop();
        } else if (currentScore == null) {
            statusLabel.setText("Load a score first to simulate playback.");
        } else if (playback.start()) {
            statusLabel.setText("Simulating playback: highlighting notes at 350ms cadence.");
        } else {
            statusLabel.setText("Current score has no note anchors to highlight.");
        }
    }

    private void updateDebug() {
        Optional<Path> pdf = PdfSibling.referenceFor(currentFile);
        double width = lastViewportWidth > 0 ? lastViewportWidth : ReferenceDiffPane.DIFF_WIDTH;
        String text = "File: " + (currentFile != null ? currentFile.toAbsolutePath() : "(none)") + '\n'
                + "PDF : " + pdf.map(p -> p.toAbsolutePath().toString()).orElse("(none)") + '\n'
                + "System width (viewport): " + String.format(Locale.ROOT, "%.0f", width) + '\n'
                + "Zoom: " + String.format(Locale.ROOT, "%.0f%%", sheetView.getZoom() * 100.0) + "\n\n"
                + ScoreInspector.describe(currentScore);
        debugArea.setText(text);
    }

    private void showAbout() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.initOwner(stage);
        alert.setTitle("About Sheetmusic4J Demo");
        alert.setHeaderText("Sheetmusic4J Demo");
        alert.setContentText("""
                A JavaFX testbed for the Sheetmusic4J sheet-music rendering library.
                Open a score to preview how it is engraved, either via
                File -> Open... or by clicking a file in the Resources tree on the left.
                A companion PDF or image (same file name) is shown side by side when present.
                Use View -> Show Diff tab to compare against the sibling PDF.""");
        alert.showAndWait();
    }

    private void showError(String header, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.initOwner(stage);
        alert.setTitle("Error");
        alert.setHeaderText(header);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
