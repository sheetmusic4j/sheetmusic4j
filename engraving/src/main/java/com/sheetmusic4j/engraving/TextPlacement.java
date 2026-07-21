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
 * @param boxed    whether the renderer should stroke a rectangle around the
 *                 text (e.g. for rehearsal marks)
 */
public record TextPlacement(String text, double x, double y, double fontSize, Align align,
                            MarkingCategory category, boolean boxed) {

    /**
     * Convenience constructor: same as the primary one with {@code boxed=false}.
     * Used by all pre-existing callers that never needed boxing.
     */
    public TextPlacement(String text, double x, double y, double fontSize, Align align,
                         MarkingCategory category) {
        this(text, x, y, fontSize, align, category, false);
    }

    /**
     * Backwards-compatible constructor for callers that did not yet classify
     * their placements. Defaults to {@link MarkingCategory#TITLE} and
     * {@code boxed=false}.
     */
    public TextPlacement(String text, double x, double y, double fontSize, Align align) {
        this(text, x, y, fontSize, align, MarkingCategory.TITLE, false);
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
