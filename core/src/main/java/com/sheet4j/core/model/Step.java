package com.sheet4j.core.model;

/**
 * Diatonic pitch step (the letter name of a note).
 */
public enum Step {
    C, D, E, F, G, A, B;

    /**
     * Semitone offset of this step from C within an octave.
     */
    public int semitonesFromC() {
        return switch (this) {
            case C -> 0;
            case D -> 2;
            case E -> 4;
            case F -> 5;
            case G -> 7;
            case A -> 9;
            case B -> 11;
        };
    }
}
