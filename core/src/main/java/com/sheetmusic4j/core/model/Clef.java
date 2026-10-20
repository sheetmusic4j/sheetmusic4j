package com.sheetmusic4j.core.model;

/**
 * A clef placed on a staff line.
 *
 * @param sign         the clef sign (G, F, C, ...)
 * @param line         the staff line the clef is centered on (counted from the bottom, 1-based)
 * @param octaveChange octaves the music sounds above (positive) or below (negative) the
 *                     written clef, e.g. -1 for the guitar's treble clef with an 8 below
 */
public record Clef(ClefSign sign, int line, int octaveChange) {

    public Clef {
        if (sign == null) {
            throw new IllegalArgumentException("sign must not be null");
        }
    }

    /** A clef without octave transposition. */
    public Clef(ClefSign sign, int line) {
        this(sign, line, 0);
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
