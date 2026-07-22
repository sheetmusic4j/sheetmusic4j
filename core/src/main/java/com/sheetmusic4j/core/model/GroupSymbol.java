package com.sheetmusic4j.core.model;

import java.util.Locale;

/**
 * Visual symbol used to bracket a {@link PartGroup} at the left edge of a
 * system. Values mirror MusicXML's {@code <group-symbol>} vocabulary:
 * {@code bracket}, {@code brace}, {@code square}, {@code line}. When the
 * source omits {@code <group-symbol>} the parser records {@link #NONE}, and
 * the engraver skips drawing any bracket for that group (the parts are
 * still logically grouped, they just do not receive a visible marker).
 */
public enum GroupSymbol {
    /** Square bracket with curled ornamental tips (orchestral families). */
    BRACKET,
    /** Curly brace, typically used for grand-staff piano groupings. */
    BRACE,
    /** Plain square bracket without ornamental tips. */
    SQUARE,
    /** Single thin vertical line without serifs. */
    LINE,
    /** No visible bracket. */
    NONE;

    /**
     * Serialisation form matching MusicXML's {@code <group-symbol>} content.
     * {@link #NONE} maps to {@code "none"}.
     *
     * @return the lowercase MusicXML token for this symbol
     */
    public String xmlValue() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * Parse a MusicXML {@code <group-symbol>} value. Returns {@link #NONE}
     * for {@code null}, blank strings, or unrecognised tokens (lenient).
     *
     * @param value raw MusicXML symbol text
     * @return matching enum value, or {@link #NONE}
     */
    public static GroupSymbol fromXml(String value) {
        if (value == null) {
            return NONE;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "bracket" -> BRACKET;
            case "brace" -> BRACE;
            case "square" -> SQUARE;
            case "line" -> LINE;
            default -> NONE;
        };
    }
}
