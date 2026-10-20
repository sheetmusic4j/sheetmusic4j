package com.sheetmusic4j.core.lilypond;

import com.sheetmusic4j.core.model.NoteType;

/**
 * Maps between LilyPond duration numbers ({@code 1}, {@code 2}, {@code 4},
 * ...) and {@link NoteType}s, for both the parser and the writer.
 */
final class LilyPondDuration {

    private LilyPondDuration() {
    }

    /** The note type of a LilyPond duration number; {@code 0} is read as a whole. */
    static NoteType typeForNumber(int n) {
        return switch (n) {
            case 0, 1 -> NoteType.WHOLE;
            case 2 -> NoteType.HALF;
            case 4 -> NoteType.QUARTER;
            case 8 -> NoteType.EIGHTH;
            case 16 -> NoteType.SIXTEENTH;
            case 32 -> NoteType.THIRTY_SECOND;
            case 64 -> NoteType.SIXTY_FOURTH;
            case 128 -> NoteType.HUNDRED_TWENTY_EIGHTH;
            default -> NoteType.QUARTER;
        };
    }

    /** The LilyPond duration number of a note type. */
    static int typeToNumber(NoteType type) {
        return switch (type) {
            case WHOLE -> 1;
            case HALF -> 2;
            case QUARTER -> 4;
            case EIGHTH -> 8;
            case SIXTEENTH -> 16;
            case THIRTY_SECOND -> 32;
            case SIXTY_FOURTH -> 64;
            case HUNDRED_TWENTY_EIGHTH -> 128;
            // Values outside LilyPond's normal range fall back to a quarter
            // — the model still stores the exact duration, only the display
            // suffix is approximated.
            default -> 4;
        };
    }

    /** The literal duration suffix (no leading pitch) for the given type + dots. */
    static String suffixFor(NoteType type, int dots) {
        StringBuilder sb = new StringBuilder();
        sb.append(typeToNumber(type));
        for (int i = 0; i < dots; i++) {
            sb.append('.');
        }
        return sb.toString();
    }
}
