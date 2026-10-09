package com.sheetmusic4j.fxdemo;

import com.sheetmusic4j.core.io.ScoreFile;
import com.sheetmusic4j.core.io.ScoreFormat;
import com.sheetmusic4j.core.model.Score;
import com.sheetmusic4j.fxdemo.ui.Ui;
import com.sheetmusic4j.fxviewer.FlyOverSheetView;
import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * Standalone JavaFX demo for {@link FlyOverSheetView}: the strip engraving on
 * a sheet of paper in 3D, a camera flying along it and noteheads hopping off
 * the paper as they are played.
 *
 * <p>Open a MusicXML / MIDI / ABC score via <em>File &rarr; Open</em> (or pass a
 * path as the first program argument), then press <em>Play</em>, or drag the
 * position slider to scrub. The camera sliders change the fly-over angle live.
 */
public final class FlyOverDemoApp extends Application {

    private static final Logger logger = LoggerFactory.getLogger(FlyOverDemoApp.class);

    private static final String TITLE = "Sheetmusic4J Fly-over Demo";

    private final FlyOverSheetView flyOver = new FlyOverSheetView();

    private final Button playButton = new Button("Play");
    private final Button pauseButton = new Button("Pause");
    private final Button stopButton = new Button("Stop");
    private final Slider tempo = new Slider(20, 240, 100);
    private final CheckBox loop = new CheckBox("Loop");
    private final Slider position = new Slider(0, 1, 0);
    private final Label timeLabel = new Label();
    private final Label statusLabel = new Label("Open a score to begin (File → Open).");

    private AnimationTimer timer;
    private long lastNanos = -1L;
    private boolean playing;
    /**
     * Set while the timer moves the position slider, so its listener doesn't seek.
     */
    private boolean advancing;

    private Stage stage;

    public static void main(String[] args) {
        launch(args);
    }

    /**
     * A slider with a live value label, bound both ways to a view property.
     */
    private static HBox slider(DoubleProperty property, double min, double max, String format) {
        return slider(property, min, max, format, false);
    }

    // ------------------------------------------------------------------
    // Scene construction
    // ------------------------------------------------------------------

    /**
     * A slider with a live value label for a view property. When the property
     * re-renders the paper (spacing, texture scale) it is only applied when
     * the knob is released.
     */
    private static HBox slider(DoubleProperty property, double min, double max, String format,
                               boolean rebuildsPaper) {
        Slider slider = new Slider(min, max, property.get());
        slider.setPrefWidth(120);
        if (rebuildsPaper) {
            slider.valueChangingProperty().addListener((obs, o, changing) -> {
                if (!changing) {
                    property.set(slider.getValue());
                }
            });
            slider.setOnMouseReleased(e -> property.set(slider.getValue()));
        } else {
            slider.valueProperty().bindBidirectional(property);
        }
        Label value = new Label();
        value.textProperty().bind(slider.valueProperty().asString(Locale.ROOT, format));
        value.setMinWidth(40);
        return new HBox(4, slider, value);
    }

    @Override
    public void start(Stage stage) {
        this.stage = stage;

        playButton.setOnAction(e -> play());
        pauseButton.setOnAction(e -> pause());
        stopButton.setOnAction(e -> stopPlayback());
        position.valueProperty().addListener((obs, o, n) -> {
            if (!advancing) {
                seek(n.doubleValue());
            }
        });

        timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                onFrame(now);
            }
        };

        BorderPane root = new BorderPane();
        root.setTop(buildMenuBar());
        root.setCenter(flyOver);
        root.setBottom(buildControls());

        if (!FlyOverSheetView.is3DSupported()) {
            statusLabel.setText("This platform does not support JavaFX 3D; the fly-over view cannot render.");
        }

        List<String> args = getParameters().getRaw();
        if (!args.isEmpty()) {
            openFile(Path.of(args.getFirst()));
        }

        stage.setTitle(TITLE);
        stage.setScene(new Scene(root, 1200, 900));
        stage.show();

        updateTimeLabel();
        updateButtons();
    }

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

    private VBox buildControls() {
        tempo.setPrefWidth(140);
        Label tempoValue = new Label();
        tempoValue.textProperty().bind(tempo.valueProperty().asString("%.0f BPM"));
        HBox.setHgrow(position, Priority.ALWAYS);
        position.setMaxWidth(Double.MAX_VALUE);

        ToolBar transport = new ToolBar(
                playButton, pauseButton, stopButton,
                new Separator(),
                new Label("Tempo:"), tempo, tempoValue,
                loop,
                new Separator(),
                new Label("Time:"), timeLabel);
        HBox scrub = new HBox(8, new Label("Position:"), position);
        scrub.setPadding(new Insets(4, 8, 4, 8));

        CheckBox cursorVisible = new CheckBox("Cursor");
        cursorVisible.setSelected(flyOver.cursorVisibleProperty().get());
        flyOver.cursorVisibleProperty().bind(cursorVisible.selectedProperty());

        ToolBar camera = new ToolBar(
                new Label("Pitch:"), slider(flyOver.cameraPitchProperty(), 0, 89, "%.0f°"),
                new Label("Yaw:"), slider(flyOver.cameraYawProperty(), -89, 89, "%.0f°"),
                new Label("Distance:"), slider(flyOver.cameraDistanceProperty(), 0.5, 8, "%.1f"),
                new Label("Look ahead:"), slider(flyOver.lookAheadProperty(), -2, 3, "%.1f"));
        ToolBar scene = new ToolBar(
                new Label("Background:"), colorPicker(flyOver.backgroundColorProperty()),
                new Label("Paper:"), colorPicker(flyOver.paperColorProperty()),
                new Label("Ink:"), colorPicker(flyOver.inkColorProperty()),
                new Separator(),
                new Label("Light:"), colorPicker(flyOver.lightColorProperty()),
                slider(flyOver.lightRadiusProperty(), 0, 4, "%.1f"),
                new Label("ahead:"), slider(flyOver.lightAheadProperty(), -2, 4, "%.1f"),
                new Label("Fade:"), slider(flyOver.distanceFadeProperty(), 0, 1, "%.2f"));
        ToolBar notes = new ToolBar(
                new Label("Staff colours:"), noteColorPicker(0), noteColorPicker(1), noteColorPicker(2),
                new Label("Glow:"), slider(flyOver.noteGlowProperty(), 0, 2, "%.1f"),
                new Label("Jump:"), slider(flyOver.jumpHeightProperty(), 0, 8, "%.1f"),
                new Label("Ball:"), slider(flyOver.ballSizeProperty(), 0, 6, "%.1f"),
                cursorVisible,
                new Separator(),
                new Label("Spacing:"), slider(flyOver.stripSpacingFactorProperty(), 1, 6, "%.1f", true),
                new Label("Texture:"), slider(flyOver.textureScaleProperty(), 1, 4, "%.1fx", true));

        statusLabel.setPadding(new Insets(4, 8, 6, 8));
        return new VBox(transport, scrub, camera, scene, notes, statusLabel);
    }

    private static ColorPicker colorPicker(ObjectProperty<Color> property) {
        ColorPicker picker = new ColorPicker(property.get());
        property.bind(picker.valueProperty());
        return picker;
    }

    /**
     * A picker editing entry {@code index} of the view's per-staff note colours.
     */
    private ColorPicker noteColorPicker(int index) {
        ColorPicker picker = new ColorPicker(flyOver.noteColors().get(index));
        picker.valueProperty().addListener((obs, o, n) -> flyOver.noteColors().set(index, n));
        return picker;
    }

    // ------------------------------------------------------------------
    // File loading
    // ------------------------------------------------------------------

    private void chooseAndOpen() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Open score");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Score files",
                ScoreFile.formats().stream().flatMap(f -> Ui.globs(f).stream()).toList()));
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
     * Load a score on a background thread, then show it.
     */
    private void openFile(Path path) {
        stopPlayback();
        statusLabel.setText("Loading " + path.getFileName() + " ...");
        Task<Score> task = new Task<>() {
            @Override
            protected Score call() {
                return ScoreFile.load(path);
            }
        };
        task.setOnSucceeded(e -> showScore(path, task.getValue()));
        task.setOnFailed(e -> {
            Throwable ex = task.getException();
            logger.error("Failed to open score: {}", path, ex);
            statusLabel.setText("Failed to open " + path.getFileName() + ": "
                    + (ex != null ? ex.getMessage() : "unknown error"));
            updateButtons();
        });
        Thread thread = new Thread(task, "sheetmusic4j-score-loader");
        thread.setDaemon(true);
        thread.start();
    }

    private void showScore(Path path, Score score) {
        try {
            flyOver.setScore(score);
            flyOver.setCursorTime(0);
            setPosition(0);
            stage.setTitle(TITLE + " - " + path.getFileName());
            statusLabel.setText(String.format(Locale.ROOT, "Loaded %s - %.1f quarter notes. Press Play.",
                    path.getFileName(), flyOver.totalDurationQuarters()));
        } catch (RuntimeException ex) {
            logger.error("Failed to open score: {}", path, ex);
            statusLabel.setText("Failed to open " + path.getFileName() + ": " + ex.getMessage());
        }
        updateTimeLabel();
        updateButtons();
    }

    // ------------------------------------------------------------------
    // Play simulation
    // ------------------------------------------------------------------

    private void play() {
        if (flyOver.totalDurationQuarters() <= 0) {
            statusLabel.setText("Open a score with notes first.");
            return;
        }
        if (flyOver.getCursorTime() >= flyOver.totalDurationQuarters()) {
            flyOver.setCursorTime(0);
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
        flyOver.setCursorTime(0);
        setPosition(0);
        updateTimeLabel();
        updateButtons();
    }

    private void seek(double fraction) {
        flyOver.setCursorTime(fraction * flyOver.totalDurationQuarters());
        updateTimeLabel();
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

        double total = flyOver.totalDurationQuarters();
        double playhead = flyOver.getCursorTime() + tempo.getValue() / 60.0 * dt;
        if (playhead >= total) {
            if (loop.isSelected() && total > 0) {
                playhead %= total;
            } else {
                playhead = total;
                playing = false;
                timer.stop();
                statusLabel.setText("Finished.");
                updateButtons();
            }
        }
        flyOver.setCursorTime(playhead);
        setPosition(total > 0 ? playhead / total : 0);
        updateTimeLabel();
    }

    private void setPosition(double fraction) {
        advancing = true;
        try {
            position.setValue(fraction);
        } finally {
            advancing = false;
        }
    }

    private void updateTimeLabel() {
        timeLabel.setText(String.format(Locale.ROOT, "%.1f / %.1f",
                flyOver.getCursorTime(), flyOver.totalDurationQuarters()));
    }

    private void updateButtons() {
        boolean hasScore = flyOver.getScore() != null && flyOver.totalDurationQuarters() > 0;
        playButton.setDisable(!hasScore || playing);
        pauseButton.setDisable(!playing);
        stopButton.setDisable(!hasScore);
    }
}
