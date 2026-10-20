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
 * @param leadingBarline the barline opening this measure (a repeat-start
 *                       {@code |:} or a heavy-light {@code [|}), or
 *                       {@code null}
 * @param leadingBarlineX x at which the leading barline starts: the
 *                       measure's left edge, or - on the first measure of
 *                       a row - just after the clef / key / time signature
 * @param ending        the first/second-ending label active for this
 *                       measure (e.g. {@code "1"}), or {@code null}
 */
public record MeasureLayout(int number, double x, double width,
                            double startQuarters, double endQuarters,
                            Barline barline, Barline leadingBarline, double leadingBarlineX, String ending) {

    /**
     * Constructor for callers that only know whether the measure opens with
     * a repeat-start, drawn at its left edge.
     */
    public MeasureLayout(int number, double x, double width, double startQuarters, double endQuarters,
                         Barline barline, boolean leadingRepeatStart, String ending) {
        this(number, x, width, startQuarters, endQuarters, barline,
                leadingRepeatStart ? new Barline(Barline.Style.HEAVY_LIGHT, Barline.Repeat.FORWARD) : null,
                x, ending);
    }

    /**
     * Whether the measure opens with a repeat-start mark.
     */
    public boolean leadingRepeatStart() {
        return leadingBarline != null && leadingBarline.repeat() == Barline.Repeat.FORWARD;
    }

    /**
     * Backwards-compatible constructor for callers that pre-date barline /
     * ending support.
     */
    public MeasureLayout(int number, double x, double width, double startQuarters, double endQuarters) {
        this(number, x, width, startQuarters, endQuarters, null, null, x, null);
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
