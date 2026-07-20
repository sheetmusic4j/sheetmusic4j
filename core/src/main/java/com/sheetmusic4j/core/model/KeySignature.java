package com.sheetmusic4j.core.model;

/**
 * A key signature expressed as the MusicXML {@code fifths} value: the number of
 * sharps (positive) or flats (negative) on the circle of fifths.
 *
 * @param fifths number of sharps (&gt;0) or flats (&lt;0)
 */
public record KeySignature(int fifths) {

    public static KeySignature cMajor() {
        return new KeySignature(0);
    }

    public int sharps() {
        return Math.max(fifths, 0);
    }

    public int flats() {
        return Math.max(-fifths, 0);
    }
}
