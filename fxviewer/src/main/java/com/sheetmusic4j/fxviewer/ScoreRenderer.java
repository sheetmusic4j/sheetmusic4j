package com.sheetmusic4j.fxviewer;

import java.util.Set;

import com.sheetmusic4j.engraving.layout.LayoutResult;
import com.sheetmusic4j.engraving.glyph.MarkingCategory;

import com.sheetmusic4j.engraving.placement.BracketPlacement;
import javafx.scene.canvas.GraphicsContext;

/**
 * Draws a {@link LayoutResult} onto a JavaFX {@link GraphicsContext}. This is a thin
 * adapter over {@link ScorePainter}, which contains the surface-agnostic drawing
 * logic shared with headless renderers used in tests.
 */
public final class ScoreRenderer {

    private final ScorePainter painter = new ScorePainter();

    /** Creates a renderer that delegates all drawing to {@link ScorePainter}. */
    public ScoreRenderer() {
    }

    /** Renders the given layout into the supplied JavaFX graphics context. */
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
     * Configure which {@link MarkingCategory categories} the underlying
     * painter should skip. See {@link ScorePainter#setHiddenCategories(Set)}.
     */
    public void setHiddenTextCategories(Set<MarkingCategory> categories) {
        painter.setHiddenCategories(categories);
    }

    /** @return the current set of hidden categories (defensive copy). */
    public Set<MarkingCategory> getHiddenTextCategories() {
        return painter.getHiddenCategories();
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

    /** @return whether brackets are currently drawn by the underlying painter. */
    public boolean isBracketsVisible() {
        return painter.isBracketsVisible();
    }
    }
