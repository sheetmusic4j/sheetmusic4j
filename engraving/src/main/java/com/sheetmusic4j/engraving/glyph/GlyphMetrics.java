package com.sheetmusic4j.engraving.glyph;

/**
 * Horizontal extent of SMuFL glyphs, in staff spaces, as drawn by the
 * Bravura font. The single source of glyph widths: layout reserves room
 * for accidentals and other marks from it, and renderers center glyphs
 * with it, so the two can't drift apart.
 *
 * <p>Values are the glyphs' advance widths (1 em = 4 staff spaces in
 * SMuFL); {@code 0} for glyphs drawn as primitives or not yet measured.
 */
public final class GlyphMetrics {

    private GlyphMetrics() {
    }

    /**
     * Advance width of a glyph, in staff spaces.
     */
    public static double advanceWidth(Glyph glyph) {
        return switch (glyph) {
            case NOTEHEAD_BLACK, NOTEHEAD_HALF -> 1.18;
            case NOTEHEAD_WHOLE -> 1.5;
            case NOTEHEAD_BREVE -> 2.2;
            case TIME_DIGIT_0, TIME_DIGIT_1, TIME_DIGIT_2, TIME_DIGIT_3, TIME_DIGIT_4,
                 TIME_DIGIT_5, TIME_DIGIT_6, TIME_DIGIT_7, TIME_DIGIT_8, TIME_DIGIT_9 -> 1.4;
            case ACCIDENTAL_FLAT -> 0.9;
            case ACCIDENTAL_NATURAL -> 0.65;
            case ACCIDENTAL_SHARP -> 1.0;
            case ACCIDENTAL_DOUBLE_SHARP -> 1.05;
            case ACCIDENTAL_DOUBLE_FLAT -> 1.75;
            case AUG_DOT -> 0.4;
            case DYNAMIC_P, DYNAMIC_F, DYNAMIC_NIENTE -> 1.5;
            case DYNAMIC_PP, DYNAMIC_MP, DYNAMIC_MF, DYNAMIC_FF, DYNAMIC_FZ,
                 DYNAMIC_FP, DYNAMIC_SF, DYNAMIC_RF -> 2.4;
            case DYNAMIC_PPP, DYNAMIC_FFF, DYNAMIC_SFZ, DYNAMIC_RFZ -> 3.2;
            case BRACE -> 0.6;
            case BRACKET_TOP, BRACKET_BOTTOM -> 0.7;
            case ARTICULATION_STACCATO -> 0.5;
            case ARTICULATION_ACCENT -> 1.2;
            case ARTICULATION_DOWN_BOW, ARTICULATION_UP_BOW -> 1.2;
            default -> 0.0;
        };
    }

    /**
     * Half the advance width of a glyph, in layout units, for a staff whose
     * line gap (one staff space) is {@code staffSpace}.
     */
    public static double halfWidth(Glyph glyph, double staffSpace) {
        return advanceWidth(glyph) * 0.5 * staffSpace;
    }
}
