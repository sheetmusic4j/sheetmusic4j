package com.sheetmusic4j.fxviewer;

/**
 * Minimal drawing surface abstraction used by {@link ScorePainter}. Implementations
 * adapt the abstract primitives to a concrete backend (JavaFX canvas, AWT image,
 * ...), so the exact same painting logic can be reused for on-screen rendering and
 * for headless image comparison in tests.
 *
 * <p>All coordinates and sizes are in the same units as the engraving layout.
 */
public interface RenderSurface {

    void setStroke(RenderColor color);

    void setFill(RenderColor color);

    void setLineWidth(double width);

    void fillRect(double x, double y, double width, double height);

    void strokeLine(double x1, double y1, double x2, double y2);

    void fillOval(double x, double y, double width, double height);

    void strokeOval(double x, double y, double width, double height);

    void strokeText(String text, double x, double y);
}
