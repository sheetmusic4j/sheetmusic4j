package com.sheetmusic4j.pdf.recognize;

import com.sheetmusic4j.pdf.extract.PageContent.Glyph;
import com.sheetmusic4j.pdf.symbol.Symbol;

/**
 * A recognised symbol and the glyph it was read from. Glyphs without an outline are placed by their origin.
 */
record Mark(Symbol symbol, Glyph glyph) {

    private boolean hasBox() {
        return glyph.box().width() > 0;
    }

    double left() {
        return hasBox() ? glyph.box().left() : glyph.x();
    }

    double right() {
        return hasBox() ? glyph.box().right() : glyph.x();
    }

    double width() {
        return right() - left();
    }

    double cx() {
        return (left() + right()) / 2;
    }

    /**
     * Vertical centre of the outline.
     */
    double cy() {
        return hasBox() ? glyph.box().centerY() : glyph.y();
    }

    /**
     * Vertical position of the glyph origin, which sits on the symbol's reference line (clef line, accidental pitch).
     */
    double oy() {
        return glyph.y();
    }
}
