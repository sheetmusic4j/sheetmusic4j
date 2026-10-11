package com.sheetmusic4j.render;

/**
 * Minimal drawing surface abstraction used by {@link ScorePainter}. Implementations
 * adapt the abstract primitives to a concrete backend (JavaFX canvas, AWT image,
 * ...), so the exact same painting logic can be reused for on-screen rendering and
 * for headless image comparison in tests.
 *
 * <p>All coordinates and sizes are in the same units as the engraving layout.
 */
public interface RenderSurface {

    /**
     * Marks the start of the drawing primitives that belong to one source
     * element (for example all glyphs and the stem of a single note). Calls
     * may not nest. Immediate-mode backends (canvas, AWT) ignore this; a
     * retained-mode backend can group the primitives that follow into one
     * object carrying {@code source}, so callers can attach events to it.
     *
     * @param source the source object, typically a {@code MusicElement}; never {@code null}
     */
    default void beginElement(Object source) {
    }

    /** Ends the group opened by {@link #beginElement(Object)}. */
    default void endElement() {
    }

    /** Sets the stroke color for subsequent outline drawing. */
    void setStroke(RenderColor color);

    /** Sets the fill color for subsequent filled drawing. */
    void setFill(RenderColor color);

    /** Sets the line width for subsequent stroke operations. */
    void setLineWidth(double width);

    /** Fills an axis-aligned rectangle. */
    void fillRect(double x, double y, double width, double height);

    /** Draws a straight line segment. */
    void strokeLine(double x1, double y1, double x2, double y2);

    /** Fills an axis-aligned oval. */
    void fillOval(double x, double y, double width, double height);

    /** Strokes the outline of an axis-aligned oval. */
    void strokeOval(double x, double y, double width, double height);

    /**
     * Strokes the outline of an axis-aligned rectangle. The default
     * implementation composes the outline from four {@link #strokeLine
     * strokeLine} calls; backends that support a native rectangle stroke
     * (JavaFX / AWT) should override for better rendering quality.
     *
     * @param x      top-left x
     * @param y      top-left y
     * @param width  width of the rectangle
     * @param height height of the rectangle
     */
    default void strokeRect(double x, double y, double width, double height) {
        strokeLine(x, y, x + width, y);
        strokeLine(x + width, y, x + width, y + height);
        strokeLine(x + width, y + height, x, y + height);
        strokeLine(x, y + height, x, y);
    }

    /**
     * Fills an axis-aligned rectangle with rounded corners. Used by
     * {@link ScorePainter} to draw semi-transparent note-background
     * highlights behind noteheads: unlike {@link #fillRect}, the fill
     * colour is passed as a parameter (rather than left as ambient surface
     * state) so backends can honour its {@link RenderColor#alpha()} channel
     * without disturbing the caller's active fill.
     *
     * <p>The default implementation ignores the corner radii and
     * approximates with a plain rectangle drawn in the current fill
     * colour. Backends that support rounded rectangles (JavaFX / AWT)
     * should override.
     *
     * @param x         top-left x
     * @param y         top-left y
     * @param width     width of the rectangle
     * @param height    height of the rectangle
     * @param arcWidth  horizontal diameter of the corner arc
     * @param arcHeight vertical diameter of the corner arc
     * @param color     fill colour, potentially semi-transparent
     */
    default void fillRoundedRect(double x, double y, double width, double height,
                                 double arcWidth, double arcHeight, RenderColor color) {
        setFill(color);
        fillRect(x, y, width, height);
    }

    /** Draws text with the current stroke/fill settings. */
    void strokeText(String text, double x, double y);

    /**
     * Draws text at the given font em-size. Default implementation ignores
     * {@code fontSize} and falls back to {@link #strokeText(String, double, double)};
     * backends that support font sizing (AWT / FX) should override.
     *
     * @param text     text to draw
     * @param x        baseline x
     * @param y        baseline y
     * @param fontSize preferred font em-size in layout units
     */
    default void drawText(String text, double x, double y, double fontSize) {
        strokeText(text, x, y);
    }

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

    /**
     * Strokes a smooth quadratic curve from ({@code x1},{@code y1}) to
     * ({@code x2},{@code y2}) bending toward the control point
     * ({@code cx},{@code cy}) - used for ties and slurs, which should read
     * as a rounded arc (like a rotated "(" or ")"), not an angular "V"/"^".
     * The default falls back to two straight segments meeting at the
     * control point; backends with a native curve primitive (JavaFX/AWT)
     * override this for a properly rounded arc.
     *
     * @param x1 start x
     * @param y1 start y
     * @param cx control point x (pulls the curve toward it, but the curve
     *           does not pass through it - see {@code ScorePainter}'s
     *           doubled-bend calculation feeding this)
     * @param cy control point y
     * @param x2 end x
     * @param y2 end y
     */
    default void strokeQuadCurve(double x1, double y1, double cx, double cy, double x2, double y2) {
        strokeLine(x1, y1, cx, cy);
        strokeLine(cx, cy, x2, y2);
    }

    /**
     * Strokes a smooth cubic (2-control-point) curve from
     * ({@code x1},{@code y1}) to ({@code x2},{@code y2}). Unlike
     * {@link #strokeQuadCurve}, two independent control points keep the arc
     * looking evenly rounded regardless of how deep it needs to bend - a
     * single-control-point quadratic curve gets visibly more "pointed" as
     * its bend-to-span ratio grows (e.g. a slur clearing a melodic peak far
     * above its two endpoints), where a cubic curve stays a smooth dome.
     * The default falls back to the quadratic approximation; backends with
     * a native cubic Bezier primitive (JavaFX/AWT) override this.
     *
     * @param x1  start x
     * @param y1  start y
     * @param c1x first control point x
     * @param c1y first control point y
     * @param c2x second control point x
     * @param c2y second control point y
     * @param x2  end x
     * @param y2  end y
     */
    default void strokeCubicCurve(double x1, double y1, double c1x, double c1y,
                                  double c2x, double c2y, double x2, double y2) {
        strokeQuadCurve(x1, y1, (c1x + c2x) / 2.0, (c1y + c2y) / 2.0, x2, y2);
    }
}
