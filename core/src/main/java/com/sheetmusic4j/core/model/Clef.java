package com.sheetmusic4j.core.model;

/**
 * A clef placed on a staff line.
 *
 * @param sign the clef sign (G, F, C, ...)
 * @param line the staff line the clef is centered on (counted from the bottom, 1-based)
 */
public record Clef(ClefSign sign, int line) {

    public Clef {
        if (sign == null) {
            throw new IllegalArgumentException("sign must not be null");
        }
    }

    /** Creates a treble clef. */
    public static Clef treble() {
        return new Clef(ClefSign.G, 2);
    }

    /** Creates a bass clef. */
    public static Clef bass() {
        return new Clef(ClefSign.F, 4);
    }
}
