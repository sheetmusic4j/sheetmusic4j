package com.sheet4j.core.model;

/**
 * A time signature.
 *
 * @param beats    the number of beats per measure (the numerator)
 * @param beatType the note value that represents one beat (the denominator)
 */
public record TimeSignature(int beats, int beatType) {

    public TimeSignature {
        if (beats <= 0) {
            throw new IllegalArgumentException("beats must be positive");
        }
        if (beatType <= 0) {
            throw new IllegalArgumentException("beatType must be positive");
        }
    }

    public static TimeSignature fourFour() {
        return new TimeSignature(4, 4);
    }

    /**
     * Length of one measure in quarter notes.
     */
    public double measureLengthInQuarters() {
        return beats * (4.0 / beatType);
    }
}
