package com.sheetmusic4j.engraving;

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
    STEM,
    CLEF_G,
    CLEF_F,
    CLEF_C,
    TIME_DIGIT,
    REST_WHOLE,
    REST_HALF,
    REST_QUARTER,
    REST_EIGHTH
}
