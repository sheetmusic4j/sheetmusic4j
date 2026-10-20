package com.sheetmusic4j.fxviewer;

import com.sheetmusic4j.core.model.Accidental;
import com.sheetmusic4j.core.model.MusicElement;
import com.sheetmusic4j.core.model.Score;
import com.sheetmusic4j.engraving.Engraver;
import com.sheetmusic4j.engraving.layout.LayoutMode;
import com.sheetmusic4j.engraving.layout.LayoutOptions;
import com.sheetmusic4j.engraving.layout.LayoutResult;
import com.sheetmusic4j.engraving.layout.PlaybackTimeline;
import com.sheetmusic4j.render.NoteBackgroundStyle;
import javafx.beans.property.*;
import javafx.collections.ObservableMap;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.Background;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;


/**
 * One-line "strip" score view designed for play-along use. The score is
 * engraved in {@link LayoutMode#STRIP} mode into one endless system; a
 * fixed vertical cursor line sits at
 * {@link #cursorScreenPositionProperty() cursorScreenPosition} of the
 * viewport width, and the score underneath is translated so the
 * musical time given by {@link #cursorTimeProperty()} sits exactly under
 * that cursor. Time maps to x through the layout's {@link PlaybackTimeline},
 * so the cursor passes through each notehead at its onset.
 *
 * <p>The score is drawn in {@link LayoutTiles tiles} of at most
 * {@value #TILE_PIXELS} pixels wide, and only the tiles around the visible
 * part exist, so a strip of any length stays within GPU texture limits and
 * a few megabytes. Advancing the cursor time is a cheap translation plus,
 * every tile width, painting one new tile - no re-engrave - which is
 * important for smooth playback. Highlight changes go through the same
 * {@link #noteHighlights()} map as {@link SheetView}.
 *
 * <p>Setting {@link #revealTimeProperty() revealTime} shows only the music
 * that has started by then, so the strip fills in note by note while it
 * plays; only the tiles around a newly revealed note are repainted.
 */
public final class StripSheetView extends Region {

    private static final double FALLBACK_HEIGHT = LayoutOptions.defaults().staffHeight()
            + LayoutOptions.defaults().topMargin() * 2;

    /** Width of one tile in pixels, well below every GPU's texture limit. */
    private static final double TILE_PIXELS = 2048;

    /** Tallest tile in pixels; a strip taller than this (a huge score, zoomed in) is cut off at the bottom. */
    private static final double MAX_TILE_HEIGHT = 16384;

    private final LayoutTiles tiles = new LayoutTiles(this::paintTile);
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

    private final DoubleProperty stripSpacingFactor =
            new SimpleDoubleProperty(this, "stripSpacingFactor", LayoutOptions.DEFAULT_STRIP_SPACING_FACTOR);

    private final ObjectProperty<NoteBackgroundStyle> noteBackgroundStyle =
            new SimpleObjectProperty<>(this, "noteBackgroundStyle", NoteBackgroundStyle.defaults());

    private final DoubleProperty revealTime =
            new SimpleDoubleProperty(this, "revealTime", Double.POSITIVE_INFINITY);

    private final ObjectProperty<Color> pageColor =
            new SimpleObjectProperty<>(this, "pageColor", Color.WHITE);

    private final ObjectProperty<Color> inkColor =
            new SimpleObjectProperty<>(this, "inkColor", Color.BLACK);

    private final NoteDecorations decorations = new NoteDecorations(renderer.painter(), this::repaint);

    private final RevealTracker reveal = new RevealTracker(renderer.painter());

    private Score score;
    private LayoutResult layout;
    private PlaybackTimeline timeline;

    /**
     * When set (not NaN), the cursor is pinned to this explicit x in layout
     * (pre-zoom) units instead of the {@link PlaybackTimeline#cursorX}
     * placement. Cleared by {@link #setCursorTime}.
     */
    private double cursorLayoutX = Double.NaN;

    /**
     * Creates an empty strip view.
     */
    public StripSheetView() {
        setPadding(new Insets(4));
        getChildren().addAll(tiles.group(), cursor);
        setClip(clip);
        cursor.setStrokeWidth(2.0);
        cursor.strokeProperty().bind(cursorColor);
        cursor.visibleProperty().bind(cursorVisible);
        renderer.painter().setNoteBackgroundStyle(noteBackgroundStyle.get());

        cursorTime.addListener((obs, o, n) -> updateCursor());
        cursorScreenPosition.addListener((obs, o, n) -> updateCursor());
        zoom.addListener((obs, o, n) -> rebuild());
        stripSpacingFactor.addListener((obs, o, n) -> rebuild());
        noteBackgroundStyle.addListener((obs, o, n) -> {
            renderer.painter().setNoteBackgroundStyle(n);
            repaint();
        });
        revealTime.addListener((obs, o, n) -> updateReveal());
        pageColor.addListener((obs, o, n) -> applyColors());
        inkColor.addListener((obs, o, n) -> applyColors());
        applyColors();

        widthProperty().addListener((obs, o, n) -> {
            clip.setWidth(n.doubleValue());
            updateCursor();
        });
        heightProperty().addListener((obs, o, n) -> {
            clip.setHeight(n.doubleValue());
            cursor.setEndY(n.doubleValue());
        });
        setMinSize(200, 60);
        setPrefSize(400, FALLBACK_HEIGHT);
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
     * The playback timeline of the last engraved layout (which notes sound
     * when, and where the cursor sits), or {@code null} if no score has been
     * set.
     */
    public PlaybackTimeline getTimeline() {
        return timeline;
    }

    /**
     * Musical time (in quarter notes from the start of the score) at which
     * the fixed cursor sits. Mutations only update a translation (and paint
     * one new tile each time a tile width scrolls into view) - no re-engrave
     * - so this is safe to animate at 60 fps.
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
     * overriding the {@link PlaybackTimeline#cursorX} placement until the
     * next {@link #setCursorTime} call.
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
        return decorations.highlights();
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
        return decorations.backgrounds();
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
        return decorations.accidentals();
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

    /**
     * Uniform horizontal spacing scale for the time-proportional STRIP layout
     * (see {@link LayoutOptions#stripSpacingFactor()}). Larger values spread
     * notes further apart; changing it re-engraves the score, so note anchor
     * positions change and any cached timeline should be rebuilt from
     * {@link #getLayout()}.
     */
    public DoubleProperty stripSpacingFactorProperty() {
        return stripSpacingFactor;
    }

    public double getStripSpacingFactor() {
        return stripSpacingFactor.get();
    }

    public void setStripSpacingFactor(double factor) {
        if (factor > 0) {
            stripSpacingFactor.set(factor);
        }
    }

    /**
     * Musical time (quarter notes) up to which the music is shown: notes,
     * chords and rests starting later are left out, so the strip can be
     * filled in note by note during playback (see
     * {@code ScorePainter#setRevealTime}). Positive infinity, the default,
     * shows everything. Usually bound to, or set together with,
     * {@link #cursorTimeProperty()}; changing it only repaints the tiles
     * around elements that appear or disappear.
     */
    public DoubleProperty revealTimeProperty() {
        return revealTime;
    }

    public double getRevealTime() {
        return revealTime.get();
    }

    public void setRevealTime(double quarters) {
        revealTime.set(quarters);
    }

    /**
     * Colour of the paper behind the music; also fills the parts of the view
     * the strip does not reach. Defaults to white.
     */
    public ObjectProperty<Color> pageColorProperty() {
        return pageColor;
    }

    /**
     * Colour of the engraved ink (staff lines, notes, text). Highlighted
     * notes keep their own colour. Defaults to black.
     */
    public ObjectProperty<Color> inkColorProperty() {
        return inkColor;
    }

    private void applyColors() {
        Color page = pageColor.get();
        renderer.painter().setPageColor(NoteDecorations.toRenderColor(page).orElse(null));
        renderer.painter().setInkColor(NoteDecorations.toRenderColor(inkColor.get()).orElse(null));
        setBackground(page == null ? Background.EMPTY : Background.fill(page));
        repaint();
    }

    private void updateReveal() {
        double[] dirty = reveal.apply(timeline, revealTime.get());
        if (dirty == RevealTracker.ALL) {
            repaint();
        } else if (dirty != null && layout != null) {
            tiles.repaint(dirty[0], dirty[1]);
        }
    }

    private void rebuild() {
        double zoomFactor = Math.max(zoom.get(), 0.01);
        if (score == null) {
            layout = null;
            timeline = null;
            tiles.reset(0, 1);
            setPrefHeight(FALLBACK_HEIGHT * zoomFactor);
            updateCursor();
            return;
        }
        LayoutOptions options = LayoutOptions.defaults().toBuilder()
                .layoutMode(LayoutMode.STRIP)
                .showTitleTexts(false)
                .stripSpacingFactor(stripSpacingFactor.get())
                .build();
        layout = engraver.layout(score, options);
        timeline = PlaybackTimeline.of(layout);
        reveal.apply(timeline, revealTime.get());
        tiles.reset(layout.width(), TILE_PIXELS / zoomFactor);
        setPrefHeight(Math.max(layout.height() * zoomFactor, 1.0));
        updateCursor();
    }

    private void repaint() {
        if (layout == null) {
            return;
        }
        tiles.repaint();
    }

    /** One tile: a canvas covering {@code [x0, x0 + width)} of the layout, placed at its zoomed x. */
    private Node paintTile(double x0, double width) {
        double zoomFactor = Math.max(zoom.get(), 0.01);
        Canvas canvas = new Canvas(Math.ceil(width * zoomFactor),
                Math.min(Math.ceil(layout.height() * zoomFactor), MAX_TILE_HEIGHT));
        canvas.setLayoutX(x0 * zoomFactor);
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.scale(zoomFactor, zoomFactor);
        gc.translate(-x0, 0);
        renderer.renderRegion(gc, layout, x0, 0, x0 + width, layout.height());
        return canvas;
    }

    private void updateCursor() {
        double viewportWidth = getWidth();
        double cursorScreenX = viewportWidth * clamp01(cursorScreenPosition.get());
        cursor.setStartX(cursorScreenX);
        cursor.setEndX(cursorScreenX);
        cursor.setStartY(0);
        cursor.setEndY(getHeight());
        if (layout == null) {
            tiles.group().setLayoutX(0);
            return;
        }
        double zoomFactor = Math.max(zoom.get(), 0.01);
        double layoutX = Double.isNaN(cursorLayoutX) ? timeline.cursorX(cursorTime.get()) : cursorLayoutX;
        double offset = cursorScreenX - layoutX * zoomFactor;
        tiles.group().setLayoutX(offset);
        tiles.show(-offset / zoomFactor, (viewportWidth - offset) / zoomFactor);
    }

    @Override
    protected void layoutChildren() {
        // Cursor is positioned via its own start/end coords, and the tiles via
        // the group's layoutX. Nothing to lay out here.
    }
}
