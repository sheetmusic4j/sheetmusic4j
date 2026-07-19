package com.sheet4j.engraving;

/**
 * A single positioned glyph.
 *
 * @param x         horizontal position (left/anchor) in layout units
 * @param y         vertical position (baseline/center) in layout units
 * @param glyph     the semantic glyph to draw
 * @param staffStep vertical position relative to the staff, measured in half staff
 *                  spaces from the top staff line (0 = top line, positive = downward).
 *                  Useful for ledger-line computation by a renderer.
 */
public record GlyphPlacement(double x, double y, Glyph glyph, int staffStep) {
}
