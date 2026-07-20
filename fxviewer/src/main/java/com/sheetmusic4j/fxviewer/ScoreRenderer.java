package com.sheetmusic4j.fxviewer;

import com.sheetmusic4j.engraving.LayoutResult;
import javafx.scene.canvas.GraphicsContext;

/**
 * Draws a {@link LayoutResult} onto a JavaFX {@link GraphicsContext}. This is a thin
 * adapter over {@link ScorePainter}, which contains the surface-agnostic drawing
 * logic shared with headless renderers used in tests.
 */
public final class ScoreRenderer {

    private final ScorePainter painter = new ScorePainter();

    public void render(GraphicsContext gc, LayoutResult layout) {
        RenderSurface surface = new FxRenderSurface(gc);
        painter.paint(surface, layout, gc.getCanvas().getWidth(), gc.getCanvas().getHeight());
    }
}
