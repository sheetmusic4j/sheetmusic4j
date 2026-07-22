package com.sheetmusic4j.engraving.placement;

import com.sheetmusic4j.core.model.MusicElement;
import com.sheetmusic4j.engraving.glyph.Glyph;
import com.sheetmusic4j.engraving.glyph.MarkingCategory;

/**
 * A single positioned glyph.
 *
 * @param x           horizontal position (left/anchor) in layout units
 * @param y           vertical position (baseline/center) in layout units
 * @param glyph       the semantic glyph to draw
 * @param staffStep   vertical position relative to the staff, measured in half staff
 *                    spaces from the top staff line (0 = top line, positive = downward).
 *                    Useful for ledger-line computation by a renderer.
 * @param category    semantic classification of this glyph; used by viewers to
 *                    toggle categories of glyphs on and off. Defaults to
 *                    {@link MarkingCategory#NOTE} for the four-arg constructor
 *                    so existing call sites are unaffected by the visibility
 *                    filter.
 * @param elementRef  identity link back to the {@link MusicElement} in the
 *                    source score that this glyph belongs to (notehead, stem,
 *                    accidental, flag, augmentation dot of a note; the rest
 *                    element for a rest glyph). {@code null} for score-level
 *                    glyphs that don't belong to any specific element
 *                    (clefs, time-signature digits, key-signature
 *                    accidentals). Used by the painter's colour provider to
 *                    highlight all glyphs of a single note as a unit.
 */
public record GlyphPlacement(double x, double y, Glyph glyph, int staffStep,
                             MarkingCategory category, MusicElement elementRef) {

    public GlyphPlacement {
        if (category == null) {
            category = MarkingCategory.NOTE;
        }
    }

    /**
     * Backwards-compatible constructor for callers that pre-date the
     * element identity link. Sets {@code elementRef} to {@code null} so no
     * per-element highlighting applies to the glyph.
     */
    public GlyphPlacement(double x, double y, Glyph glyph, int staffStep, MarkingCategory category) {
        this(x, y, glyph, staffStep, category, null);
    }

    /**
     * Backwards-compatible constructor for callers that pre-date glyph
     * categorization. Defaults the category to {@link MarkingCategory#NOTE}
     * and {@link #elementRef} to {@code null}.
     */
    public GlyphPlacement(double x, double y, Glyph glyph, int staffStep) {
        this(x, y, glyph, staffStep, MarkingCategory.NOTE, null);
    }

    /** Return a copy of this placement bound to the given source element. */
    public GlyphPlacement withElementRef(MusicElement element) {
        return new GlyphPlacement(x, y, glyph, staffStep, category, element);
    }
}
