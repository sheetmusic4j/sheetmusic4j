package com.sheetmusic4j.engraving;

/**
 * A vertical barline that spans multiple staves at the left edge (or any
 * shared vertical anchor) of a {@link SystemLayout}. Distinct from the
 * per-measure barlines drawn inside {@link StaffLayout}: those live in the
 * staff, while a {@code SystemBarline} runs across the whole system.
 *
 * <p>This task uses only {@link LineStyle#THIN} for the single "system left
 * barline" that groups all staves of a multi-part system. {@link
 * LineStyle#THICK} is reserved for future group barlines
 * (e.g. {@code <part-group>}-driven strong bracketing).
 *
 * @param x       horizontal position in layout units
 * @param topY    y of the top of the barline (top-line of the first staff)
 * @param bottomY y of the bottom of the barline (bottom-line of the last staff)
 * @param style   stroke weight
 */
public record SystemBarline(double x, double topY, double bottomY, LineStyle style) {

    /**
     * Stroke weight of a {@link SystemBarline}. Renderers pick concrete
     * pixel weights consistent with the surrounding staff-line thickness.
     */
    public enum LineStyle {
        /** Ordinary thin barline; matches per-measure barline thickness. */
        THIN,
        /**
         * Thick barline reserved for future group brackets ({@code <part-group>}).
         * Not emitted in this task.
         */
        THICK
    }
}
