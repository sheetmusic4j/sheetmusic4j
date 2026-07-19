package com.sheet4j.core.model;

/**
 * Accidental applied to a pitch, derived from the {@code alter} value.
 */
public enum Accidental {
    DOUBLE_FLAT(-2),
    FLAT(-1),
    NATURAL(0),
    SHARP(1),
    DOUBLE_SHARP(2);

    private final int alter;

    Accidental(int alter) {
        this.alter = alter;
    }

    public int alter() {
        return alter;
    }

    public static Accidental fromAlter(int alter) {
        for (Accidental accidental : values()) {
            if (accidental.alter == alter) {
                return accidental;
            }
        }
        return NATURAL;
    }
}
