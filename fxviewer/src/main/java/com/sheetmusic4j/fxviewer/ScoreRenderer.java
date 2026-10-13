package com.sheetmusic4j.fxviewer;

import com.sheetmusic4j.engraving.layout.LayoutResult;
import com.sheetmusic4j.render.RenderSurface;
import com.sheetmusic4j.render.ScorePainter;
import javafx.scene.canvas.GraphicsContext;

/**
 * Draws a {@link LayoutResult} onto a JavaFX {@link GraphicsContext}. This is a thin
 * adapter over {@link ScorePainter}, which contains the surface-agnostic drawing
 * logic shared with headless renderers used in tests; configure what and how it
 * draws on {@link #painter()}.
 */
public final class ScoreRenderer {

    private final ScorePainter painter = new ScorePainter();

    /**
     * Creates a renderer that delegates all drawing to {@link ScorePainter}.
     */
    public ScoreRenderer() {
    }

    /**
     * The painter this renderer draws with: hidden categories, bracket
     * visibility, page colour, per-note colours/backgrounds/accidentals and
     * text metrics are all set there.
     */
    public ScorePainter painter() {
        return painter;
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
}
