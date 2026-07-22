package com.sheetmusic4j.engraving.layout;

import com.sheetmusic4j.engraving.placement.BeamPlacement;
import com.sheetmusic4j.engraving.placement.GlyphPlacement;
import com.sheetmusic4j.engraving.placement.HairpinPlacement;
import com.sheetmusic4j.engraving.placement.SlurPlacement;
import com.sheetmusic4j.engraving.placement.TiePlacement;
import com.sheetmusic4j.engraving.placement.TupletPlacement;

import java.util.List;

/**
 * A positioned five-line staff for one part within a system.
 *
 * @param x        left edge
 * @param y        y of the top staff line
 * @param width    staff width
 * @param lineGap  vertical gap between adjacent staff lines
 * @param measures the measures placed on this staff
 * @param glyphs   glyphs (clef, time signature, notes, rests, articulations) placed on this staff
 * @param beams    beam segments connecting stem tips of beamed groups
 * @param ties     tie arcs joining notes of the same pitch
 * @param slurs    slur arcs joining the first/last note of a phrase
 * @param tuplets  tuplet numbers/brackets spanning a run of notes
 * @param hairpins crescendo/diminuendo wedges
 */
public record StaffLayout(
        double x,
        double y,
        double width,
        double lineGap,
        List<MeasureLayout> measures,
        List<GlyphPlacement> glyphs,
        List<BeamPlacement> beams,
        List<TiePlacement> ties,
        List<SlurPlacement> slurs,
        List<TupletPlacement> tuplets,
        List<HairpinPlacement> hairpins) {

    public StaffLayout {
        measures = List.copyOf(measures);
        glyphs = List.copyOf(glyphs);
        beams = List.copyOf(beams);
        ties = List.copyOf(ties);
        slurs = List.copyOf(slurs);
        tuplets = List.copyOf(tuplets);
        hairpins = List.copyOf(hairpins);
    }

    /**
     * Backwards-compatible constructor for callers that pre-date slur/tuplet/hairpin support.
     */
    public StaffLayout(double x, double y, double width, double lineGap,
                       List<MeasureLayout> measures, List<GlyphPlacement> glyphs,
                       List<BeamPlacement> beams, List<TiePlacement> ties) {
        this(x, y, width, lineGap, measures, glyphs, beams, ties, List.of(), List.of(), List.of());
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
