package com.sheetmusic4j.core.model;

/**
 * Accidental applied to a pitch, derived from the {@code alter} value.
 */
public enum Accidental {
    /** Double flat. */
    DOUBLE_FLAT(-2),
    /** Flat. */
    FLAT(-1),
    /** Natural (no accidental). */
    NATURAL(0),
    /** Sharp. */
    SHARP(1),
    /** Double sharp. */
    DOUBLE_SHARP(2);

    private final int alter;

    Accidental(int alter) {
        this.alter = alter;
    }

    /** Returns the chromatic alteration value. */
    public int alter() {
        return alter;
    }

    /** Converts an alteration value to the corresponding accidental.
     *
     * @param alter the alteration value (-2, -1, 0, 1, 2, etc.)
     * @return the corresponding accidental, or {@link #NATURAL} if not found
     */
    public static Accidental fromAlter(int alter) {
        for (Accidental accidental : values()) {
            if (accidental.alter == alter) {
                return accidental;
            }
        }
        return NATURAL;
    }
}
