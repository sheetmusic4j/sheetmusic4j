package com.sheetmusic4j.engraving;

import java.util.List;

/**
 * A positioned five-line staff for one part within a system.
 *
 * @param x        left edge
 * @param y        y of the top staff line
 * @param width    staff width
 * @param lineGap  vertical gap between adjacent staff lines
 * @param measures the measures placed on this staff
 * @param glyphs   glyphs (clef, time signature, notes, rests) placed on this staff
 * @param beams    beam segments connecting stem tips of beamed groups
 * @param ties     tie arcs joining notes of the same pitch
 */
public record StaffLayout(
        double x,
        double y,
        double width,
        double lineGap,
        List<MeasureLayout> measures,
        List<GlyphPlacement> glyphs,
        List<BeamPlacement> beams,
        List<TiePlacement> ties) {

    public StaffLayout {
        measures = List.copyOf(measures);
        glyphs = List.copyOf(glyphs);
        beams = List.copyOf(beams);
        ties = List.copyOf(ties);
    }

    /**
     * Backwards-compatible constructor for callers that pre-date beam/tie support.
     */
    public StaffLayout(double x, double y, double width, double lineGap,
                       List<MeasureLayout> measures, List<GlyphPlacement> glyphs) {
        this(x, y, width, lineGap, measures, glyphs, List.of(), List.of());
    }

    /**
     * Y position of the given staff line (0 = top line, 4 = bottom line).
     */
    public double lineY(int line) {
        return y + line * lineGap;
    }
}
