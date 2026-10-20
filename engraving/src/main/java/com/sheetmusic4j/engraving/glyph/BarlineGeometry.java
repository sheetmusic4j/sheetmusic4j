package com.sheetmusic4j.engraving.glyph;

import com.sheetmusic4j.core.model.Barline;

/**
 * Line weights and spacing of barlines and repeat signs, in staff spaces,
 * following the SMuFL engraving defaults (as published in Bravura's
 * metadata). The single source for both drawing a barline and reserving
 * room for it, so repeat dots never land on the next note.
 *
 * <p>Extents are measured from the barline's anchor x, which is the
 * measure boundary: a barline ending a measure extends to the left of it
 * (into the measure), one opening a measure to the right.
 */
public final class BarlineGeometry {

    /** Thickness of a thin barline. */
    public static final double THIN = 0.16;

    /** Thickness of a thick barline. */
    public static final double THICK = 0.5;

    /** Clear space between the two lines of a double, final or repeat barline. */
    public static final double SEPARATION = 0.4;

    /** Clear space between a repeat barline's thin line and its dots. */
    public static final double DOT_SEPARATION = 0.16;

    /** Diameter of a repeat dot. */
    public static final double DOT_DIAMETER = 0.4;

    private BarlineGeometry() {
    }

    /**
     * How far a barline closing a measure reaches into it, to the left of
     * the measure boundary (dots included).
     */
    public static double extentBefore(Barline barline) {
        if (barline == null) {
            return THIN;
        }
        return switch (barline.repeat()) {
            case BACKWARD -> THICK + SEPARATION + THIN + DOT_SEPARATION + DOT_DIAMETER;
            case BOTH -> THICK / 2 + SEPARATION + THIN + DOT_SEPARATION + DOT_DIAMETER;
            case FORWARD -> THICK;
            case NONE -> switch (barline.style()) {
                case REGULAR -> THIN;
                case DOUBLE -> THIN + SEPARATION + THIN;
                case FINAL, HEAVY_LIGHT -> THICK + SEPARATION + THIN;
            };
        };
    }

    /**
     * How far a barline opening a measure reaches into it, to the right of
     * the measure boundary (dots included). For a closing barline, how far
     * it reaches into the <em>next</em> measure.
     */
    public static double extentAfter(Barline barline, boolean opening) {
        if (barline == null) {
            return 0.0;
        }
        return switch (barline.repeat()) {
            case FORWARD -> THICK + SEPARATION + THIN + DOT_SEPARATION + DOT_DIAMETER;
            case BOTH -> THICK / 2 + SEPARATION + THIN + DOT_SEPARATION + DOT_DIAMETER;
            case BACKWARD -> 0.0;
            case NONE -> opening ? switch (barline.style()) {
                case REGULAR -> THIN;
                case DOUBLE -> THIN + SEPARATION + THIN;
                case FINAL, HEAVY_LIGHT -> THICK + SEPARATION + THIN;
            } : 0.0;
        };
    }
}
