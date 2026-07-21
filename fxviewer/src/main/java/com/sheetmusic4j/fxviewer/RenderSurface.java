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

    /**
     * Draw a SMuFL glyph (see {@link SmuflGlyphs}) if a SMuFL font is
     * available on this surface. The default implementation returns
     * {@code false}, meaning "no SMuFL font, fall back to primitives".
     *
     * @param glyphChars one or more Private Use Area characters
     * @param x          horizontal anchor (SMuFL glyphs are drawn with their
     *                   left edge at {@code x} and baseline at {@code y})
     * @param y          baseline y
     * @param sizeHint   preferred font em-size, typically {@code 4 * staffLineGap}
     * @return {@code true} if the surface rendered the glyph, {@code false}
     *         if the caller should use a primitive fallback
     */
    default boolean drawSmuflGlyph(String glyphChars, double x, double y, double sizeHint) {
        return false;
    }
}
