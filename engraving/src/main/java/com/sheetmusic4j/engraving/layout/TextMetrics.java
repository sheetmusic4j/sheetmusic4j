package com.sheetmusic4j.engraving.layout;

/**
 * Measures text, so layout can reserve room for lyrics and labels and
 * renderers can align text the same way.
 *
 * <p>The default {@link #estimate()} needs no font and gives identical
 * results everywhere (headless tests, any toolkit); a renderer that can
 * measure its real font supplies its own via
 * {@link LayoutOptions.Builder#textMetrics(TextMetrics)}.
 */
@FunctionalInterface
public interface TextMetrics {

    /**
     * Average character width, as a fraction of the font size, used by
     * {@link #estimate()}.
     */
    double ESTIMATED_CHAR_WIDTH_EM = 0.55;

    /**
     * Width of {@code text} at {@code fontSize}, in layout units.
     */
    double width(String text, double fontSize);

    /**
     * Font-independent estimate: every character is
     * {@link #ESTIMATED_CHAR_WIDTH_EM} of the font size wide.
     */
    static TextMetrics estimate() {
        return (text, fontSize) -> text == null ? 0.0 : ESTIMATED_CHAR_WIDTH_EM * fontSize * text.length();
    }
}
