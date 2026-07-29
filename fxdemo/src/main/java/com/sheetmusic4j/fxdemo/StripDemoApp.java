package com.sheetmusic4j.fxdemo;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.sheetmusic4j.core.io.ScoreFile;
import com.sheetmusic4j.core.model.Chord;
import com.sheetmusic4j.core.model.MusicElement;
import com.sheetmusic4j.core.model.Note;
import com.sheetmusic4j.core.model.Score;
import com.sheetmusic4j.engraving.layout.LayoutResult;
import com.sheetmusic4j.engraving.layout.NoteAnchor;
import com.sheetmusic4j.fxviewer.NoteBackgroundStyle;
import com.sheetmusic4j.fxviewer.StripSheetView;

import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Separator;
import javafx.scene.control.Slider;
import javafx.scene.control.ToolBar;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Standalone JavaFX demo/showcase for the Sheetmusic4J
 * {@link StripSheetView} - the one-line "play-along" score view.
 *
 * <p>Unlike {@link SheetDemoApp}, this app is <em>not</em> about comparing the
 * engraving against a reference PDF; it exists to demonstrate what the strip
 * view can do interactively:
 * <ul>
 *   <li>a play simulation that scrolls the score under a fixed cursor at a
 *       chosen tempo and highlights the notes sounding "right now",</li>
 *   <li>live controls for the foreground highlight tint, the note-background
 *       colour and the cursor colour,</li>
 *   <li>cursor visibility / screen position and a zoom factor.</li>
 * </ul>
 *
 * <p>Open a MusicXML / MIDI / ABC score via <em>File &rarr; Open</em> (or pass a
 * path as the first program argument), then press <em>Play</em>.
 */
public final class StripDemoApp extends Application {

    private static final Logger logger = LoggerFactory.getLogger(StripDemoApp.class);

    private static final String TITLE = "Sheetmusic4J Strip Demo";

    private final StripSheetView stripView = new StripSheetView();

    // Appearance controls.
    private final ColorPicker highlightColor = new ColorPicker(Color.CRIMSON);
    private final ColorPicker backgroundColor = new ColorPicker(Color.color(1.0, 0.92, 0.23, 0.5));
    private final ColorPicker cursorColor = new ColorPicker(Color.CRIMSON);
    private final CheckBox cursorVisible = new CheckBox("Cursor");
    private final Slider cursorPosition = new Slider(0.0, 1.0, 0.3);
    private final Slider zoom = new Slider(0.5, 3.0, 1.0);
    private final Slider spacing = new Slider(1.0, 6.0, 2.0);

    // Background-highlight box geometry (in staff-line gaps).
    private final Slider bgPadX = new Slider(0.0, 3.0, 0.2);
    private final Slider bgPadY = new Slider(0.0, 2.0, 1.3);
    private final Slider bgArc = new Slider(0.0, 2.0, 0.8);
    private final Slider bgOffsetY = new Slider(-3.0, 3.0, -1.3);

    // Transport controls.
    private final Button playButton = new Button("Play");
    private final Button pauseButton = new Button("Pause");
    private final Button stopButton = new Button("Stop");
    private final Slider tempo = new Slider(20, 240, 100);
    private final CheckBox loop = new CheckBox("Loop");
    private final Label timeLabel = new Label();
    private final Label statusLabel = new Label("Open a score to begin (File → Open).");

    // Playback state.
    private final Set<MusicElement> active = Collections.newSetFromMap(new IdentityHashMap<>());
    private List<NoteAnchor> soundingAnchors = List.of();
    private AnimationTimer timer;
    private long lastNanos = -1L;
    private boolean playing;
    /** Continuous musical playhead in quarter notes. */
    private double playhead;
    /**
     * Cursor position timeline: strictly increasing note onsets and the layout
     * x of each. The cursor x is interpolated across these so it moves
     * continuously (passing exactly through each notehead at its onset) rather
     * than parking on a held note.
     */
    private double[] sampleOnset = new double[0];
    private double[] sampleX = new double[0];

    private Stage stage;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        this.stage = stage;

        // Drive the view's appearance directly from the controls.
        cursorVisible.setSelected(true);
        stripView.cursorColorProperty().bind(cursorColor.valueProperty());
        stripView.cursorVisibleProperty().bind(cursorVisible.selectedProperty());
        stripView.cursorScreenPositionProperty().bind(cursorPosition.valueProperty());
        stripView.zoomProperty().bind(zoom.valueProperty());

        // Spacing re-engraves the score (note x positions change), so after it
        // we rebuild the cursor timeline and re-place the cursor.
        spacing.valueProperty().addListener((obs, o, n) -> applySpacing());

        // Recolour the currently-lit notes when the pickers change mid-play.
        highlightColor.valueProperty().addListener((obs, o, n) -> reapplyActiveColours());
        backgroundColor.valueProperty().addListener((obs, o, n) -> reapplyActiveColours());

        // Rebuild the background-box geometry whenever a slider moves.
        bgPadX.valueProperty().addListener((obs, o, n) -> updateBackgroundStyle());
        bgPadY.valueProperty().addListener((obs, o, n) -> updateBackgroundStyle());
        bgArc.valueProperty().addListener((obs, o, n) -> updateBackgroundStyle());
        bgOffsetY.valueProperty().addListener((obs, o, n) -> updateBackgroundStyle());
        updateBackgroundStyle();

        playButton.setOnAction(e -> play());
        pauseButton.setOnAction(e -> pause());
        stopButton.setOnAction(e -> stopPlayback());

        timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                onFrame(now);
            }
        };

        BorderPane root = new BorderPane();
        root.setTop(buildMenuBar());
        root.setCenter(buildStripArea());
        root.setBottom(buildControls());

        List<String> args = getParameters().getRaw();
        if (!args.isEmpty()) {
            openFile(Path.of(args.getFirst()));
        }

        Scene scene = new Scene(root, 1100, 500);
        stage.setTitle(TITLE);
        stage.setScene(scene);
        stage.show();

        updateTimeLabel();
        updateButtons();
    }

    // ------------------------------------------------------------------
    // Scene construction
    // ------------------------------------------------------------------

    private MenuBar buildMenuBar() {
        Menu fileMenu = new Menu("File");
        MenuItem open = new MenuItem("Open...");
        open.setAccelerator(new KeyCodeCombination(KeyCode.O, KeyCombination.SHORTCUT_DOWN));
        open.setOnAction(e -> chooseAndOpen());
        MenuItem quit = new MenuItem("Quit");
        quit.setOnAction(e -> stage.close());
        fileMenu.getItems().addAll(open, quit);
        return new MenuBar(fileMenu);
    }

    private StackPane buildStripArea() {
        StackPane area = new StackPane(stripView);
        area.setPadding(new Insets(12));
        area.setStyle("-fx-background-color: linear-gradient(to bottom, #fafafa, #ececec);");
        StackPane.setAlignment(stripView, Pos.CENTER);
        // Fill the available width (the cursor math depends on getWidth()) but
        // keep the natural one-line height, vertically centred.
        stripView.setMaxWidth(Double.MAX_VALUE);
        return area;
    }

    private VBox buildControls() {
        tempo.setPrefWidth(160);
        tempo.setShowTickLabels(true);
        tempo.setShowTickMarks(true);
        tempo.setMajorTickUnit(55);
        Label tempoValue = new Label();
        tempoValue.textProperty().bind(tempo.valueProperty().asString("%.0f BPM"));

        ToolBar transport = new ToolBar(
                playButton, pauseButton, stopButton,
                new Separator(),
                new Label("Tempo:"), tempo, tempoValue,
                new Separator(),
                loop,
                new Separator(),
                new Label("Time:"), timeLabel);

        cursorPosition.setPrefWidth(120);
        zoom.setPrefWidth(120);
        zoom.setShowTickMarks(true);
        Label zoomValue = new Label();
        zoomValue.textProperty().bind(zoom.valueProperty().asString("%.2fx"));

        spacing.setPrefWidth(120);
        Label spacingValue = new Label();
        spacingValue.textProperty().bind(spacing.valueProperty().asString("%.1f"));

        ToolBar appearance1 = new ToolBar(
                new Label("Highlight:"), highlightColor,
                new Label("Background:"), backgroundColor,
                new Separator(),
                new Label("Cursor:"), cursorColor, cursorVisible);

        ToolBar appearance2 = new ToolBar(
                new Label("Position:"), cursorPosition,
                new Separator(),
                new Label("Zoom:"), zoom, zoomValue,
                new Separator(),
                new Label("Spacing:"), spacing, spacingValue);

        bgPadX.setPrefWidth(100);
        bgPadY.setPrefWidth(100);
        bgArc.setPrefWidth(80);
        bgOffsetY.setPrefWidth(100);
        Label bgPadXValue = new Label();
        bgPadXValue.textProperty().bind(bgPadX.valueProperty().asString("%.1f"));
        Label bgPadYValue = new Label();
        bgPadYValue.textProperty().bind(bgPadY.valueProperty().asString("%.1f"));
        Label bgArcValue = new Label();
        bgArcValue.textProperty().bind(bgArc.valueProperty().asString("%.1f"));
        Label bgOffsetYValue = new Label();
        bgOffsetYValue.textProperty().bind(bgOffsetY.valueProperty().asString("%+.1f"));

        ToolBar backgroundBox = new ToolBar(
                new Label("Background box (staff gaps) -  width:"), bgPadX, bgPadXValue,
                new Separator(),
                new Label("height:"), bgPadY, bgPadYValue,
                new Separator(),
                new Label("corner:"), bgArc, bgArcValue,
                new Separator(),
                new Label("offset Y:"), bgOffsetY, bgOffsetYValue);

        statusLabel.setPadding(new Insets(4, 8, 6, 8));

        VBox box = new VBox(transport, appearance1, appearance2, backgroundBox, statusLabel);
        VBox.setVgrow(box, Priority.NEVER);
        return box;
    }

    /** Apply a new strip spacing factor: re-engrave, rebuild the timeline, re-place the cursor. */
    private void applySpacing() {
        stripView.setStripSpacingFactor(spacing.getValue());
        recomputeAnchors();
        if (stripView.getScore() != null) {
            stripView.setCursorLayoutX(cursorXForTime(playhead));
        }
    }

    /** Rebuild the note-background geometry from the three sliders (symmetric padding). */
    private void updateBackgroundStyle() {
        stripView.setNoteBackgroundStyle(NoteBackgroundStyle.defaults()
                .withHorizontalPadding(bgPadX.getValue())
                .withVerticalPadding(bgPadY.getValue())
                .withArc(bgArc.getValue())
                .withOffsetY(bgOffsetY.getValue()));
    }

    // ------------------------------------------------------------------
    // File loading
    // ------------------------------------------------------------------

    private void chooseAndOpen() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Open score");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Score files",
                        "*.musicxml", "*.xml", "*.mxl", "*.mid", "*.midi", "*.abc", "*.gp"),
                new FileChooser.ExtensionFilter("MusicXML", "*.musicxml", "*.xml", "*.mxl"),
                new FileChooser.ExtensionFilter("MIDI", "*.mid", "*.midi"),
                new FileChooser.ExtensionFilter("GuitarPro", "*.gp"),
                new FileChooser.ExtensionFilter("ABC", "*.abc"),
                new FileChooser.ExtensionFilter("All files", "*.*"));
        var file = chooser.showOpenDialog(stage);
        if (file != null) {
            openFile(file.toPath());
        }
    }

    private void openFile(Path path) {
        try {
            stopPlayback();
            Score score = ScoreFile.load(path);
            stripView.setScore(score);
            recomputeAnchors();
            playhead = 0;
            stripView.setCursorTime(0);
            updateTimeLabel();
            stage.setTitle(TITLE + " - " + path.getFileName());
            statusLabel.setText(String.format(Locale.ROOT,
                    "Loaded %s - %.1f quarter notes, %d sounding notes. Press Play.",
                    path.getFileName(), stripView.totalDurationQuarters(), soundingAnchors.size()));
        } catch (RuntimeException ex) {
            logger.error("Failed to open score: {}", path, ex);
            statusLabel.setText("Failed to open " + path.getFileName() + ": " + ex.getMessage());
        }
        updateButtons();
    }

    private void recomputeAnchors() {
        List<NoteAnchor> anchors = new ArrayList<>();
        LayoutResult layout = stripView.getLayout();
        if (layout != null) {
            for (NoteAnchor anchor : layout.noteAnchors()) {
                if (anchor.durationQuarters() > 0
                        && (anchor.elementRef() instanceof Note || anchor.elementRef() instanceof Chord)) {
                    anchors.add(anchor);
                }
            }
        }
        soundingAnchors = anchors;
        buildCursorTimeline(layout);
    }

    /**
     * Build the {@link #sampleOnset}/{@link #sampleX} timeline the cursor
     * interpolates over: every anchor's onset mapped to its layout x, sorted
     * and deduplicated by onset, bracketed by the start and end of the layout
     * so the cursor keeps moving before the first note and after the last.
     */
    private void buildCursorTimeline(LayoutResult layout) {
        if (layout == null || layout.noteAnchors().isEmpty()) {
            sampleOnset = new double[0];
            sampleX = new double[0];
            return;
        }
        List<NoteAnchor> sorted = new ArrayList<>(layout.noteAnchors());
        sorted.sort(java.util.Comparator.comparingDouble(NoteAnchor::onsetQuarters));

        List<Double> onsets = new ArrayList<>();
        List<Double> xs = new ArrayList<>();
        // Leading sample so the cursor eases in from the staff start.
        if (sorted.get(0).onsetQuarters() > 0) {
            onsets.add(0.0);
            xs.add(layout.xAtTime(0));
        }
        for (NoteAnchor a : sorted) {
            if (!onsets.isEmpty() && a.onsetQuarters() == onsets.get(onsets.size() - 1)) {
                continue; // same time column (e.g. chord members) -> one sample
            }
            onsets.add(a.onsetQuarters());
            xs.add(a.x());
        }
        // Trailing sample at the end of the piece so motion continues through
        // the final note's duration to the closing barline.
        double total = layout.totalDurationQuarters();
        if (total > onsets.get(onsets.size() - 1)) {
            onsets.add(total);
            xs.add(layout.xAtTime(total));
        }

        sampleOnset = new double[onsets.size()];
        sampleX = new double[xs.size()];
        for (int i = 0; i < onsets.size(); i++) {
            sampleOnset[i] = onsets.get(i);
            sampleX[i] = xs.get(i);
        }
    }

    // ------------------------------------------------------------------
    // Play simulation
    // ------------------------------------------------------------------

    private void play() {
        if (stripView.getScore() == null) {
            statusLabel.setText("Open a score first.");
            return;
        }
        if (stripView.totalDurationQuarters() <= 0) {
            statusLabel.setText("This score has no sounding notes to play.");
            return;
        }
        playing = true;
        lastNanos = -1L;
        timer.start();
        statusLabel.setText("Playing.");
        updateButtons();
    }

    private void pause() {
        playing = false;
        timer.stop();
        statusLabel.setText("Paused.");
        updateButtons();
    }

    private void stopPlayback() {
        playing = false;
        if (timer != null) {
            timer.stop();
        }
        lastNanos = -1L;
        playhead = 0;
        stripView.setCursorTime(0);
        clearHighlights();
        updateTimeLabel();
        updateButtons();
    }

    private void onFrame(long now) {
        if (!playing) {
            return;
        }
        if (lastNanos < 0) {
            lastNanos = now;
            return;
        }
        double dt = (now - lastNanos) / 1_000_000_000.0;
        lastNanos = now;

        double total = stripView.totalDurationQuarters();
        double quartersPerSecond = tempo.getValue() / 60.0;
        playhead += quartersPerSecond * dt;

        if (playhead >= total) {
            if (loop.isSelected()) {
                playhead = total <= 0 ? 0 : playhead % total;
                clearHighlights();
            } else {
                playhead = total;
                syncToPlayhead();
                playing = false;
                timer.stop();
                statusLabel.setText("Finished.");
                updateButtons();
                return;
            }
        }

        syncToPlayhead();
    }

    /**
     * Highlight the notes sounding at the current {@link #playhead} and move the
     * cursor to the continuous, note-aware position for that time: interpolated
     * across the note timeline so the marker glides the whole time it is
     * playing, speeds up or slows with the note spacing, and still passes
     * exactly through each notehead at its onset.
     */
    private void syncToPlayhead() {
        updateHighlights(playhead);
        stripView.setCursorLayoutX(cursorXForTime(playhead));
        updateTimeLabel();
    }

    /**
     * Interpolate the cursor x across the {@link #sampleOnset}/{@link #sampleX}
     * note timeline for the given musical time, clamped to its ends. Falls back
     * to the layout's time-based x when no timeline is available.
     */
    private double cursorXForTime(double quarters) {
        int n = sampleOnset.length;
        if (n == 0) {
            LayoutResult layout = stripView.getLayout();
            return layout != null ? layout.xAtTime(quarters) : 0.0;
        }
        if (quarters <= sampleOnset[0]) {
            return sampleX[0];
        }
        if (quarters >= sampleOnset[n - 1]) {
            return sampleX[n - 1];
        }
        int i = 1;
        while (i < n && sampleOnset[i] < quarters) {
            i++;
        }
        double span = sampleOnset[i] - sampleOnset[i - 1];
        double f = span <= 0 ? 0 : (quarters - sampleOnset[i - 1]) / span;
        return sampleX[i - 1] + f * (sampleX[i] - sampleX[i - 1]);
    }

    /**
     * Highlight every note whose sounding window contains {@code t} and
     * unhighlight the rest.
     */
    private void updateHighlights(double t) {
        Color tint = highlightColor.getValue();
        Color background = backgroundColor.getValue();

        Set<MusicElement> current = Collections.newSetFromMap(new IdentityHashMap<>());
        for (NoteAnchor anchor : soundingAnchors) {
            double onset = anchor.onsetQuarters();
            if (onset <= t && t < onset + anchor.durationQuarters()) {
                current.add(anchor.elementRef());
            }
        }

        for (MusicElement element : active) {
            if (!current.contains(element)) {
                stripView.noteHighlights().remove(element);
                stripView.noteBackgrounds().remove(element);
            }
        }
        for (MusicElement element : current) {
            if (!active.contains(element)) {
                stripView.noteHighlights().put(element, tint);
                stripView.noteBackgrounds().put(element, background);
            }
        }
        active.clear();
        active.addAll(current);
    }

    private void reapplyActiveColours() {
        Color tint = highlightColor.getValue();
        Color background = backgroundColor.getValue();
        for (MusicElement element : active) {
            stripView.noteHighlights().put(element, tint);
            stripView.noteBackgrounds().put(element, background);
        }
    }

    private void clearHighlights() {
        stripView.noteHighlights().clear();
        stripView.noteBackgrounds().clear();
        active.clear();
    }

    private void updateTimeLabel() {
        timeLabel.setText(String.format(Locale.ROOT, "%.1f / %.1f",
                playhead, stripView.totalDurationQuarters()));
    }

    private void updateButtons() {
        boolean hasScore = stripView.getScore() != null && stripView.totalDurationQuarters() > 0;
        playButton.setDisable(!hasScore || playing);
        pauseButton.setDisable(!playing);
        stopButton.setDisable(!hasScore);
    }
}
