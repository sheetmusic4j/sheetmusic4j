package com.sheetmusic4j.fxviewer;

import com.sheetmusic4j.core.model.Accidental;
import com.sheetmusic4j.core.model.MusicElement;
import com.sheetmusic4j.core.model.Score;
import com.sheetmusic4j.engraving.Engraver;
import com.sheetmusic4j.engraving.layout.LayoutMode;
import com.sheetmusic4j.engraving.layout.LayoutOptions;
import com.sheetmusic4j.engraving.layout.LayoutResult;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.MapChangeListener;
import javafx.collections.ObservableMap;
import javafx.geometry.Insets;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;

import java.util.IdentityHashMap;
import java.util.Optional;

/**
 * One-line "strip" score view designed for play-along use. The score is
 * engraved in {@link LayoutMode#STRIP} mode into one endless system; a
 * fixed vertical cursor line sits at
 * {@link #cursorScreenPositionProperty() cursorScreenPosition} of the
 * viewport width, and the score canvas underneath is translated so the
 * musical time given by {@link #cursorTimeProperty()} sits exactly under
 * that cursor.
 *
 * <p>Advancing the cursor time is therefore a cheap {@code translateX}
 * update - no re-engrave, no repaint - which is important for smooth
 * playback. Highlight changes go through the same
 * {@link #noteHighlights()} map as {@link SheetView}.
 */
public final class StripSheetView extends Region {

    private static final double FALLBACK_HEIGHT = LayoutOptions.defaults().staffHeight()
            + LayoutOptions.defaults().topMargin() * 2;

    private final Canvas canvas = new Canvas();
    private final Line cursor = new Line();
    private final Rectangle clip = new Rectangle();
    private final Engraver engraver = new Engraver();
    private final ScoreRenderer renderer = new ScoreRenderer();

    private final DoubleProperty cursorTime =
            new SimpleDoubleProperty(this, "cursorTime", 0.0);

    private final DoubleProperty cursorScreenPosition =
            new SimpleDoubleProperty(this, "cursorScreenPosition", 0.3);

    private final BooleanProperty cursorVisible =
            new SimpleBooleanProperty(this, "cursorVisible", true);

    private final ObjectProperty<Color> cursorColor =
            new SimpleObjectProperty<>(this, "cursorColor", Color.CRIMSON);

    private final DoubleProperty zoom = new SimpleDoubleProperty(this, "zoom", 1.0);

    private final ObservableMap<MusicElement, Color> noteHighlights =
            FXCollections.observableMap(new IdentityHashMap<>());

    private final ObservableMap<MusicElement, Color> noteBackgrounds =
            FXCollections.observableMap(new IdentityHashMap<>());

    private final ObservableMap<MusicElement, Accidental> noteAccidentals =
            FXCollections.observableMap(new IdentityHashMap<>());

    private final ObjectProperty<NoteBackgroundStyle> noteBackgroundStyle =
            new SimpleObjectProperty<>(this, "noteBackgroundStyle", NoteBackgroundStyle.defaults());

    private Score score;
    private LayoutResult layout;

    /**
     * When set (not NaN), the cursor is pinned to this explicit x in layout
     * (pre-zoom) units instead of the time-based {@link LayoutResult#xAtTime}
     * placement. Cleared by {@link #setCursorTime}.
     */
    private double cursorLayoutX = Double.NaN;

    /**
     * Creates an empty strip view.
     */
    public StripSheetView() {
        setPadding(new Insets(4));
        getChildren().addAll(canvas, cursor);
        setClip(clip);
        cursor.setStrokeWidth(2.0);
        cursor.strokeProperty().bind(cursorColor);
        cursor.visibleProperty().bind(cursorVisible);
        renderer.setNoteColorProvider(this::highlightFor);
        renderer.setNoteBackgroundProvider(this::backgroundFor);
        renderer.setNoteAccidentalProvider(this::accidentalFor);
        renderer.setNoteBackgroundStyle(noteBackgroundStyle.get());

        cursorTime.addListener((obs, o, n) -> updateCursor());
        cursorScreenPosition.addListener((obs, o, n) -> updateCursor());
        zoom.addListener((obs, o, n) -> rebuild());
        noteHighlights.addListener((MapChangeListener<MusicElement, Color>) c -> repaint());
        noteBackgrounds.addListener((MapChangeListener<MusicElement, Color>) c -> repaint());
        noteAccidentals.addListener((MapChangeListener<MusicElement, Accidental>) c -> repaint());
        noteBackgroundStyle.addListener((obs, o, n) -> {
            renderer.setNoteBackgroundStyle(n);
            repaint();
        });

        widthProperty().addListener((obs, o, n) -> {
            clip.setWidth(n.doubleValue());
            updateCursor();
        });
        heightProperty().addListener((obs, o, n) -> {
            clip.setHeight(n.doubleValue());
            cursor.setEndY(n.doubleValue());
        });
        canvas.setWidth(400);
        canvas.setHeight(FALLBACK_HEIGHT);
        setMinSize(200, 60);
        setPrefSize(400, FALLBACK_HEIGHT);
    }

    private static Optional<RenderColor> toRenderColor(Color c) {
        if (c == null) {
            return Optional.empty();
        }
        return Optional.of(new RenderColor(
                (int) Math.round(c.getRed() * 255),
                (int) Math.round(c.getGreen() * 255),
                (int) Math.round(c.getBlue() * 255),
                (int) Math.round(c.getOpacity() * 255)));
    }

    private static double clamp01(double v) {
        if (v < 0) {
            return 0;
        }
        if (v > 1) {
            return 1;
        }
        return v;
    }

    /**
     * @return the currently displayed score, or {@code null}.
     */
    public Score getScore() {
        return score;
    }

    /**
     * Set the score to display. The score is engraved in
     * {@link LayoutMode#STRIP} mode; other options are taken from
     * {@link LayoutOptions#defaults()}. The cursor time is preserved.
     */
    public void setScore(Score score) {
        this.score = score;
        rebuild();
    }

    /**
     * The last engraved layout, or {@code null} if no score has been set.
     * Exposed so callers can inspect {@link LayoutResult#noteAnchors()}.
     */
    public LayoutResult getLayout() {
        return layout;
    }

    /**
     * Musical time (in quarter notes from the start of the score) at which
     * the fixed cursor sits. Mutations only update a canvas translation -
     * no re-engrave or repaint - so this is safe to animate at 60 fps.
     */
    public DoubleProperty cursorTimeProperty() {
        return cursorTime;
    }

    public double getCursorTime() {
        return cursorTime.get();
    }

    public void setCursorTime(double quarters) {
        cursorLayoutX = Double.NaN;
        cursorTime.set(quarters);
        updateCursor();
    }

    /**
     * Pin the cursor to an explicit x position in layout (pre-zoom) units,
     * overriding the time-based {@link LayoutResult#xAtTime} placement until
     * the next {@link #setCursorTime} call. Use this to align the cursor
     * exactly with a note's rendered centre (its
     * {@link com.sheetmusic4j.engraving.layout.NoteAnchor} x) rather than the
     * piecewise-linear time interpolation, which spaces time evenly across a
     * measure and so does not land on individual noteheads.
     *
     * @param layoutX the x coordinate in layout units to place under the cursor
     */
    public void setCursorLayoutX(double layoutX) {
        cursorLayoutX = layoutX;
        updateCursor();
    }

    /**
     * Where the fixed cursor line sits as a fraction of the viewport
     * width. Defaults to {@code 0.3} so a third of the viewport shows the
     * upcoming music and two thirds shows the recently played music.
     */
    public DoubleProperty cursorScreenPositionProperty() {
        return cursorScreenPosition;
    }

    public double getCursorScreenPosition() {
        return cursorScreenPosition.get();
    }

    public void setCursorScreenPosition(double fraction) {
        cursorScreenPosition.set(fraction);
    }

    /**
     * Whether the cursor line is drawn.
     */
    public BooleanProperty cursorVisibleProperty() {
        return cursorVisible;
    }

    public boolean isCursorVisible() {
        return cursorVisible.get();
    }

    public void setCursorVisible(boolean visible) {
        cursorVisible.set(visible);
    }

    /**
     * Colour of the cursor line.
     */
    public ObjectProperty<Color> cursorColorProperty() {
        return cursorColor;
    }

    public Color getCursorColor() {
        return cursorColor.get();
    }

    public void setCursorColor(Color color) {
        cursorColor.set(color);
    }

    /**
     * Total musical duration of the currently-engraved score, in quarter
     * notes. Zero when no score has been set.
     */
    public double totalDurationQuarters() {
        return layout == null ? 0.0 : layout.totalDurationQuarters();
    }

    /**
     * Live-observable per-element highlight colours. Identity-comparison
     * on keys; mutations only trigger a canvas repaint.
     */
    public ObservableMap<MusicElement, Color> noteHighlights() {
        return noteHighlights;
    }

    /**
     * Live-observable per-element <em>background</em> colours. Adding a
     * {@code (element, colour)} pair draws a rounded, semi-transparent
     * rectangle behind that element's notehead for a strong "played right
     * now" visual pop. Independent of {@link #noteHighlights()}.
     * Mutations only trigger a canvas repaint - no re-engrave.
     *
     * @return the observable map (never {@code null})
     */
    public ObservableMap<MusicElement, Color> noteBackgrounds() {
        return noteBackgrounds;
    }

    /**
     * Live-observable per-element accidental overlays. Adding a
     * {@code (element, Accidental)} pair draws the corresponding SMuFL
     * accidental glyph to the immediate left of that element's notehead;
     * removing the entry clears it. Independent of
     * {@link #noteHighlights()} and {@link #noteBackgrounds()}; mutations
     * only trigger a canvas repaint - no re-engrave. When the source note
     * already carries an engraved accidental, the overlay value replaces
     * it in-place for as long as the entry is present.
     *
     * @return the observable map (never {@code null})
     */
    public ObservableMap<MusicElement, Accidental> noteAccidentals() {
        return noteAccidentals;
    }

    /**
     * Geometry of the rounded rectangle drawn behind
     * {@link #noteBackgrounds() background}-highlighted notes: how far it
     * extends beyond the note on each side, its corner radius and a height cap.
     * Changing it repaints the strip without a re-engrave. See
     * {@link NoteBackgroundStyle}.
     *
     * @return the observable style property (never {@code null})
     */
    public ObjectProperty<NoteBackgroundStyle> noteBackgroundStyleProperty() {
        return noteBackgroundStyle;
    }

    public NoteBackgroundStyle getNoteBackgroundStyle() {
        return noteBackgroundStyle.get();
    }

    public void setNoteBackgroundStyle(NoteBackgroundStyle style) {
        noteBackgroundStyle.set(style != null ? style : NoteBackgroundStyle.defaults());
    }

    /**
     * Render zoom factor (positive). Values above 1 enlarge the score.
     */
    public DoubleProperty zoomProperty() {
        return zoom;
    }

    public double getZoom() {
        return zoom.get();
    }

    public void setZoom(double factor) {
        if (factor > 0) {
            zoom.set(factor);
        }
    }

    private Optional<RenderColor> highlightFor(MusicElement element) {
        return toRenderColor(noteHighlights.get(element));
    }

    private Optional<RenderColor> backgroundFor(MusicElement element) {
        return toRenderColor(noteBackgrounds.get(element));
    }

    private Optional<Accidental> accidentalFor(MusicElement element) {
        return Optional.ofNullable(noteAccidentals.get(element));
    }

    private void rebuild() {
        double zoomFactor = Math.max(zoom.get(), 0.01);
        if (score == null) {
            layout = null;
            canvas.setWidth(400);
            canvas.setHeight(FALLBACK_HEIGHT * zoomFactor);
            canvas.getGraphicsContext2D().clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
            updateCursor();
            return;
        }
        LayoutOptions options = LayoutOptions.defaults().toBuilder()
                .layoutMode(LayoutMode.STRIP)
                .showTitleTexts(false)
                .build();
        layout = engraver.layout(score, options);
        canvas.setWidth(Math.max(layout.width() * zoomFactor, 1.0));
        canvas.setHeight(Math.max(layout.height() * zoomFactor, 1.0));
        setPrefHeight(canvas.getHeight());
        paintCurrent();
        updateCursor();
    }

    private void repaint() {
        if (layout == null) {
            return;
        }
        paintCurrent();
    }

    private void paintCurrent() {
        double zoomFactor = Math.max(zoom.get(), 0.01);
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
        gc.save();
        gc.scale(zoomFactor, zoomFactor);
        renderer.render(gc, layout, layout.width(), layout.height());
        gc.restore();
    }

    private void updateCursor() {
        double viewportWidth = getWidth();
        double cursorScreenX = viewportWidth * clamp01(cursorScreenPosition.get());
        cursor.setStartX(cursorScreenX);
        cursor.setEndX(cursorScreenX);
        cursor.setStartY(0);
        cursor.setEndY(getHeight());
        if (layout == null) {
            canvas.setLayoutX(0);
            return;
        }
        double zoomFactor = Math.max(zoom.get(), 0.01);
        double layoutX = Double.isNaN(cursorLayoutX) ? layout.xAtTime(cursorTime.get()) : cursorLayoutX;
        canvas.setLayoutX(cursorScreenX - layoutX * zoomFactor);
    }

    @Override
    protected void layoutChildren() {
        // Cursor is positioned via its own start/end coords, and canvas via
        // its layoutX property. Nothing to lay out here.
    }
}
