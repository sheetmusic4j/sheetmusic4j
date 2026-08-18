package com.sheetmusic4j.core.lilypond;

import com.sheetmusic4j.core.model.Duration;
import com.sheetmusic4j.core.model.NoteType;

/**
 * Parses and emits LilyPond duration suffixes (the number + dots following a
 * pitch, e.g. {@code 4}, {@code 8.}, {@code 16..}).
 *
 * <p>LilyPond allows a duration to be omitted on any note after the first:
 * the previous note's duration carries over. This class both parses that
 * carry-over ({@link #parseSuffix} returns {@code null} when no digits are
 * present) and emits it ({@link #writeSuffix} skips the suffix when it
 * would match the previously-emitted one).
 */
final class LilyPondDuration {

    private LilyPondDuration() {
    }

    /** Result of parsing a duration suffix: the note type + dot count + characters consumed. */
    record Parsed(NoteType type, int dots, int consumed) {
    }

    /**
     * Parse a LilyPond duration suffix starting at {@code pos}. Returns
     * {@code null} when no digits are found — LilyPond's convention is that
     * such a note inherits the previous duration.
     */
    static Parsed parseSuffix(String input, int pos) {
        int i = pos;
        int len = input.length();
        int start = i;
        int num = 0;
        boolean hasDigit = false;
        while (i < len && Character.isDigit(input.charAt(i))) {
            num = num * 10 + (input.charAt(i) - '0');
            hasDigit = true;
            i++;
        }
        if (!hasDigit) {
            return null;
        }
        int dots = 0;
        while (i < len && input.charAt(i) == '.') {
            dots++;
            i++;
        }
        NoteType type = numberToType(num);
        return new Parsed(type, dots, i - start);
    }

    /** Convert a LilyPond numeric duration into a {@link NoteType}. */
    static NoteType numberToType(int n) {
        return switch (n) {
            case 1 -> NoteType.WHOLE;
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

    /** Reverse mapping used by {@link LilyPondWriter}. */
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

    /**
     * Convert a {@link NoteType} + dots + divisions triple into an internal
     * {@link Duration}.
     */
    static Duration toDuration(NoteType type, int dots, int divisions) {
        double quarters = type.quarterValue();
        double dotted = quarters;
        double increment = quarters / 2.0;
        for (int i = 0; i < dots; i++) {
            dotted += increment;
            increment /= 2.0;
        }
        int value = Math.max(1, (int) Math.round(dotted * divisions));
        return new Duration(value, divisions);
    }

    /**
     * Emit a duration suffix onto {@code out} for a note with the given
     * {@link NoteType}, dot count, and (already-computed) {@link Duration}.
     * Returns the suffix that was appended (as a string) so that the caller
     * can detect whether it matched the previously emitted suffix and, in a
     * future call with the same suffix, skip emitting it entirely.
     */
    static String writeSuffix(StringBuilder out, NoteType type, int dots, String previousSuffix) {
        String suffix = suffixFor(type, dots);
        if (suffix.equals(previousSuffix)) {
            return previousSuffix;
        }
        out.append(suffix);
        return suffix;
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
