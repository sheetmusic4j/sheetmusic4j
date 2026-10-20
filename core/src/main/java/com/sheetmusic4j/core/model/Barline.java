package com.sheetmusic4j.core.model;

/**
 * A barline: the one drawn at the end of a {@link Measure} — separating it
 * from the next measure, or closing the part — or, via
 * {@link Measure#leadingBarline()}, one opening a measure. {@code style}
 * controls the line weight; {@code repeat} adds repeat dots on the
 * appropriate side(s).
 */
public record Barline(Style style, Repeat repeat) {

    /**
     * Style axis: line weight. Ignored when {@link #repeat()} is not
     * {@link Repeat#NONE} — repeat barlines always draw their own
     * conventional weight (thick+thin facing the dots for a single
     * direction, thin+thick+thin for both).
     */
    public enum Style {
        /** One thin line ({@code |}). */
        REGULAR,
        /** Two thin lines ({@code ||}). */
        DOUBLE,
        /** Thin then thick, ending a section or the piece ({@code |]}). */
        FINAL,
        /** Thick then thin, opening a section ({@code [|}). */
        HEAVY_LIGHT
    }

    /** Repeat-dot placement relative to the barline. */
    public enum Repeat {
        NONE,
        /** Dots before the line: closes a repeated section ({@code :|}). */
        BACKWARD,
        /** Dots after the line: opens a repeated section ({@code |:}). */
        FORWARD,
        /** Dots on both sides ({@code ::}): closes one section, opens the next. */
        BOTH
    }
}
