package com.sheetmusic4j.engraving.glyph;

import com.sheetmusic4j.core.model.Accidental;
import com.sheetmusic4j.core.model.Articulation;
import com.sheetmusic4j.core.model.Clef;
import com.sheetmusic4j.core.model.DynamicMark;
import com.sheetmusic4j.core.model.NoteType;
import com.sheetmusic4j.engraving.glyph.Glyph;

/**
 * Lookup tables from model values to the renderer-agnostic {@link Glyph}
 * that draws them.
 */
public final class GlyphMapper {

    private GlyphMapper() {
    }

    public static Glyph clefGlyph(Clef clef) {
        return switch (clef.sign()) {
            case F -> Glyph.CLEF_F;
            case C -> Glyph.CLEF_C;
            default -> Glyph.CLEF_G;
        };
    }

    public static Glyph noteheadGlyph(NoteType type) {
        return switch (type) {
            case WHOLE -> Glyph.NOTEHEAD_WHOLE;
            case BREVE, LONG, MAXIMA -> Glyph.NOTEHEAD_BREVE;
            case HALF -> Glyph.NOTEHEAD_HALF;
            default -> Glyph.NOTEHEAD_BLACK;
        };
    }

    public static Glyph restGlyph(NoteType type) {
        return switch (type) {
            case WHOLE, BREVE, LONG, MAXIMA -> Glyph.REST_WHOLE;
            case HALF -> Glyph.REST_HALF;
            case QUARTER -> Glyph.REST_QUARTER;
            case EIGHTH -> Glyph.REST_EIGHTH;
            case SIXTEENTH -> Glyph.REST_SIXTEENTH;
            case THIRTY_SECOND -> Glyph.REST_THIRTY_SECOND;
            case SIXTY_FOURTH -> Glyph.REST_SIXTY_FOURTH;
            case HUNDRED_TWENTY_EIGHTH -> Glyph.REST_128TH;
        };
    }

    /**
     * Choose the flag glyph for an unbeamed short note, or {@code null} if the
     * type does not use a flag.
     */
    public static Glyph flagGlyph(NoteType type, boolean stemUp) {
        return switch (type) {
            case EIGHTH -> stemUp ? Glyph.FLAG_8TH_UP : Glyph.FLAG_8TH_DOWN;
            case SIXTEENTH -> stemUp ? Glyph.FLAG_16TH_UP : Glyph.FLAG_16TH_DOWN;
            case THIRTY_SECOND -> stemUp ? Glyph.FLAG_32ND_UP : Glyph.FLAG_32ND_DOWN;
            case SIXTY_FOURTH -> stemUp ? Glyph.FLAG_64TH_UP : Glyph.FLAG_64TH_DOWN;
            case HUNDRED_TWENTY_EIGHTH -> stemUp ? Glyph.FLAG_128TH_UP : Glyph.FLAG_128TH_DOWN;
            default -> null;
        };
    }

    public static Glyph accidentalGlyph(Accidental accidental) {
        if (accidental == null) {
            return null;
        }
        return switch (accidental) {
            case SHARP -> Glyph.ACCIDENTAL_SHARP;
            case FLAT -> Glyph.ACCIDENTAL_FLAT;
            case NATURAL -> Glyph.ACCIDENTAL_NATURAL;
            case DOUBLE_SHARP -> Glyph.ACCIDENTAL_DOUBLE_SHARP;
            case DOUBLE_FLAT -> Glyph.ACCIDENTAL_DOUBLE_FLAT;
        };
    }

    public static Glyph articulationGlyph(Articulation articulation) {
        return switch (articulation) {
            case STACCATO -> Glyph.ARTICULATION_STACCATO;
            case ACCENT -> Glyph.ARTICULATION_ACCENT;
            case DOWN_BOW -> Glyph.ARTICULATION_DOWN_BOW;
            case UP_BOW -> Glyph.ARTICULATION_UP_BOW;
            case ROLL -> Glyph.ARTICULATION_ROLL;
        };
    }

    /**
     * Map a {@link DynamicMark} to the corresponding SMuFL dynamic glyph.
     */
    public static Glyph dynamicGlyph(DynamicMark mark) {
        return switch (mark) {
            case PPP -> Glyph.DYNAMIC_PPP;
            case PP -> Glyph.DYNAMIC_PP;
            case P -> Glyph.DYNAMIC_P;
            case MP -> Glyph.DYNAMIC_MP;
            case MF -> Glyph.DYNAMIC_MF;
            case F -> Glyph.DYNAMIC_F;
            case FF -> Glyph.DYNAMIC_FF;
            case FFF -> Glyph.DYNAMIC_FFF;
            case SF -> Glyph.DYNAMIC_SF;
            case SFZ -> Glyph.DYNAMIC_SFZ;
            case FZ -> Glyph.DYNAMIC_FZ;
            case FP -> Glyph.DYNAMIC_FP;
            case RF -> Glyph.DYNAMIC_RF;
            case RFZ -> Glyph.DYNAMIC_RFZ;
            case N -> Glyph.DYNAMIC_NIENTE;
        };
    }
}
