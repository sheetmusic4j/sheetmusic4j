package com.sheetmusic4j.core.model;

/**
 * The barline drawn at the end of a {@link Measure} — the line separating it
 * from the next measure, or closing the part. {@code style} controls the
 * line weight; {@code repeat} adds repeat dots on the appropriate side(s).
 *
 * <p>A repeat that opens at the very start of a measure (no dots needed on
 * a preceding line) is instead carried by {@link Measure#leadingRepeatStart()}.
 */
public record Barline(Style style, Repeat repeat) {

    /**
     * Style axis: line weight. Ignored when {@link #repeat()} is not
     * {@link Repeat#NONE} — repeat barlines always draw their own
     * conventional weight (thin+thick for a single direction, thin+thin
     * for both).
     */
    public enum Style {
        REGULAR,
        DOUBLE,
        FINAL
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
