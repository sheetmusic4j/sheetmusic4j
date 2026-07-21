package com.sheetmusic4j.core.model;

/**
 * A duration expressed in MusicXML {@code divisions} (ticks per quarter note),
 * together with the number of divisions used by the containing context.
 *
 * @param value    duration in divisions
 * @param divisions divisions per quarter note (the resolution)
 */
public record Duration(int value, int divisions) {

    /**
     * A zero-length duration. Useful for elements that occupy no time on the
     * timeline (e.g. directions, dynamics, tempo markings).
     */
    public static final Duration ZERO = new Duration(0, 1);

    public Duration {
        if (value < 0) {
            throw new IllegalArgumentException("duration value must not be negative");
        }
        if (divisions <= 0) {
            throw new IllegalArgumentException("divisions must be positive");
        }
    }

    /**
     * Duration expressed in quarter notes.
     *
     * @return the duration as a quarter note value
     */
    public double inQuarters() {
        return (double) value / divisions;
    }

    /**
     * Whether this duration occupies zero time on the timeline.
     *
     * @return true if the duration is zero
     */
    public boolean isZero() {
        return value == 0;
    }

    /**
     * Create a duration from a number of quarter notes.
     *
     * @param quarters the duration in quarter notes
     * @param divisions divisions per quarter note
     * @return a new Duration
     */
    public static Duration ofQuarters(double quarters, int divisions) {
        return new Duration((int) Math.round(quarters * divisions), divisions);
    }
}
