package com.sheetmusic4j.fxviewer;

import com.sheetmusic4j.core.model.Accidental;
import com.sheetmusic4j.core.model.MusicElement;
import com.sheetmusic4j.core.model.Score;
import com.sheetmusic4j.engraving.Engraver;
import com.sheetmusic4j.engraving.glyph.MarkingCategory;
import com.sheetmusic4j.engraving.layout.LayoutOptions;
import com.sheetmusic4j.engraving.layout.LayoutResult;
import com.sheetmusic4j.engraving.layout.NoteAnchor;
import com.sheetmusic4j.engraving.layout.SystemLayout;
import com.sheetmusic4j.engraving.placement.BracketPlacement;
import com.sheetmusic4j.engraving.placement.TextPlacement;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.collections.*;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;

import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Optional;

/**
 * A JavaFX control that renders a {@link Score}. It engraves the score into a
 * {@link LayoutResult} via {@link Engraver} and draws it on a single
 * {@link Canvas}.
 *
 * <p>The view is <em>content-sized</em>: after each engrave, the region's
 * preferred/min/max sizes track {@link LayoutResult}'s full width and height
 * (scaled by {@link #zoomProperty()}). That way, when the view is wrapped in
 * a {@code ScrollPane}, the pane sees the real content size and shows
 * scrollbars whenever the content is larger than the viewport.
 *
 * <p>The underlying {@link Canvas} itself, however, is capped at
 * {@link #MAX_CANVAS_DIMENSION} pixels: JavaFX backs every Canvas with a GPU
 * texture, and most backends refuse dimensions beyond roughly 16384px, so a
 * sufficiently tall score (many parts, many systems) would otherwise crash
 * the renderer rather than just failing to display. When the content
 * exceeds that cap, only a <em>window</em> of it - sized to fit the cap - is
 * actually materialized into the Canvas at any one time, positioned to cover
 * whatever {@link #viewportTopProperty()}/{@link #viewportHeightProperty()}
 * currently say is visible. Callers that wrap this view in a
 * {@code ScrollPane} should keep those two properties bound to the pane's
 * scroll position (see the properties' docs) so the window follows
 * scrolling; callers that never do so still get a safe, non-crashing (if
 * only partially visible) render of oversized scores.
 *
 * <p>Callers can override the engraving width via {@link #setSystemWidth(double)}
 * (or the {@link #systemWidthProperty()} JavaFX property, e.g. by binding it to
 * a container's width). The default is {@link LayoutOptions#defaults()}
 * {@code .systemWidth()}. Callers can also scale the rendered result via
 * {@link #setZoom(double)}.
 *
 * <p>For per-note highlighting during playback, mutate the
 * {@link #noteHighlights()} map: adding {@code (element, color)} pairs
 * repaints the view without re-engraving, so highlights are cheap enough to
 * drive from real-time MIDI events.
 */
public final class SheetView extends Region {

    private static final double FALLBACK_HEIGHT = LayoutOptions.defaults().staffHeight()
            + LayoutOptions.defaults().topMargin() * 2;

    /**
     * Safe ceiling for the Canvas's pixel width/height. JavaFX's hardware
     * renderer backs every Canvas with a GPU texture, and most backends cap
     * texture dimensions around 16384px; the Canvas is never asked to grow
     * past this, regardless of how tall the engraved score actually is (see
     * {@link #updateWindow(boolean)}).
     */
    private static final double MAX_CANVAS_DIMENSION = 8000.0;

    private final Canvas canvas = new Canvas();
    private final Engraver engraver = new Engraver();
    private final ScoreRenderer renderer = new ScoreRenderer();

    /**
     * Top edge, in unzoomed layout units, of the score content currently
     * materialized into {@link #canvas}. Only meaningful once a score is
     * loaded; {@link #updateWindow(boolean)} is the sole writer.
     */
    private double windowStartLayoutY = 0;

    private final DoubleProperty systemWidth =
            new SimpleDoubleProperty(this, "systemWidth", LayoutOptions.defaults().systemWidth());

    private final DoubleProperty zoom = new SimpleDoubleProperty(this, "zoom", 1.0);

    /**
     * Top edge of the currently visible viewport, in this view's own local
     * coordinate space (i.e. already scaled by {@link #zoomProperty()} -
     * the same units as {@link #computePrefHeight(double)}). Defaults to 0.
     * Bind this to a wrapping {@code ScrollPane}'s scroll position - e.g.
     * from its {@code vvalueProperty()} combined with its
     * {@code viewportBoundsProperty()} - so this view knows which portion of
     * an oversized score to actually materialize into its Canvas. Changing
     * this never re-engraves; it only repaints, and only when the new
     * viewport falls outside the currently materialized window.
     *
     * @return the writable viewport-top property, in local pixel units
     */
    public DoubleProperty viewportTopProperty() {
        return viewportTop;
    }

    private final DoubleProperty viewportTop = new SimpleDoubleProperty(this, "viewportTop", 0);

    /**
     * Height of the currently visible viewport, in the same local pixel
     * units as {@link #viewportTopProperty()}. Defaults to
     * {@link Double#MAX_VALUE}, meaning "assume the whole score is visible"
     * - the safe default for callers that never wire this up: an oversized
     * score still renders (windowed near the top) instead of crashing, it
     * just isn't scrollable into view past the window without this being
     * set correctly.
     *
     * @return the writable viewport-height property, in local pixel units
     */
    public DoubleProperty viewportHeightProperty() {
        return viewportHeight;
    }

    private final DoubleProperty viewportHeight =
            new SimpleDoubleProperty(this, "viewportHeight", Double.MAX_VALUE);

    private final ObservableSet<MarkingCategory> hiddenTextCategories =
            FXCollections.observableSet(EnumSet.noneOf(MarkingCategory.class));

    private final BooleanProperty bracketsVisible =
            new SimpleBooleanProperty(this, "bracketsVisible", true);

    /**
     * Live map of per-element highlight colours. Uses identity comparisons
     * on keys so a caller can hold the exact {@link MusicElement} instance
     * returned by the model builder as the map key. Mutations trigger a
     * cheap {@link #repaint()} - no re-engrave.
     */
    private final ObservableMap<MusicElement, Color> noteHighlights =
            FXCollections.observableMap(new IdentityHashMap<>());

    /**
     * Live map of per-element background colours. Independent of
     * {@link #noteHighlights} - an element can carry a tint, a
     * background, both, or neither.
     */
    private final ObservableMap<MusicElement, Color> noteBackgrounds =
            FXCollections.observableMap(new IdentityHashMap<>());

    /**
     * Live map of per-element accidental overlays. Independent of
     * {@link #noteHighlights} and {@link #noteBackgrounds} - an element
     * can carry any combination of tint, background, and accidental
     * overlay.
     */
    private final ObservableMap<MusicElement, Accidental> noteAccidentals =
            FXCollections.observableMap(new IdentityHashMap<>());

    private Score score;
    private LayoutResult layout;

    /**
     * Creates an empty score view at the default engraving width.
     */
    public SheetView() {
        getChildren().add(canvas);
        systemWidth.addListener((obs, oldV, newV) -> rebuild());
        zoom.addListener((obs, oldV, newV) -> rebuild());
        viewportTop.addListener((obs, oldV, newV) -> updateWindow(false));
        viewportHeight.addListener((obs, oldV, newV) -> updateWindow(false));
        hiddenTextCategories.addListener((SetChangeListener<MarkingCategory>) change -> repaint());
        bracketsVisible.addListener((obs, oldV, newV) -> repaint());
        noteHighlights.addListener((MapChangeListener<MusicElement, Color>) change -> repaint());
        noteBackgrounds.addListener((MapChangeListener<MusicElement, Color>) change -> repaint());
        noteAccidentals.addListener((MapChangeListener<MusicElement, Accidental>) change -> repaint());
        renderer.setNoteColorProvider(this::highlightFor);
        renderer.setNoteBackgroundProvider(this::backgroundFor);
        renderer.setNoteAccidentalProvider(this::accidentalFor);
        setMinSize(200, 120);
        // Initial empty canvas at the default width; setScore replaces it.
        rebuild();
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

    /**
     * Returns the score currently displayed by this view, or {@code null}.
     */
    public Score getScore() {
        return score;
    }

    /**
     * Sets the score to display and rebuilds the engraved layout.
     */
    public void setScore(Score score) {
        this.score = score;
        rebuild();
    }

    /**
     * The most recently engraved layout for the current score, or
     * {@code null} when no score has been set. Callers building playback
     * cursors read anchors and call {@link LayoutResult#xAtTime(double)}
     * from this result. This is always the <em>full</em> layout, regardless
     * of how much of it is currently windowed into the Canvas.
     */
    public LayoutResult getLayout() {
        return layout;
    }

    /**
     * The system width used by the engraver. Changing this triggers a rebuild.
     * Callers can bind this to a container's width (e.g., the ScrollPane
     * viewport) to make the score reflow while still relying on the layout
     * to report the actual content size.
     *
     * @return the writable width property used by the engraver
     */
    public DoubleProperty systemWidthProperty() {
        return systemWidth;
    }

    /**
     * Returns the current system width used by the engraver.
     */
    public double getSystemWidth() {
        return systemWidth.get();
    }

    /**
     * Updates the system width used by the engraver, if the width is positive.
     */
    public void setSystemWidth(double width) {
        if (width > 0) {
            systemWidth.set(width);
        }
    }

    /**
     * Render zoom factor. Values above 1 enlarge the score; values between 0
     * and 1 shrink it. Changing this triggers a rebuild.
     */
    public DoubleProperty zoomProperty() {
        return zoom;
    }

    /**
     * @return the current render zoom factor.
     */
    public double getZoom() {
        return zoom.get();
    }

    /**
     * Update the render zoom factor, if positive.
     */
    public void setZoom(double factor) {
        if (factor > 0) {
            zoom.set(factor);
        }
    }

    /**
     * @return the current viewport top, in local pixel units.
     */
    public double getViewportTop() {
        return viewportTop.get();
    }

    /**
     * Updates the viewport top. See {@link #viewportTopProperty()}.
     */
    public void setViewportTop(double top) {
        viewportTop.set(Math.max(0, top));
    }

    /**
     * @return the current viewport height, in local pixel units.
     */
    public double getViewportHeight() {
        return viewportHeight.get();
    }

    /**
     * Updates the viewport height. See {@link #viewportHeightProperty()}.
     */
    public void setViewportHeight(double height) {
        if (height > 0) {
            viewportHeight.set(height);
        }
    }

    /**
     * Live-observable set of {@link MarkingCategory categories} that this
     * view should hide. Mutations trigger a rebuild.
     *
     * @return the observable set (never {@code null})
     */
    public ObservableSet<MarkingCategory> hiddenTextCategoriesProperty() {
        return hiddenTextCategories;
    }

    /**
     * JavaFX property controlling whether {@link BracketPlacement
     * bracket placements} (both implicit grand-staff braces and
     * {@code <part-group>}-driven brackets) are drawn. Defaults to
     * {@code true}; mutations trigger a repaint (no re-engrave).
     *
     * @return the writable brackets-visible property
     */
    public BooleanProperty bracketsVisibleProperty() {
        return bracketsVisible;
    }

    /**
     * @return {@code true} when brackets are currently drawn.
     */
    public boolean isBracketsVisible() {
        return bracketsVisible.get();
    }

    /**
     * Update the bracket visibility flag, triggering a repaint.
     */
    public void setBracketsVisible(boolean visible) {
        bracketsVisible.set(visible);
    }

    /**
     * Live-observable per-element highlight colour map. Add entries to
     * tint the notehead + stem + flag + accidental of individual notes
     * (looked up by identity), and remove entries to clear them. Every
     * mutation triggers a {@link #repaint()} but not a re-engrave, so
     * this is inexpensive even at real-time MIDI event rates.
     *
     * @return the observable map (never {@code null})
     */
    public ObservableMap<MusicElement, Color> noteHighlights() {
        return noteHighlights;
    }

    /**
     * Live-observable per-element <em>background</em> colour map. Adding a
     * {@code (element, colour)} pair draws a rounded, semi-transparent
     * rectangle behind that element's notehead - including the accidental
     * slot and augmentation-dot column - for a strong "played right now"
     * visual pop. Removing the entry clears the background. Mutations
     * trigger a {@link #repaint()} - no re-engrave.
     *
     * <p>Independent of {@link #noteHighlights()}: an element can carry a
     * tint, a background, both, or neither.
     *
     * @return the observable map (never {@code null})
     */
    public ObservableMap<MusicElement, Color> noteBackgrounds() {
        return noteBackgrounds;
    }

    /**
     * Live-observable per-element accidental overlay map. Adding a
     * {@code (element, Accidental)} pair draws the corresponding SMuFL
     * accidental glyph to the immediate left of that element's notehead -
     * regardless of whether the element itself carries a
     * {@link com.sheetmusic4j.core.model.Note#displayedAccidental()}.
     * Removing the entry clears it. Mutations trigger a {@link #repaint()} -
     * no re-engrave.
     *
     * <p>Intended for dynamic runtime overlays such as playback / live-input
     * views that need to signal "the user just played a sharp of this
     * natural note" without altering the engraved score. Independent of
     * both {@link #noteHighlights()} and {@link #noteBackgrounds()}: an
     * element can carry any combination of tint, background, and
     * accidental overlay.
     *
     * <p>When the source note already carries an engraved accidental
     * (via {@code <accidental>} on the note's MusicXML, i.e.
     * {@link com.sheetmusic4j.core.model.Note#displayedAccidental()} is
     * present), the overlay value <em>replaces</em> the engraved glyph
     * in-place for as long as the entry is in the map. Removing the
     * entry restores the original engraved accidental. This lets callers
     * momentarily flash e.g. {@link Accidental#DOUBLE_SHARP} on a note
     * that natively displays a simple sharp, without editing the model.
     *
     * @return the observable map (never {@code null})
     */
    public ObservableMap<MusicElement, Accidental> noteAccidentals() {
        return noteAccidentals;
    }

    /**
     * Look up the highlight colour for the given source element, if any.
     * Wrapped as a {@link RenderColor} so the surface-agnostic
     * {@link ScorePainter} can apply it without dragging in JavaFX.
     */
    private Optional<RenderColor> highlightFor(MusicElement element) {
        return toRenderColor(noteHighlights.get(element));
    }

    /**
     * Look up the background colour for the given source element, if any.
     * Honours the JavaFX colour's opacity so callers can drive
     * semi-transparent highlights straight from a {@link Color} literal.
     */
    private Optional<RenderColor> backgroundFor(MusicElement element) {
        return toRenderColor(noteBackgrounds.get(element));
    }

    /**
     * Look up the accidental overlay for the given source element, if any.
     */
    private Optional<Accidental> accidentalFor(MusicElement element) {
        return Optional.ofNullable(noteAccidentals.get(element));
    }

    /**
     * Re-engrave the current score (produces a fresh {@link LayoutResult}),
     * update this view's reported content size accordingly, and repaint.
     * Called only when a score/layout knob changes; per-note highlight
     * changes go through {@link #repaint()} instead, and viewport-only
     * changes go through {@link #updateWindow(boolean)}.
     */
    private void rebuild() {
        windowStartLayoutY = 0;
        if (score == null) {
            layout = null;
            double zoomFactor = Math.max(zoom.get(), 0.01);
            canvas.setWidth(systemWidth.get() * zoomFactor);
            canvas.setHeight(FALLBACK_HEIGHT * zoomFactor);
            canvas.relocate(0, 0);
            canvas.getGraphicsContext2D().clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
        } else {
            layout = engraver.layout(score, layoutOptions());
            updateWindow(true);
        }

        double totalWidth = contentWidth();
        double totalHeight = contentHeight();
        setPrefSize(totalWidth, totalHeight);
        setMinSize(Math.min(200, totalWidth), Math.min(120, totalHeight));
        setMaxSize(totalWidth, totalHeight);
        requestLayout();
        if (getParent() != null) {
            getParent().requestLayout();
        }
    }

    /**
     * Ensures the Canvas currently materializes whatever
     * {@link #viewportTopProperty()}/{@link #viewportHeightProperty()} say
     * is visible, re-painting (and repositioning) it only when the visible
     * viewport has moved outside the currently rendered window - or when
     * {@code force} is set, as {@link #rebuild()} does right after
     * re-engraving. For scores that fit within {@link #MAX_CANVAS_DIMENSION}
     * this always resolves to "the whole score, once" - identical to a
     * plain, unwindowed Canvas - so ordinary scores never pay any windowing
     * cost.
     */
    private void updateWindow(boolean force) {
        if (layout == null) {
            return;
        }
        double zoomFactor = Math.max(zoom.get(), 0.01);
        double contentHeightPx = layout.height() * zoomFactor;
        double contentWidthPx = layout.width() * zoomFactor;
        double canvasWidthPx = Math.max(Math.min(contentWidthPx, MAX_CANVAS_DIMENSION), 1.0);
        double canvasHeightPx = Math.max(Math.min(contentHeightPx, MAX_CANVAS_DIMENSION), 1.0);

        double viewTop = Math.max(0, viewportTop.get());
        double viewHeight = Math.max(0, Math.min(viewportHeight.get(), contentHeightPx));
        double viewBottom = viewTop + viewHeight;

        double windowStartPx = windowStartLayoutY * zoomFactor;
        double windowEndPx = windowStartPx + canvasHeightPx;
        boolean windowCoversViewport = canvasHeightPx >= contentHeightPx
                || (viewTop >= windowStartPx && viewBottom <= windowEndPx);

        if (!force && windowCoversViewport) {
            return;
        }

        double desiredStartPx;
        if (canvasHeightPx >= contentHeightPx) {
            desiredStartPx = 0;
        } else {
            double centered = viewTop - (canvasHeightPx - viewHeight) / 2.0;
            desiredStartPx = Math.max(0, Math.min(centered, contentHeightPx - canvasHeightPx));
        }
        windowStartLayoutY = desiredStartPx / zoomFactor;

        canvas.setWidth(canvasWidthPx);
        canvas.setHeight(canvasHeightPx);
        requestLayout();
        paintWindow();
    }

    /**
     * Restricts a full layout to just the systems/texts/note-anchors whose
     * y-position falls within {@code [startY, endY)}, so the Canvas only
     * pays the drawing cost for its own window instead of the whole score.
     */
    private static LayoutResult sliceForWindow(LayoutResult full, double startY, double endY) {
        List<SystemLayout> systems = full.systems().stream()
                .filter(s -> s.y() >= startY && s.y() < endY)
                .toList();
        List<TextPlacement> texts = full.texts().stream()
                .filter(t -> t.y() >= startY && t.y() < endY)
                .toList();
        List<NoteAnchor> anchors = full.noteAnchors().stream()
                .filter(a -> a.y() >= startY && a.y() < endY)
                .toList();
        return new LayoutResult(systems, texts, anchors, full.width(), endY - startY);
    }

    /**
     * Redraw the currently materialized window from the cached layout
     * without re-engraving or repositioning it. Used whenever only per-note
     * highlights or the bracket visibility flag change.
     */
    private void repaint() {
        if (layout == null) {
            return;
        }
        paintWindow();
    }

    private void paintWindow() {
        double zoomFactor = Math.max(zoom.get(), 0.01);
        renderer.setHiddenTextCategories(hiddenTextCategories);
        renderer.setBracketsVisible(bracketsVisible.get());
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
        gc.save();
        gc.translate(0, -windowStartLayoutY * zoomFactor);
        gc.scale(zoomFactor, zoomFactor);
        double windowEndLayoutY = windowStartLayoutY + canvas.getHeight() / zoomFactor;
        LayoutResult slice = sliceForWindow(layout, windowStartLayoutY, windowEndLayoutY);
        renderer.render(gc, slice, layout.width(), canvas.getHeight() / zoomFactor);
        gc.restore();
    }

    private double contentWidth() {
        double zoomFactor = Math.max(zoom.get(), 0.01);
        return Math.max((layout != null ? layout.width() : systemWidth.get()) * zoomFactor, 1.0);
    }

    private double contentHeight() {
        double zoomFactor = Math.max(zoom.get(), 0.01);
        return Math.max((layout != null ? layout.height() : FALLBACK_HEIGHT) * zoomFactor, 1.0);
    }

    @Override
    protected void layoutChildren() {
        canvas.relocate(0, windowStartLayoutY * Math.max(zoom.get(), 0.01));
    }

    @Override
    protected double computeMinWidth(double height) {
        return Math.min(200, contentWidth());
    }

    @Override
    protected double computeMinHeight(double width) {
        return Math.min(120, contentHeight());
    }

    @Override
    protected double computePrefWidth(double height) {
        return contentWidth();
    }

    @Override
    protected double computePrefHeight(double width) {
        return contentHeight();
    }

    @Override
    protected double computeMaxWidth(double height) {
        return contentWidth();
    }

    @Override
    protected double computeMaxHeight(double width) {
        return contentHeight();
    }

    private LayoutOptions layoutOptions() {
        LayoutOptions defaults = LayoutOptions.defaults();
        double width = systemWidth.get() > 0 ? systemWidth.get() : defaults.systemWidth();
        return defaults.toBuilder().systemWidth(width).build();
    }
}
