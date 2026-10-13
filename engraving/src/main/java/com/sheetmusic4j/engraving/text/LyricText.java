package com.sheetmusic4j.engraving.text;

import com.sheetmusic4j.core.model.Lyric;
import com.sheetmusic4j.core.model.Syllabic;

/**
 * How a lyric syllable is drawn: its text and font size. Shared by
 * horizontal spacing (which reserves room for the syllable) and the staff
 * annotation layout (which places it), so both agree on its width.
 */
public final class LyricText {

    /**
     * Multiplier applied to the staff-line gap to derive the lyric font size.
     */
    public static final double FONT_SIZE_GAPS = 1.4;

    private LyricText() {
    }

    /**
     * A syllable as drawn: its text plus a trailing hyphen when the word
     * continues.
     */
    public static String of(Lyric lyric) {
        return lyric.text() + syllabicSuffix(lyric.syllabic());
    }

    /**
     * Trailing character appended to a syllable's rendered text to signal a
     * mid-word hyphenation. A proper centered hyphen glyph between adjacent
     * syllables is a follow-up refinement.
     */
    private static String syllabicSuffix(Syllabic syllabic) {
        return switch (syllabic) {
            case BEGIN, MIDDLE -> "-";
            case SINGLE, END -> "";
        };
    }
}
