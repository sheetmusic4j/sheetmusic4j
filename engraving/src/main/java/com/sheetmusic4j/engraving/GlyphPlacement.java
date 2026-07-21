package com.sheetmusic4j.engraving;

/**
 * A single positioned glyph.
 *
 * @param x         horizontal position (left/anchor) in layout units
 * @param y         vertical position (baseline/center) in layout units
 * @param glyph     the semantic glyph to draw
 * @param staffStep vertical position relative to the staff, measured in half staff
 *                  spaces from the top staff line (0 = top line, positive = downward).
 *                  Useful for ledger-line computation by a renderer.
 * @param category  semantic classification of this glyph; used by viewers to
 *                  toggle categories of glyphs on and off. Defaults to
 *                  {@link MarkingCategory#NOTE} for the four-arg constructor
 *                  so existing call sites are unaffected by the visibility
 *                  filter.
 */
public record GlyphPlacement(double x, double y, Glyph glyph, int staffStep, MarkingCategory category) {

    public GlyphPlacement {
        if (category == null) {
            category = MarkingCategory.NOTE;
        }
    }

    /**
     * Backwards-compatible constructor for callers that pre-date glyph
     * categorization. Defaults the category to {@link MarkingCategory#NOTE}.
     */
    public GlyphPlacement(double x, double y, Glyph glyph, int staffStep) {
        this(x, y, glyph, staffStep, MarkingCategory.NOTE);
    }
}
