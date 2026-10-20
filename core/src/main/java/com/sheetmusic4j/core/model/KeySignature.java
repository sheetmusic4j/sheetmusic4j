package com.sheetmusic4j.core.model;

/**
 * A key signature expressed as the MusicXML {@code fifths} value: the number of
 * sharps (positive) or flats (negative) on the circle of fifths.
 *
 * @param fifths number of sharps (&gt;0) or flats (&lt;0)
 */
public record KeySignature(int fifths) {

    private static final String SHARP_ORDER = "FCGDAEB";
    private static final String FLAT_ORDER = "BEADGCF";

    /** Creates a C major key signature (no sharps or flats). */
    public static KeySignature cMajor() {
        return new KeySignature(0);
    }

    /** Returns the number of sharps in this key signature. */
    public int sharps() {
        return Math.max(fifths, 0);
    }

    /** Returns the number of flats in this key signature. */
    public int flats() {
        return Math.max(-fifths, 0);
    }

    /**
     * The alteration this key signature implies for a step: {@code +1} when
     * the step carries a sharp, {@code -1} for a flat, {@code 0} otherwise.
     */
    public int alter(Step step) {
        if (fifths > 0) {
            return SHARP_ORDER.indexOf(step.name()) < fifths ? 1 : 0;
        }
        return FLAT_ORDER.indexOf(step.name()) < -fifths ? -1 : 0;
    }
}
