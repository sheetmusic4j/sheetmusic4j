package com.sheetmusic4j.engraving;

/**
 * A piece of free-form text (title, subtitle, composer credit, direction word,
 * ...) positioned somewhere on the engraved page. Used for score-level
 * decorations that do not belong to a specific {@link StaffLayout}.
 *
 * @param text     the string to render
 * @param x        horizontal anchor (interpreted with {@code align})
 * @param y        baseline y
 * @param fontSize font em-size used to draw the text
 * @param align    horizontal alignment relative to {@code x}
 * @param category semantic classification of this text; used by viewers to
 *                 toggle categories of text on and off
 */
public record TextPlacement(String text, double x, double y, double fontSize, Align align, MarkingCategory category) {

    /**
     * Backwards-compatible constructor for callers that did not yet classify
     * their placements. Defaults to {@link MarkingCategory#TITLE}.
     */
    public TextPlacement(String text, double x, double y, double fontSize, Align align) {
        this(text, x, y, fontSize, align, MarkingCategory.TITLE);
    }

    /**
     * Horizontal alignment of a {@link TextPlacement} relative to its {@code x}
     * anchor.
     */
    public enum Align {
        LEFT,
        CENTER,
        RIGHT
    }
}
