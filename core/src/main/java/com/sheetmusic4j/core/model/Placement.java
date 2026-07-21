package com.sheetmusic4j.core.model;

import java.util.Locale;

/**
 * Positional hint for elements that can be rendered above or below the staff
 * (directions, dynamics, tempo marks, wedges, ...). {@link #DEFAULT} means the
 * engraver should pick a sensible side based on the element type.
 */
public enum Placement {
    ABOVE,
    BELOW,
    DEFAULT;

    /**
     * Parse a MusicXML {@code placement} attribute value. Returns
     * {@link #DEFAULT} for {@code null}/blank/unknown values.
     */
    public static Placement fromXml(String value) {
        if (value == null) {
            return DEFAULT;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "above" -> ABOVE;
            case "below" -> BELOW;
            default -> DEFAULT;
        };
    }

    /**
     * MusicXML string representation, or {@code null} when this is
     * {@link #DEFAULT} (canonical form: no attribute).
     */
    public String xmlValue() {
        return switch (this) {
            case ABOVE -> "above";
            case BELOW -> "below";
            case DEFAULT -> null;
        };
    }
}
