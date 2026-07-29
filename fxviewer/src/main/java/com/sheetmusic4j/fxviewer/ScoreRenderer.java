package com.sheetmusic4j.fxviewer;

import com.sheetmusic4j.core.model.Accidental;
import com.sheetmusic4j.core.model.MusicElement;
import com.sheetmusic4j.engraving.glyph.MarkingCategory;
import com.sheetmusic4j.engraving.layout.LayoutResult;
import com.sheetmusic4j.engraving.placement.BracketPlacement;
import javafx.scene.canvas.GraphicsContext;

import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Draws a {@link LayoutResult} onto a JavaFX {@link GraphicsContext}. This is a thin
 * adapter over {@link ScorePainter}, which contains the surface-agnostic drawing
 * logic shared with headless renderers used in tests.
 */
public final class ScoreRenderer {

    private final ScorePainter painter = new ScorePainter();

    /**
     * Creates a renderer that delegates all drawing to {@link ScorePainter}.
     */
    public ScoreRenderer() {
    }

    /**
     * Renders the given layout into the supplied JavaFX graphics context.
     */
    public void render(GraphicsContext gc, LayoutResult layout) {
        render(gc, layout, gc.getCanvas().getWidth(), gc.getCanvas().getHeight());
    }

    /**
     * Renders the given layout into the supplied JavaFX graphics context using
     * explicit painter bounds in layout units.
     */
    public void render(GraphicsContext gc, LayoutResult layout, double width, double height) {
        RenderSurface surface = new FxRenderSurface(gc);
        painter.paint(surface, layout, width, height);
    }

    /**
     * @return the current set of hidden categories (defensive copy).
     */
    public Set<MarkingCategory> getHiddenTextCategories() {
        return painter.getHiddenCategories();
    }

    /**
     * Configure which {@link MarkingCategory categories} the underlying
     * painter should skip. See {@link ScorePainter#setHiddenCategories(Set)}.
     */
    public void setHiddenTextCategories(Set<MarkingCategory> categories) {
        painter.setHiddenCategories(categories);
    }

    /**
     * @return whether brackets are currently drawn by the underlying painter.
     */
    public boolean isBracketsVisible() {
        return painter.isBracketsVisible();
    }

    /**
     * Toggle the visibility of all {@link BracketPlacement bracket placements}
     * on the underlying painter. See {@link ScorePainter#setBracketsVisible(boolean)}.
     *
     * @param visible whether brackets should be drawn
     */
    public void setBracketsVisible(boolean visible) {
        painter.setBracketsVisible(visible);
    }

    /**
     * Install a per-element colour provider on the underlying painter. See
     * {@link ScorePainter#setNoteColorProvider(Function)}.
     */
    public void setNoteColorProvider(Function<MusicElement, Optional<RenderColor>> provider) {
        painter.setNoteColorProvider(provider);
    }

    /**
     * Install a per-element background provider on the underlying painter.
     * See {@link ScorePainter#setNoteBackgroundProvider(Function)}.
     */
    public void setNoteBackgroundProvider(Function<MusicElement, Optional<RenderColor>> provider) {
        painter.setNoteBackgroundProvider(provider);
    }

    /**
     * @return the underlying painter's note-background geometry.
     */
    public NoteBackgroundStyle getNoteBackgroundStyle() {
        return painter.getNoteBackgroundStyle();
    }

    /**
     * Configure the geometry of the note-background rectangle on the underlying
     * painter. See {@link ScorePainter#setNoteBackgroundStyle(NoteBackgroundStyle)}.
     */
    public void setNoteBackgroundStyle(NoteBackgroundStyle style) {
        painter.setNoteBackgroundStyle(style);
    }

    /**
     * Install a per-element accidental override provider on the
     * underlying painter. See
     * {@link ScorePainter#setNoteAccidentalProvider(Function)}.
     */
    public void setNoteAccidentalProvider(Function<MusicElement, Optional<Accidental>> provider) {
        painter.setNoteAccidentalProvider(provider);
    }
}
