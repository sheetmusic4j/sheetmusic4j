package com.sheet4j.core.model;

/**
 * Clef sign as defined by MusicXML {@code <clef><sign>}.
 */
public enum ClefSign {
    G, F, C, PERCUSSION, TAB, NONE;

    public String xmlValue() {
        return switch (this) {
            case PERCUSSION -> "percussion";
            case TAB -> "TAB";
            case NONE -> "none";
            default -> name();
        };
    }

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
