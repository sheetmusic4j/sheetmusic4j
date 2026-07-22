package com.sheetmusic4j.engraving.glyph;

import com.sheetmusic4j.engraving.placement.BeamPlacement;
import com.sheetmusic4j.engraving.placement.BracketPlacement;
import com.sheetmusic4j.engraving.placement.GlyphPlacement;

/**
 * A minimal, renderer-agnostic set of musical glyphs. Values loosely correspond
 * to SMuFL semantics but carry no font/rendering details themselves.
 */
public enum Glyph {
    STAFF_LINE,
    LEDGER_LINE,
    NOTEHEAD_WHOLE,
    NOTEHEAD_HALF,
    NOTEHEAD_BLACK,
    /**
     * Stem going up (from the right side of the notehead).
     */
    STEM_UP,
    /**
     * Stem going down (from the left side of the notehead).
     */
    STEM_DOWN,
    /**
     * @deprecated use {@link #STEM_UP} or {@link #STEM_DOWN}. Kept for source compatibility.
     */
    @Deprecated
    STEM,
    /**
     * Flag for an unbeamed eighth note with an upward stem.
     */
    FLAG_8TH_UP,
    /**
     * Flag for an unbeamed eighth note with a downward stem.
     */
    FLAG_8TH_DOWN,
    /**
     * Flag for an unbeamed sixteenth note with an upward stem.
     */
    FLAG_16TH_UP,
    /**
     * Flag for an unbeamed sixteenth note with a downward stem.
     */
    FLAG_16TH_DOWN,
    /**
     * A beam segment connecting the stem tips of two or more beamed notes.
     * Positioning is carried by {@link BeamPlacement} rather than a
     * {@link GlyphPlacement}, so this value is a marker only.
     */
    BEAM,
    ACCIDENTAL_SHARP,
    ACCIDENTAL_FLAT,
    ACCIDENTAL_NATURAL,
    ACCIDENTAL_DOUBLE_SHARP,
    ACCIDENTAL_DOUBLE_FLAT,
    /**
     * Augmentation dot ("dot of prolongation").
     */
    AUG_DOT,
    CLEF_G,
    CLEF_F,
    CLEF_C,
    /**
     * @deprecated use {@link #TIME_DIGIT_0}..{@link #TIME_DIGIT_9}. Kept for source compatibility.
     */
    @Deprecated
    TIME_DIGIT,
    TIME_DIGIT_0,
    TIME_DIGIT_1,
    TIME_DIGIT_2,
    TIME_DIGIT_3,
    TIME_DIGIT_4,
    TIME_DIGIT_5,
    TIME_DIGIT_6,
    TIME_DIGIT_7,
    TIME_DIGIT_8,
    TIME_DIGIT_9,
    REST_WHOLE,
    REST_HALF,
    REST_QUARTER,
    REST_EIGHTH,
    REST_SIXTEENTH,
    REST_THIRTY_SECOND,
    REST_SIXTY_FOURTH,
    REST_128TH,
    /* -- Dynamics (SMuFL Private Use Area U+E5xx). -------------------- */
    DYNAMIC_PPP,
    DYNAMIC_PP,
    DYNAMIC_P,
    DYNAMIC_MP,
    DYNAMIC_MF,
    DYNAMIC_F,
    DYNAMIC_FF,
    DYNAMIC_FFF,
    DYNAMIC_SF,
    DYNAMIC_SFZ,
    DYNAMIC_FZ,
    DYNAMIC_FP,
    DYNAMIC_RF,
    DYNAMIC_RFZ,
    DYNAMIC_NIENTE,
    /**
     * SMuFL {@code brace} glyph (U+E000). Drawn at the left edge of a
     * multi-staff part to visually group its staves.
     */
    BRACE,
    /**
     * SMuFL {@code bracketTop} glyph (U+E003). Ornamental tip drawn at the
     * top of a {@link BracketPlacement.BracketShape#BRACKET} bracket.
     */
    BRACKET_TOP,
    /**
     * SMuFL {@code bracketBottom} glyph (U+E004). Ornamental tip drawn at
     * the bottom of a {@link BracketPlacement.BracketShape#BRACKET}
     * bracket.
     */
    BRACKET_BOTTOM,
    /**
     * SMuFL {@code articStaccatoAbove} glyph (U+E4A2).
     */
    ARTICULATION_STACCATO,
    /**
     * SMuFL {@code articAccentAbove} glyph (U+E4A0).
     */
    ARTICULATION_ACCENT;

    /**
     * Look up the {@code TIME_DIGIT_N} glyph for a single digit 0..9.
     *
     * @throws IllegalArgumentException if {@code digit} is out of range
     */
    public static Glyph timeDigit(int digit) {
        return switch (digit) {
            case 0 -> TIME_DIGIT_0;
            case 1 -> TIME_DIGIT_1;
            case 2 -> TIME_DIGIT_2;
            case 3 -> TIME_DIGIT_3;
            case 4 -> TIME_DIGIT_4;
            case 5 -> TIME_DIGIT_5;
            case 6 -> TIME_DIGIT_6;
            case 7 -> TIME_DIGIT_7;
            case 8 -> TIME_DIGIT_8;
            case 9 -> TIME_DIGIT_9;
            default -> throw new IllegalArgumentException("digit must be 0..9, got " + digit);
        };
    }

    /**
     * If this glyph is a {@code TIME_DIGIT_N} return the corresponding digit character,
     * otherwise {@code null}.
     */
    public Character timeDigitChar() {
        return switch (this) {
            case TIME_DIGIT_0 -> '0';
            case TIME_DIGIT_1 -> '1';
            case TIME_DIGIT_2 -> '2';
            case TIME_DIGIT_3 -> '3';
            case TIME_DIGIT_4 -> '4';
            case TIME_DIGIT_5 -> '5';
            case TIME_DIGIT_6 -> '6';
            case TIME_DIGIT_7 -> '7';
            case TIME_DIGIT_8 -> '8';
            case TIME_DIGIT_9 -> '9';
            default -> null;
        };
    }
}
