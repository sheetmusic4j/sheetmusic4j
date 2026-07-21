package com.sheetmusic4j.core.model;

/**
 * Diatonic pitch step (the letter name of a note).
 */
public enum Step {
    /** C. */ C,
    /** D. */ D,
    /** E. */ E,
    /** F. */ F,
    /** G. */ G,
    /** A. */ A,
    /** B. */ B;

    /**
     * Semitone offset of this step from C within an octave.
     *
     * @return the number of semitones from C (0-11)
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
