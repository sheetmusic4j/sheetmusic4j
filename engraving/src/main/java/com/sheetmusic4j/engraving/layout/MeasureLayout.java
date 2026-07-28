package com.sheetmusic4j.engraving.layout;

import com.sheetmusic4j.core.model.Barline;

/**
 * The horizontal extent and musical time range of a single measure on a
 * staff.
 *
 * @param number         the measure number
 * @param x              left edge of the measure
 * @param width          width of the measure
 * @param startQuarters  onset of the first beat of the measure, measured
 *                       from the start of the score in quarter notes
 * @param endQuarters    end of the measure (onset + measure duration), in
 *                       quarter notes
 * @param barline        the barline drawn at this measure's right edge, or
 *                       {@code null} for a plain single thin line
 * @param leadingRepeatStart whether a repeat-start mark ({@code |:}) is
 *                       drawn at this measure's left edge
 * @param ending         the first/second-ending label active for this
 *                       measure (e.g. {@code "1"}), or {@code null}
 */
public record MeasureLayout(int number, double x, double width,
                            double startQuarters, double endQuarters,
                            Barline barline, boolean leadingRepeatStart, String ending) {

    /**
     * Backwards-compatible constructor for callers that pre-date barline /
     * ending support.
     */
    public MeasureLayout(int number, double x, double width, double startQuarters, double endQuarters) {
        this(number, x, width, startQuarters, endQuarters, null, false, null);
    }

    /**
     * Backwards-compatible constructor for callers that pre-date musical
     * time on the layout. Sets the start / end quarter positions to
     * {@code 0}; downstream time-based helpers (cursor positioning,
     * {@code xAtTime}) will fall back to a linear x-space interpolation for
     * such measures.
     */
    public MeasureLayout(int number, double x, double width) {
        this(number, x, width, 0.0, 0.0);
    }

    public double right() {
        return x + width;
    }

    /**
     * Duration of the measure in quarter notes. Zero for measures produced
     * by the backwards-compatible constructor (i.e. before the engraver
     * started tracking musical time).
     */
    public double durationQuarters() {
        return endQuarters - startQuarters;
    }
}
