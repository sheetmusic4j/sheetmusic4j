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
import com.sheetmusic4j.core.model.MusicElement;
import com.sheetmusic4j.core.model.Score;
import com.sheetmusic4j.engraving.Engraver;
import com.sheetmusic4j.engraving.glyph.MarkingCategory;
import com.sheetmusic4j.engraving.layout.LayoutOptions;
import com.sheetmusic4j.engraving.layout.LayoutResult;
import com.sheetmusic4j.engraving.layout.NoteAnchor;
import com.sheetmusic4j.fxdemo.reference.DiagnosticComparator;
import com.sheetmusic4j.fxdemo.reference.DiffReportWriter;
import com.sheetmusic4j.fxdemo.reference.ImageStack;
import com.sheetmusic4j.fxdemo.reference.PdfRasterizer;
import com.sheetmusic4j.fxviewer.SheetView;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.web.WebView;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.Comparator;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Standalone demo/testbed for the Sheetmusic4J {@link SheetView}. Provides a File menu
 * to open MusicXML/MIDI scores, a live rendering area, a debug panel, an
 * optional PDF-companion pane, and a "Diff" pane that compares the Sheetmusic4J
 * engraving against the rasterized sibling PDF.
 */
public final class SheetDemoApp extends Application {

    private static final int DIFF_WIDTH = 1000;
    private static final double ZOOM_MIN = 0.25;
    private static final double ZOOM_MAX = 4.0;
    private static final double ZOOM_STEP = 1.25;

    private static final float PDF_DPI =
            (float) Double.parseDouble(
                    System.getProperty("sheetmusic4j.compare.pdf.dpi", "150"));

    /** Extensions the resource tree lists as loadable scores. */
    private static final List<String> SCORE_EXTENSIONS =
            List.of(".musicxml", ".xml", ".mxl", ".mid", ".midi", ".abc");
    /** Extensions the resource tree lists as directly previewable images. */
    private static final List<String> IMAGE_EXTENSIONS =
            List.of(".png", ".jpg", ".jpeg", ".gif", ".bmp");

    private static final double TREE_DIVIDER = 0.18;
    private static final Log log = LogFactory.getLog(SheetDemoApp.class);

    private final SheetView sheetView = new SheetView();
    private final PDFView pdfView = new PDFView();
    private final ImageView imageView = new ImageView();
    private final ScrollPane imageScroll = new ScrollPane(imageView);
    private final ImageView previewImageView = new ImageView();
    private final ScrollPane previewImageScroll = new ScrollPane(previewImageView);
    private final TextArea debugArea = new TextArea();
    private final Label statusLabel = new Label("Ready.");
    private final Label zoomLabel = new Label();

    private final WebView diffWebView = new WebView();
    private final Label diffStatus = new Label("Open a MusicXML file with a sibling PDF and click 'Compare against PDF'.");
    private final Button generateReferenceButton = new Button("Compare against PDF");

    private SplitPane split;
    private BorderPane pdfPane;
    private BorderPane imagePane;
    private BorderPane treePane;
    private TreeView<Path> resourceTree;
    private Path resourcesRoot;
    private BorderPane scorePane;
    private BorderPane previewPane;
    private BorderPane debugPane;
    private BorderPane diffPane;
    private ScrollPane scoreScroll;
    private double lastViewportWidth = 0;

    private boolean previewVisible = false;
    private boolean debugVisible = false;
    private boolean diffVisible = false;

    private Stage stage;
    private Path currentFile;
    private Score currentScore;
    private Timeline playbackSimulation;
    private int simulationIndex;

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

    /**
     * Resolves the default root for the resource tree: {@code src/test/resources}
     * relative to the current working directory (when launched from the {@code fxdemo}
     * module) or relative to the repository root (when launched from elsewhere),
     * falling back to the working directory itself.
     *
     * @return default resource tree root
     */
    private static Path defaultResourcesRoot() {
        Path direct = Path.of("src/test/resources");
        if (Files.isDirectory(direct)) {
            return direct.toAbsolutePath();
        }
        Path fromRepoRoot = Path.of("fxdemo/src/test/resources");
        if (Files.isDirectory(fromRepoRoot)) {
            return fromRepoRoot.toAbsolutePath();
        }
        return Path.of(System.getProperty("user.dir"));
    }

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
        configurePreviewImageView();

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
        changeResourcesFolder.setOnAction(e -> chooseResourcesRoot());

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
        zoomIn.setOnAction(e -> adjustZoom(ZOOM_STEP));
        MenuItem zoomOut = new MenuItem("Zoom Out");
        zoomOut.setAccelerator(new KeyCodeCombination(KeyCode.MINUS, KeyCombination.SHORTCUT_DOWN));
        zoomOut.setOnAction(e -> adjustZoom(1.0 / ZOOM_STEP));
        MenuItem zoomReset = new MenuItem("Actual Size");
        zoomReset.setAccelerator(new KeyCodeCombination(KeyCode.DIGIT0, KeyCombination.SHORTCUT_DOWN));
        zoomReset.setOnAction(e -> setZoom(1.0));
        viewMenu.getItems().addAll(zoomIn, zoomOut, zoomReset);

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
        scorePane = new BorderPane(scoreScroll);
        scorePane.setTop(buildScoreToolbar());

        treePane = buildTreePane();

        previewPane = new BorderPane(pdfView);
        previewPane.setTop(sectionTitle("Preview"));

        imageView.setPreserveRatio(true);
        imageView.setSmooth(true);
        imageScroll.setPannable(true);
        imageScroll.setFitToWidth(true);
        imageScroll.setFitToHeight(true);
        imagePane = new BorderPane(imageScroll);
        imagePane.setTop(sectionTitle("Reference image"));

        debugArea.setEditable(false);
        debugArea.setWrapText(false);
        debugArea.setStyle("-fx-font-family: 'monospaced';");
        debugPane = new BorderPane(debugArea);
        debugPane.setTop(sectionTitle("Debug info"));

        diffPane = buildDiffPane();

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
        List<Node> items = new java.util.ArrayList<>();
        items.add(treePane);
        items.add(scorePane);
        if (previewVisible) {
            items.add(previewPane);
        }
        if (debugVisible) {
            items.add(debugPane);
        }
        if (diffVisible) {
            items.add(diffPane);
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

    private void configurePreviewImageView() {
        previewImageView.setPreserveRatio(true);
        previewImageView.setSmooth(true);
        previewImageView.fitWidthProperty().bind(previewImageScroll.widthProperty().subtract(4));
        previewImageScroll.setFitToWidth(true);
    }

    private BorderPane buildTreePane() {
        resourcesRoot = defaultResourcesRoot();
        resourceTree = new TreeView<>();
        resourceTree.setCellFactory(tv -> new TreeCell<>() {
            @Override
            protected void updateItem(Path item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null
                        ? null
                        : item.getFileName() != null ? item.getFileName().toString() : item.toString());
            }
        });
        resourceTree.getSelectionModel().selectedItemProperty().addListener((obs, old, selected) -> {
            if (selected != null && selected.getValue() != null && !Files.isDirectory(selected.getValue())) {
                openTreeSelection(selected.getValue());
            }
        });
        setTreeRoot(resourcesRoot);

        BorderPane pane = new BorderPane(resourceTree);
        pane.setTop(sectionTitle("Resources"));
        return pane;
    }

    private void setTreeRoot(Path root) {
        resourcesRoot = root;
        PathTreeItem rootItem = new PathTreeItem(root);
        rootItem.setExpanded(true);
        resourceTree.setRoot(rootItem);
    }

    private void chooseResourcesRoot() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Choose resources folder");
        if (Files.isDirectory(resourcesRoot)) {
            chooser.setInitialDirectory(resourcesRoot.toFile());
        }
        var dir = chooser.showDialog(stage);
        if (dir != null) {
            setTreeRoot(dir.toPath());
            statusLabel.setText("Resources folder: " + dir.getAbsolutePath());
        }
    }

    /**
     * Dispatches a resource-tree file selection. The tree only ever lists
     * score files (see {@link #SCORE_EXTENSIONS}), so this simply loads it.
     *
     * @param path selected file path
     */
    private void openTreeSelection(Path path) {
        openFile(path);
    }

    private void showPreviewImage(Path imagePath) {
        try (InputStream in = Files.newInputStream(imagePath)) {
            previewImageView.setImage(new Image(in));
            showPreviewNode(previewImageScroll);
            statusLabel.setText("Preview: " + imagePath.toAbsolutePath());
        } catch (IOException | RuntimeException ex) {
            showError("Failed to preview image", imagePath + "\n\n" + ex.getMessage());
        }
    }

    private void showPreviewNode(Node node) {
        previewPane.setCenter(node);
        previewVisible = true;
        rebuildSplit();
    }

    private void removePreview() {
        previewVisible = false;
        rebuildSplit();
    }

    private BorderPane buildDiffPane() {
        generateReferenceButton.setOnAction(e -> generateReferenceAsync());
        generateReferenceButton.setDisable(true);
        HBox toolbar = new HBox(8, generateReferenceButton, diffStatus);
        toolbar.setPadding(new Insets(4));

        BorderPane pane = new BorderPane(diffWebView);
        pane.setTop(new javafx.scene.layout.VBox(sectionTitle("Diff (Sheetmusic4J vs sibling PDF)"), toolbar));
        return pane;
    }

    private Label sectionTitle(String text) {
        Label label = new Label(text);
        label.setPadding(new Insets(4));
        label.setStyle("-fx-font-weight: bold;");
        return label;
    }

    private HBox buildScoreToolbar() {
        Label title = sectionTitle("Sheetmusic4J rendering");
        Button zoomOut = new Button("-");
        zoomOut.setOnAction(e -> adjustZoom(1.0 / ZOOM_STEP));
        Button zoomIn = new Button("+");
        zoomIn.setOnAction(e -> adjustZoom(ZOOM_STEP));
        Button zoomReset = new Button("100%");
        zoomReset.setOnAction(e -> setZoom(1.0));
        zoomLabel.setPadding(new Insets(0, 8, 0, 0));
        updateZoomLabel();

        Button reportButton = new Button("Report Visual Issue...");
        reportButton.setOnAction(e -> reportVisualIssue());

        HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox bar = new HBox(8, title, spacer, reportButton, new Separator(), zoomOut, zoomIn, zoomReset, zoomLabel);
        bar.setPadding(new Insets(4));
        return bar;
    }

    /**
     * Captures the current (zoomed/scrolled) score view and, if shown, the preview
     * pane, prompts the user for a free-text description of the visual issue, and
     * writes both images plus a {@code report.json} sidecar (source file, zoom,
     * description, image filenames) into a fresh timestamped folder under
     * {@code target/sheetmusic4j-reports/}. Intended for reporting rendering-vs-
     * reference differences in a form that is easy to hand off for follow-up.
     */
    private void reportVisualIssue() {
        Optional<String> description = promptIssueDescription();
        if (description.isEmpty()) {
            return;
        }
        try {
            Path outDir = createReportDirectory();
            Path scoreImage = saveSnapshot(scoreScroll, outDir.resolve("score.png"));
            Path previewImage = previewVisible ? saveSnapshot(previewPane, outDir.resolve("preview.png")) : null;
            writeReportJson(outDir, description.get(), scoreImage, previewImage);
            statusLabel.setText("Report saved: " + outDir.toAbsolutePath());
        } catch (IOException ex) {
            showError("Failed to save report", ex.getMessage());
        }
    }

    private Optional<String> promptIssueDescription() {
        Dialog<String> dialog = new Dialog<>();
        dialog.initOwner(stage);
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

    private Path createReportDirectory() throws IOException {
        String stamp = java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS")
                .format(java.time.LocalDateTime.now());
        Path dir = Path.of( "reports", "report-" + stamp);
        Files.createDirectories(dir);
        return dir;
    }

    private Path saveSnapshot(Node node, Path file) throws IOException {
        javafx.scene.image.WritableImage image = node.snapshot(new javafx.scene.SnapshotParameters(), null);
        BufferedImage buffered = javafx.embed.swing.SwingFXUtils.fromFXImage(image, null);
        javax.imageio.ImageIO.write(buffered, "png", file.toFile());
        return file;
    }

    private void writeReportJson(Path dir, String description, Path scoreImage, Path previewImage)
            throws IOException {
        String json = "{\n"
                + "  \"timestamp\": " + jsonString(java.time.Instant.now().toString()) + ",\n"
                + "  \"sourceFile\": " + jsonString(currentFile != null ? currentFile.toAbsolutePath().toString() : null) + ",\n"
                + "  \"zoom\": " + sheetView.getZoom() + ",\n"
                + "  \"description\": " + jsonString(description) + ",\n"
                + "  \"scoreImage\": " + jsonString(scoreImage.getFileName().toString()) + ",\n"
                + "  \"previewImage\": " + jsonString(previewImage != null ? previewImage.getFileName().toString() : null) + "\n"
                + "}\n";
        Files.writeString(dir.resolve("report.json"), json);
        log.info("Report written to " + dir.toAbsolutePath());
    }

    private static String jsonString(String value) {
        if (value == null) {
            return "null";
        }
        String escaped = value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
        return "\"" + escaped + "\"";
    }

    private void chooseAndOpen() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Open score");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Score files",
                        "*.musicxml", "*.xml", "*.mxl", "*.mid", "*.midi", "*.abc"),
                new FileChooser.ExtensionFilter("MusicXML", "*.musicxml", "*.xml", "*.mxl"),
                new FileChooser.ExtensionFilter("MIDI", "*.mid", "*.midi"),
                new FileChooser.ExtensionFilter("ABC", "*.abc"),
                new FileChooser.ExtensionFilter("All files", "*.*"));
        var file = chooser.showOpenDialog(stage);
        if (file != null) {
            openFile(file.toPath());
        }
    }

    private void openFile(Path path) {
        try {
            stopPlaybackSimulation();
            Score score = ScoreFile.load(path);
            currentFile = path;
            currentScore = score;
            sheetView.setScore(score);
            Optional<Path> pdf = PdfSibling.existingPathFor(path);
            Optional<Path> image = pdf.isPresent() ? Optional.empty() : ImageSibling.existingPathFor(path);
            if (pdf.isPresent()) {
                showPdfSibling(pdf);
            } else if (image.isPresent()) {
                showPreviewImage(image.get());
            } else {
                removePreview();
            }
            updateDebug(score, pdf);
            stage.setTitle("Sheetmusic4J Demo - " + path.getFileName());
            generateReferenceButton.setDisable(pdf.isEmpty());
            String referenceHint;
            if (pdf.isPresent()) {
                referenceHint = "  (PDF: " + pdf.get().getFileName() + ")";
                diffStatus.setText("Click 'Compare against PDF' to render the diff report.");
            } else if (image.isPresent()) {
                referenceHint = "  (image: " + image.get().getFileName() + ")";
                diffStatus.setText("No sibling PDF for this score - diff disabled. "
                        + "Showing sibling image " + image.get().getFileName() + " instead.");
            } else {
                referenceHint = "";
                diffStatus.setText("No sibling PDF for this score - diff disabled.");
            }
            statusLabel.setText("Loaded: " + path.toAbsolutePath() + referenceHint);
        } catch (RuntimeException ex) {
            showError("Failed to open file", path + "\n\n" + ex.getMessage());
            statusLabel.setText("Error loading: " + path);
        }
    }

    private void showPdfSibling(Optional<Path> pdf) {
        if (pdf.isPresent()) {
            try (InputStream in = Files.newInputStream(pdf.get())) {
                pdfView.load(in);
                showPreviewNode(pdfView);
            } catch (IOException | RuntimeException ex) {
                removePreview();
                statusLabel.setText("Could not display PDF: " + ex.getMessage());
            }
        } else {
            removePreview();
        }
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
        diffVisible = true;
        rebuildSplit();
    }

    private void adjustZoom(double factor) {
        setZoom(sheetView.getZoom() * factor);
    }

    private void setZoom(double value) {
        double clamped = Math.max(ZOOM_MIN, Math.min(ZOOM_MAX, value));
        sheetView.setZoom(clamped);
        updateZoomLabel();
        statusLabel.setText(String.format(java.util.Locale.ROOT,
                "Zoom: %.0f%%", clamped * 100.0));
        Optional<Path> pdf = currentFile != null
                ? PdfSibling.existingPathFor(currentFile)
                : Optional.empty();
        updateDebug(currentScore, pdf);
    }

    private void updateZoomLabel() {
        zoomLabel.setText(String.format(java.util.Locale.ROOT, "Zoom %.0f%%", sheetView.getZoom() * 100.0));
    }

    /**
     * Viewport-driven width used by the top "Sheetmusic4J rendering" pane. The
     * diff comparison keeps the fixed {@link #DIFF_WIDTH} so per-fixture
     * similarity numbers stay comparable across runs.
     *
     * @return the current effective system width in pixels, or
     * {@code DIFF_WIDTH} when the viewport has not resolved yet
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

                Path outDir = Path.of(System.getProperty("java.io.tmpdir"), "sheetmusic4j-diff",
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
        new Thread(task, "sheetmusic4j-diff-generator").start();
    }

    private void reload() {
        if (currentFile != null) {
            openFile(currentFile);
        } else {
            statusLabel.setText("Nothing to reload.");
        }
    }

    private void clear() {
        stopPlaybackSimulation();
        currentFile = null;
        currentScore = null;
        sheetView.setScore(null);
        removePreview();
        updateDebug(null, Optional.empty());
        stage.setTitle("Sheetmusic4J Demo");
        statusLabel.setText("Closed.");
        generateReferenceButton.setDisable(true);
    }

    /**
     * Cheap playback simulator: iterates through the current layout's note
     * anchors at a fixed cadence and highlights each one - both a yellow
     * translucent background (via {@link SheetView#noteBackgrounds()}) and
     * a red foreground tint (via {@link SheetView#noteHighlights()}) - so
     * the two-map API is exercised in the demo.
     */
    private void togglePlaybackSimulation(boolean on) {
        if (on) {
            startPlaybackSimulation();
        } else {
            stopPlaybackSimulation();
        }
    }

    private void startPlaybackSimulation() {
        stopPlaybackSimulation();
        if (currentScore == null) {
            statusLabel.setText("Load a score first to simulate playback.");
            return;
        }
        LayoutResult layout = sheetView.getLayout();
        if (layout == null || layout.noteAnchors().isEmpty()) {
            statusLabel.setText("Current score has no note anchors to highlight.");
            return;
        }
        final List<NoteAnchor> anchors = layout.noteAnchors();
        simulationIndex = 0;
        final javafx.scene.paint.Color highlightTint = javafx.scene.paint.Color.CRIMSON;
        final javafx.scene.paint.Color highlightBg = javafx.scene.paint.Color.color(1.0, 0.92, 0.23, 0.5);
        playbackSimulation = new Timeline(new KeyFrame(Duration.millis(350), evt -> {
            MusicElement previous = simulationIndex > 0
                    ? anchors.get((simulationIndex - 1) % anchors.size()).elementRef()
                    : anchors.get(anchors.size() - 1).elementRef();
            sheetView.noteHighlights().remove(previous);
            sheetView.noteBackgrounds().remove(previous);
            MusicElement current = anchors.get(simulationIndex % anchors.size()).elementRef();
            sheetView.noteHighlights().put(current, highlightTint);
            sheetView.noteBackgrounds().put(current, highlightBg);
            simulationIndex++;
        }));
        playbackSimulation.setCycleCount(Animation.INDEFINITE);
        playbackSimulation.play();
        statusLabel.setText("Simulating playback: highlighting notes at 350ms cadence.");
    }

    private void stopPlaybackSimulation() {
        if (playbackSimulation != null) {
            playbackSimulation.stop();
            playbackSimulation = null;
        }
        sheetView.noteHighlights().clear();
        sheetView.noteBackgrounds().clear();
    }

    private void updateDebug(Score score, Optional<Path> pdf) {
        String sb = "File: " + (currentFile != null ? currentFile.toAbsolutePath() : "(none)") + '\n' +
                "PDF : " + pdf.map(p -> p.toAbsolutePath().toString()).orElse("(none)") + '\n' +
                "System width (viewport): " +
                String.format(java.util.Locale.ROOT, "%.0f", currentViewportWidth()) +
                '\n' +
                "Zoom: " +
                String.format(java.util.Locale.ROOT, "%.0f%%", sheetView.getZoom() * 100.0) +
                "\n\n" +
                ScoreInspector.describe(score);
        debugArea.setText(sb);
    }

    private void showAbout() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.initOwner(stage);
        alert.setTitle("About Sheetmusic4J Demo");
        alert.setHeaderText("Sheetmusic4J Demo");
        alert.setContentText("""
                A JavaFX testbed for the Sheetmusic4J sheet-music rendering library.
                Open MusicXML or MIDI files to preview how they are engraved, either via
                File -> Open... or by clicking a file in the Resources tree on the left.
                A companion PDF (same file name) is shown side by side when present, and
                clicking a PDF or image directly in the tree previews it the same way.
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

    /**
     * Lazily-populated {@link TreeItem} backed by a filesystem {@link Path}. Directory
     * children are listed only when first expanded, and are filtered to
     * subdirectories plus files this demo knows how to open (see
     * {@link #SCORE_EXTENSIONS}). PDFs and images are shown as reference previews
     * alongside a loaded score, not as selectable tree entries.
     */
    private static final class PathTreeItem extends TreeItem<Path> {

        private boolean childrenLoaded = false;

        PathTreeItem(Path path) {
            super(path);
        }

        @Override
        public boolean isLeaf() {
            return !Files.isDirectory(getValue());
        }

        @Override
        public ObservableList<TreeItem<Path>> getChildren() {
            if (!childrenLoaded) {
                childrenLoaded = true;
                super.getChildren().setAll(listChildren(getValue()));
            }
            return super.getChildren();
        }

        private static boolean isDisplayable(Path path) {
            if (Files.isDirectory(path)) {
                return true;
            }
            String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
            return SCORE_EXTENSIONS.stream().anyMatch(name::endsWith);
        }

        private static List<TreeItem<Path>> listChildren(Path dir) {
            try (Stream<Path> stream = Files.list(dir)) {
                return stream
                        .filter(PathTreeItem::isDisplayable)
                        .sorted(Comparator
                                .comparing((Path p) -> !Files.isDirectory(p))
                                .thenComparing(p -> p.getFileName().toString(), String.CASE_INSENSITIVE_ORDER))
                        .map(p -> (TreeItem<Path>) new PathTreeItem(p))
                        .toList();
            } catch (IOException e) {
                return List.of();
            }
        }
    }
}
