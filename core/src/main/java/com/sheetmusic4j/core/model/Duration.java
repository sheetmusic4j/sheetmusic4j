package com.sheetmusic4j.core.model;

/**
 * A duration expressed in MusicXML {@code divisions} (ticks per quarter note),
 * together with the number of divisions used by the containing context.
 *
 * @param value    duration in divisions
 * @param divisions divisions per quarter note (the resolution)
 */
public record Duration(int value, int divisions) {

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
     */
    public double inQuarters() {
        return (double) value / divisions;
    }

    /**
     * Create a duration from a number of quarter notes.
     */
    public static Duration ofQuarters(double quarters, int divisions) {
        return new Duration((int) Math.round(quarters * divisions), divisions);
    }
}
