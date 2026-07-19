package com.sheet4j.engraving;

/**
 * Tunable parameters controlling how a score is laid out. All measurements are
 * in abstract pixel-like units.
 *
 * @param staffLineGap    vertical gap between two adjacent staff lines
 * @param staffSpacing    vertical gap between the bottom of one part's staff and the top of the next
 * @param systemWidth     total available width for a system
 * @param leftMargin      left margin before the first measure
 * @param rightMargin     right margin after the last measure
 * @param topMargin       top margin above the first staff
 * @param measureMinWidth minimum width for a single measure
 * @param fontSize        default font size hint for glyphs
 */
public record LayoutOptions(
        double staffLineGap,
        double staffSpacing,
        double systemWidth,
        double leftMargin,
        double rightMargin,
        double topMargin,
        double measureMinWidth,
        double fontSize) {

    public static LayoutOptions defaults() {
        return new LayoutOptions(10.0, 60.0, 1000.0, 40.0, 20.0, 40.0, 120.0, 32.0);
    }

    /**
     * Height of a five-line staff (four gaps).
     */
    public double staffHeight() {
        return staffLineGap * 4;
    }
}
