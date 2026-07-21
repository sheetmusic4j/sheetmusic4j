package com.sheetmusic4j.core.model;

/**
 * Clef sign as defined by MusicXML {@code <clef><sign>}.
 */
public enum ClefSign {
    /** G clef (treble). */
    G,
    /** F clef (bass). */
    F,
    /** C clef (alto/tenor). */
    C,
    /** Percussion clef. */
    PERCUSSION,
    /** Tablature clef. */
    TAB,
    /** No clef. */
    NONE;

    /** Returns the MusicXML string representation. */
    public String xmlValue() {
        return switch (this) {
            case PERCUSSION -> "percussion";
            case TAB -> "TAB";
            case NONE -> "none";
            default -> name();
        };
    }

    /**
     * Parse a MusicXML clef sign string.
     *
     * @param value the MusicXML string value (null-safe)
     * @return the corresponding ClefSign, or {@link #NONE} if not recognized
     */
    public static ClefSign fromXml(String value) {
        if (value == null) {
            return NONE;
        }
        return switch (value.trim().toUpperCase()) {
            case "G" -> G;
            case "F" -> F;
            case "C" -> C;
            case "PERCUSSION" -> PERCUSSION;
            case "TAB" -> TAB;
            default -> NONE;
        };
    }
}
