package com.sheetmusic4j.engraving;

/**
 * Semantic classification for both {@link TextPlacement text} and
 * {@link GlyphPlacement glyph} placements. Viewers can hide or show categories
 * independently (e.g. show notes but hide dynamics).
 *
 * <p>Categories are a superset of what earlier tasks used for text only. New
 * marking types added by follow-up tasks (rehearsal marks, chord symbols,
 * segno/coda, ...) plug into the same visibility toggles.
 */
public enum MarkingCategory {
    /**
     * Ordinary staff content: notes, rests, stems, beams, ties, clefs, key
     * signatures, time signatures, accidentals. The default for
     * {@link GlyphPlacement} so pre-existing call sites are unaffected by the
     * category-aware visibility filter.
     */
    NOTE,
    TITLE,
    SUBTITLE,
    CREATOR,
    LYRIC,
    DIRECTION,
    TEMPO,
    DYNAMIC,
    /** Reserved for a follow-up task. */
    REHEARSAL,
    /** Reserved for a follow-up task. */
    CHORD_SYMBOL,
    /**
     * Instrument label emitted at the left of a system for each part
     * (full {@code <part-name>} on the first system,
     * {@code <part-abbreviation>} on continuation systems).
     */
    PART_LABEL
    }
