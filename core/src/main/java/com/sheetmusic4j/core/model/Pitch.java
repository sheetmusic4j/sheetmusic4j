package com.sheetmusic4j.core.model;

/**
 * A concrete pitch: a diatonic {@link Step}, an octave, and a chromatic alteration
 * ({@code alter}: -1 = flat, +1 = sharp, etc.).
 */
public record Pitch(Step step, int octave, int alter) {

    public Pitch {
        if (step == null) {
            throw new IllegalArgumentException("step must not be null");
        }
    }

    public Pitch(Step step, int octave) {
        this(step, octave, 0);
    }

    public Accidental accidental() {
        return Accidental.fromAlter(alter);
    }

    /**
     * Convert this pitch to a MIDI note number (middle C = C4 = 60).
     */
    public int toMidiNumber() {
        return (octave + 1) * 12 + step.semitonesFromC() + alter;
    }

    /**
     * Build a {@link Pitch} from a MIDI note number, preferring sharps for black keys.
     */
    public static Pitch fromMidiNumber(int midi) {
        int octave = midi / 12 - 1;
        int semitone = midi % 12;
        return switch (semitone) {
            case 0 -> new Pitch(Step.C, octave, 0);
            case 1 -> new Pitch(Step.C, octave, 1);
            case 2 -> new Pitch(Step.D, octave, 0);
            case 3 -> new Pitch(Step.D, octave, 1);
            case 4 -> new Pitch(Step.E, octave, 0);
            case 5 -> new Pitch(Step.F, octave, 0);
            case 6 -> new Pitch(Step.F, octave, 1);
            case 7 -> new Pitch(Step.G, octave, 0);
            case 8 -> new Pitch(Step.G, octave, 1);
            case 9 -> new Pitch(Step.A, octave, 0);
            case 10 -> new Pitch(Step.A, octave, 1);
            case 11 -> new Pitch(Step.B, octave, 0);
            default -> throw new IllegalStateException("Unexpected semitone: " + semitone);
        };
    }

    /**
     * Diatonic staff step index used for vertical positioning: C0 = 0, D0 = 1, ...
     */
    public int diatonicStepNumber() {
        return octave * 7 + step.ordinal();
    }
}
