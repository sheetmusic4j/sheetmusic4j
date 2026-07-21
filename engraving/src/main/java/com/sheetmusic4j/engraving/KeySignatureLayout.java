package com.sheetmusic4j.engraving;

import com.sheetmusic4j.core.model.Clef;
import com.sheetmusic4j.core.model.ClefSign;
import com.sheetmusic4j.core.model.KeySignature;

/**
 * Compute the staff-step positions for the accidentals in a
 * {@link KeySignature}, taking the current {@link Clef} into account.
 *
 * <p>Staff steps are expressed the same way as {@link GlyphPlacement#staffStep()}:
 * half-staff-space units measured downward from the top staff line, so 0 is
 * the top line, 2 the second line, 4 the middle line, ..., 8 the bottom line.
 */
public final class KeySignatureLayout {

    // Standard order of sharps: F# C# G# D# A# E# B#
    // Vertical positions (staff steps) for treble clef, corresponding to
    // F5, C5, G5, D5, A4, E5, B4.
    private static final int[] SHARP_STEPS_TREBLE = {0, 3, -1, 2, 5, 1, 4};
    // Standard order of flats: Bb Eb Ab Db Gb Cb Fb (treble)
    // B4, E5, A4, D5, G4, C5, F4
    private static final int[] FLAT_STEPS_TREBLE = {4, 1, 5, 2, 6, 3, 7};

    private KeySignatureLayout() {
    }

    /**
     * Vertical positions (staff steps) for the accidentals in the given key
     * signature under the given clef, in the order they must be drawn from
     * left to right.
     *
     * @param clef the clef in force at this point
     * @param key  the key signature
     * @return an array of staff-step positions, one per accidental to draw
     */
    public static int[] positions(Clef clef, KeySignature key) {
        int count = Math.min(7, Math.max(key.sharps(), key.flats()));
        if (count == 0) {
            return new int[0];
        }
        int shift = trebleOffset(clef);
        int[] template = key.sharps() > 0 ? SHARP_STEPS_TREBLE : FLAT_STEPS_TREBLE;
        int[] result = new int[count];
        for (int i = 0; i < count; i++) {
            result[i] = template[i] + shift;
        }
        return result;
    }

    /**
     * Returns the accidental glyph (sharp or flat) that this key signature
     * requires. Natural cancellations are not emitted at this stage.
     */
    public static Glyph glyphFor(KeySignature key) {
        return key.sharps() > 0 ? Glyph.ACCIDENTAL_SHARP : Glyph.ACCIDENTAL_FLAT;
    }

    /**
     * Vertical shift (in staff steps) from a treble-clef key-signature template
     * to the given clef.
     *
     * <p>Key-signature accidentals sit two diatonic steps lower under bass
     * clef, and one under alto (C on line 3). This matches the standard
     * engraving convention.
     */
    private static int trebleOffset(Clef clef) {
        ClefSign sign = clef.sign();
        int line = clef.line();
        // G clef line 2 = treble reference.
        // Each unit of "how many diatonic steps lower the clef sits vs
        // treble" shifts the pattern down by 2 staff steps.
        if (sign == ClefSign.G) {
            // Rare treble-8vb / french violin variants; treat as treble.
            return 0;
        }
        if (sign == ClefSign.F) {
            // Bass clef: sharps rendered two steps lower than treble.
            // Bass on line 4 is the standard; other F-clef variants are close.
            return 2;
        }
        if (sign == ClefSign.C) {
            // Alto (line 3) = +1 step; tenor (line 4) = -1 step from treble.
            return switch (line) {
                case 3 -> 1; // alto
                case 4 -> -1; // tenor (accidentals shifted up)
                case 5 -> -3; // baritone (rare)
                case 2 -> 3;  // mezzo-soprano
                case 1 -> 5;  // soprano
                default -> 1;
            };
        }
        return 0;
    }
}
