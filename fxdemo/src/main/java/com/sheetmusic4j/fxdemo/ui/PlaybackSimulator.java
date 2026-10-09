package com.sheetmusic4j.fxdemo.ui;

import java.util.List;

import com.sheetmusic4j.core.model.MusicElement;
import com.sheetmusic4j.engraving.layout.LayoutResult;
import com.sheetmusic4j.engraving.layout.NoteAnchor;
import com.sheetmusic4j.fxviewer.SheetView;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.scene.paint.Color;
import javafx.util.Duration;

/**
 * Cheap playback simulator: iterates through the current layout's note
 * anchors at a fixed cadence and highlights each one - both a yellow
 * translucent background (via {@link SheetView#noteBackgrounds()}) and a red
 * foreground tint (via {@link SheetView#noteHighlights()}) - so the two-map
 * API is exercised in the demo.
 */
public final class PlaybackSimulator {

    private static final Duration CADENCE = Duration.millis(350);
    private static final Color TINT = Color.CRIMSON;
    private static final Color BACKGROUND = Color.color(1.0, 0.92, 0.23, 0.5);

    private final SheetView sheetView;
    private Timeline timeline;
    private int index;

    public PlaybackSimulator(SheetView sheetView) {
        this.sheetView = sheetView;
    }

    /**
     * Start highlighting the view's current layout.
     *
     * @return {@code false} when there is nothing to highlight
     */
    public boolean start() {
        stop();
        LayoutResult layout = sheetView.getLayout();
        if (layout == null || layout.noteAnchors().isEmpty()) {
            return false;
        }
        List<NoteAnchor> anchors = layout.noteAnchors();
        index = 0;
        timeline = new Timeline(new KeyFrame(CADENCE, evt -> {
            MusicElement previous = index > 0
                    ? anchors.get((index - 1) % anchors.size()).elementRef()
                    : anchors.get(anchors.size() - 1).elementRef();
            sheetView.noteHighlights().remove(previous);
            sheetView.noteBackgrounds().remove(previous);
            MusicElement current = anchors.get(index % anchors.size()).elementRef();
            sheetView.noteHighlights().put(current, TINT);
            sheetView.noteBackgrounds().put(current, BACKGROUND);
            index++;
        }));
        timeline.setCycleCount(Animation.INDEFINITE);
        timeline.play();
        return true;
    }

    /**
     * Stop and clear every highlight.
     */
    public void stop() {
        if (timeline != null) {
            timeline.stop();
            timeline = null;
        }
        sheetView.noteHighlights().clear();
        sheetView.noteBackgrounds().clear();
    }
}
