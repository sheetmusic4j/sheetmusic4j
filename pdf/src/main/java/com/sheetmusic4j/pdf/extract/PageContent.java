package com.sheetmusic4j.pdf.extract;

import java.util.List;

/**
 * What an engraved PDF page is made of, in a y-down coordinate system (points): the glyphs of its fonts and the
 * vector shapes drawn around them. Staff lines, stems and barlines are thin lines or rectangles; beams are filled
 * polygons; ties and slurs are curves.
 *
 * @param glyphs the shown glyphs in drawing order
 * @param hlines horizontal line segments (staff lines, ledger lines)
 * @param vlines vertical line segments (stems, barlines)
 * @param diagonals slanted line segments, left end first (hairpins, outlines)
 * @param beams  straight filled shapes that are neither thin lines nor curves
 * @param curves bounding boxes of curved shapes
 */
public record PageContent(int index, double width, double height, List<Glyph> glyphs, List<HLine> hlines,
                          List<VLine> vlines, List<Segment> diagonals, List<Box> beams, List<Box> curves) {

    /**
     * An axis-aligned rectangle.
     */
    public record Box(double left, double top, double right, double bottom) {
        public double width() {
            return right - left;
        }

        public double height() {
            return bottom - top;
        }

        public double centerX() {
            return (left + right) / 2;
        }

        public double centerY() {
            return (top + bottom) / 2;
        }
    }

    /**
     * One shown glyph. {@code name} is the glyph name of a Type 1 font (e.g. {@code noteheads.s2}), {@code text} the
     * Unicode the font maps the character to, either may be {@code null}. {@code x}/{@code y} is the glyph origin and
     * {@code box} the outline's bounds.
     */
    public record Glyph(String font, String name, String text, double x, double y, Box box) {
    }

    public record HLine(double x0, double x1, double y, double thickness) {
    }

    public record VLine(double x, double y0, double y1, double thickness) {
    }

    /** A slanted segment; {@code x0 <= x1}. */
    public record Segment(double x0, double y0, double x1, double y1, double thickness) {
    }
}
