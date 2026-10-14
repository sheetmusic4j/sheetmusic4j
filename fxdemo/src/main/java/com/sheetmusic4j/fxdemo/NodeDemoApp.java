package com.sheetmusic4j.fxdemo;

import java.nio.file.Path;
import java.util.List;

import com.sheetmusic4j.core.io.ScoreFile;
import com.sheetmusic4j.fxdemo.ui.ScoreLoader;
import com.sheetmusic4j.core.io.ScoreFormat;
import com.sheetmusic4j.core.model.MusicElement;
import com.sheetmusic4j.core.model.Note;
import com.sheetmusic4j.core.model.Pitch;
import com.sheetmusic4j.core.model.Step;
import com.sheetmusic4j.engraving.layout.LayoutOptions;
import com.sheetmusic4j.engraving.layout.NoteAnchor;
import com.sheetmusic4j.core.model.Score;
import com.sheetmusic4j.fxdemo.ui.Ui;
import com.sheetmusic4j.fxviewer.NodeSheetView;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.control.ToolBar;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.BorderPane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Standalone JavaFX demo for the retained-mode {@link NodeSheetView}.
 *
 * <p>Every note, rest and chord is its own scene graph node, so the demo can work
 * with them individually: hovering and clicking selects an element (shown in the
 * status bar), <em>Delete</em> hides the selected element's node, and
 * <em>Restore all</em> brings the hidden ones back and puts moved ones in place. Dragging a note with the mouse
 * snaps it to staff positions (Shift = sharp, Alt = flat); other elements move freely. Only the element's node
 * moves; the staff, beams and ties stay where they are. Open a MusicXML / MIDI / ABC
 * score via <em>File &rarr; Open</em> (or pass a path as the first program argument).
 *
 * <p>Run with {@code mvn -pl fxdemo -Pnode javafx:run}.
 */
public final class NodeDemoApp extends Application {

    private final ScoreLoader loader = new ScoreLoader();

    private static final Logger logger = LoggerFactory.getLogger(NodeDemoApp.class);

    private static final String TITLE = "Sheetmusic4J Node View Demo";

    private final NodeSheetView view = new NodeSheetView();
    private final Slider zoom = new Slider(Ui.ZOOM_MIN, Ui.ZOOM_MAX, 1.0);
    private final Label zoomLabel = new Label(Ui.zoomText(1.0));
    private final Button hideButton = new Button("Hide selected");
    private final Button restoreButton = new Button("Restore all");
    private final Label hoverLabel = new Label("");
    private final Label statusLabel = new Label("Open a score to begin (File → Open).");

    private final List<MusicElement> hidden = new java.util.ArrayList<>();
    private final java.util.Set<MusicElement> moved =
            java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());

    // State of the drag in progress.
    private MusicElement dragged;
    private Point2D dragStart;
    private double dragStartTx;
    private double dragStartTy;
    private NoteAnchor dragAnchor;
    private Text accidentalOverlay;
    /** Accidentals chosen by dragging; they stay next to the moved note until restored. */
    private final java.util.Map<MusicElement, Text> overlays = new java.util.IdentityHashMap<>();
    private Stage stage;

    /**
     * Launches the demo.
     *
     * @param args optional path of a score to open
     */
    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        this.stage = primaryStage;

        MenuItem open = new MenuItem("Open...");
        open.setAccelerator(new KeyCodeCombination(KeyCode.O, KeyCombination.SHORTCUT_DOWN));
        open.setOnAction(e -> chooseAndOpen());
        MenuItem quit = new MenuItem("Quit");
        quit.setOnAction(e -> stage.close());
        MenuBar menuBar = new MenuBar(new Menu("File", null, open, quit));

        zoom.setPrefWidth(180);
        zoom.valueProperty().addListener((obs, o, n) -> {
            view.zoomProperty().set(n.doubleValue());
            zoomLabel.setText(Ui.zoomText(n.doubleValue()));
        });
        hideButton.setOnAction(e -> hideSelected());
        hideButton.setTooltip(new Tooltip("Hide the selected note (Delete)"));
        restoreButton.setTooltip(new Tooltip("Show hidden notes again and put moved notes (and their \u266F/\u266D) back"));
        hideButton.setDisable(true);
        restoreButton.setOnAction(e -> restoreAll());
        restoreButton.setDisable(true);
        ToolBar toolBar = new ToolBar(new Label("Zoom"), zoom, zoomLabel,
                new javafx.scene.control.Separator(), hideButton, restoreButton);

        view.selectedElementProperty().addListener((obs, o, n) -> {
            hideButton.setDisable(n == null);
            statusLabel.setText(n == null ? "Nothing selected." : "Selected: " + n);
        });
        view.hoveredElementProperty().addListener((obs, o, n) ->
                hoverLabel.setText(n == null ? "" : "Hover: " + n));
        view.setOnElementClicked((element, event) ->
                logger.info("Clicked {} (click count {})", element, event.getClickCount()));

        view.addEventHandler(MouseEvent.MOUSE_PRESSED, this::startDrag);
        view.addEventHandler(MouseEvent.MOUSE_DRAGGED, this::drag);
        view.addEventHandler(MouseEvent.MOUSE_RELEASED, e -> endDrag());
        view.addEventHandler(MouseEvent.MOUSE_MOVED, e ->
                view.setCursor(view.pick(e) != null ? Cursor.MOVE : Cursor.DEFAULT));

        Label help = new Label("Drag a note up or down to change its pitch.  "
                + "Hold Shift while dragging for a sharp (\u266F), Alt/Option for a flat (\u266D).  "
                + "Delete hides the selected note, Esc deselects.");
        help.setWrapText(true);
        help.setPadding(new Insets(6, 8, 6, 8));
        help.setStyle("-fx-background-color: #eef4fb; -fx-border-color: #c5d6ea; -fx-border-width: 0 0 1 0;");
        help.setMaxWidth(Double.MAX_VALUE);

        ScrollPane scroll = new ScrollPane(view);
        scroll.setFitToWidth(false);
        scroll.setPannable(false);

        BorderPane status = new BorderPane();
        status.setPadding(new Insets(4, 8, 4, 8));
        status.setLeft(statusLabel);
        status.setRight(hoverLabel);
        BorderPane.setAlignment(statusLabel, Pos.CENTER_LEFT);

        BorderPane root = new BorderPane(scroll);
        root.setTop(new javafx.scene.layout.VBox(menuBar, toolBar, help));
        root.setBottom(status);

        Scene scene = new Scene(root, 1100, 800);
        scene.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.DELETE || e.getCode() == KeyCode.BACK_SPACE) {
                hideSelected();
            } else if (e.getCode() == KeyCode.ESCAPE) {
                view.selectedElementProperty().set(null);
            }
        });
        stage.setTitle(TITLE);
        stage.setScene(scene);
        stage.show();

        List<String> args = getParameters().getRaw();
        if (!args.isEmpty()) {
            openFile(Path.of(args.getFirst()));
        }
    }

    /** Pressing on an element selects it and starts moving its node. */
    private void startDrag(MouseEvent e) {
        dragged = view.pick(e);
        Group node = dragged == null ? null : view.nodeFor(dragged);
        if (node == null) {
            dragged = null;
            return;
        }
        view.selectedElementProperty().set(dragged);
        dragStart = view.sceneToLayout(e.getSceneX(), e.getSceneY());
        dragStartTx = node.getTranslateX();
        dragStartTy = node.getTranslateY();
        dragAnchor = view.getLayout().noteAnchors().stream()
                .filter(a -> a.elementRef() == dragged).findFirst().orElse(null);
        e.consume();
    }

    /**
     * Moves the pressed element's node by the mouse delta, in layout units. A
     * note snaps to staff positions (half a staff space per step) and stays in
     * its column; hold Shift for a sharp or Alt/Option for a flat. Other
     * elements move freely.
     */
    private void drag(MouseEvent e) {
        Group node = dragged == null ? null : view.nodeFor(dragged);
        if (node == null || dragStart == null) {
            return;
        }
        Point2D now = view.sceneToLayout(e.getSceneX(), e.getSceneY());
        double dx = now.getX() - dragStart.getX();
        double dy = now.getY() - dragStart.getY();
        if (dragged instanceof Note note && note.pitch() != null && dragAnchor != null) {
            double halfGap = LayoutOptions.defaults().staffLineGap() / 2.0;
            int steps = (int) Math.round(-dy / halfGap);
            int alter = e.isShiftDown() ? 1 : e.isAltDown() ? -1 : 0;
            node.setTranslateX(dragStartTx);
            node.setTranslateY(dragStartTy - steps * halfGap);
            showSnap(node, snapped(note.pitch(), steps, alter), halfGap * 2);
        } else {
            node.setTranslateX(dragStartTx + dx);
            node.setTranslateY(dragStartTy + dy);
        }
        if (moved.add(dragged)) {
            restoreButton.setDisable(false);
        }
        e.consume();
    }

    /** The pitch {@code steps} staff positions above {@code from}, with the given accidental. */
    private static Pitch snapped(Pitch from, int steps, int alter) {
        int number = from.diatonicStepNumber() + steps;
        return new Pitch(Step.values()[Math.floorMod(number, 7)], Math.floorDiv(number, 7), alter);
    }

    /** Shows the snapped pitch in the status bar and its accidental left of the notehead. */
    private void showSnap(Group node, Pitch pitch, double gap) {
        String symbol = switch (pitch.alter()) {
            case 1 -> "\u266F";
            case -1 -> "\u266D";
            default -> "";
        };
        statusLabel.setText("Moving to " + pitch.step() + symbol + pitch.octave()
                + "  (Shift = sharp, Alt = flat)");
        if (accidentalOverlay == null) {
            accidentalOverlay = overlays.computeIfAbsent(dragged, key -> {
                Text text = new Text();
                text.setMouseTransparent(true);
                text.setFill(Color.DODGERBLUE);
                node.getChildren().add(text);
                return text;
            });
        }
        accidentalOverlay.setFont(Font.font(gap * 2.2));
        accidentalOverlay.setText(symbol);
        accidentalOverlay.setX(dragAnchor.x() - gap * 1.8);
        accidentalOverlay.setY(dragAnchor.y() + gap * 0.3);
    }

    private void endDrag() {
        accidentalOverlay = null;
        dragAnchor = null;
        dragged = null;
        dragStart = null;
    }

    private void hideSelected() {
        MusicElement element = view.selectedElementProperty().get();
        Group node = element == null ? null : view.nodeFor(element);
        if (node != null) {
            node.setVisible(false);
            hidden.add(element);
            restoreButton.setDisable(false);
            view.selectedElementProperty().set(null);
        }
    }

    private void restoreAll() {
        for (MusicElement element : hidden) {
            Group node = view.nodeFor(element);
            if (node != null) {
                node.setVisible(true);
            }
        }
        hidden.clear();
        for (MusicElement element : moved) {
            Group node = view.nodeFor(element);
            if (node != null) {
                node.setTranslateX(0);
                node.setTranslateY(0);
            }
        }
        moved.clear();
        overlays.values().forEach(text -> {
            if (text.getParent() instanceof Group parent) {
                parent.getChildren().remove(text);
            }
        });
        overlays.clear();
        restoreButton.setDisable(true);
    }

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

    /** Loads a score on a background thread, then shows it. */
    private void openFile(Path path) {
        statusLabel.setText("Loading " + path.getFileName() + " ...");
        loader.load(path, score -> {
            hidden.clear();
            moved.clear();
            overlays.clear();
            restoreButton.setDisable(true);
            view.selectedElementProperty().set(null);
            view.setScore(score);
            stage.setTitle(TITLE + " - " + path.getFileName());
            statusLabel.setText("Loaded " + path.getFileName() + ". Click to select, drag to move; Shift = sharp, Alt = flat.");
        }, ex -> {
            logger.error("Failed to open score: {}", path, ex);
            statusLabel.setText("Failed to open " + path.getFileName() + ": "
                    + (ex != null ? ex.getMessage() : "unknown error"));
        });
    }
}
