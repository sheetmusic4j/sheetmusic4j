package com.sheetmusic4j.engraving;

/**
 * A vertical grouping mark drawn at the left of a system that visually
 * binds a run of staves together. The concrete visual shape is chosen via
 * {@link BracketShape}.
 *
 * <p>This task emits {@link BracketShape#BRACE} for every part that occupies
 * more than one staff (the implicit grand-staff brace). {@link
 * BracketShape#BRACKET} and {@link BracketShape#LINE} are reserved for the
 * upcoming {@code <part-group>} parser, which will reuse the same record.
 *
 * @param x       horizontal position (typically slightly left of the system
 *                content edge)
 * @param topY    y of the top edge (top-line of the first grouped staff)
 * @param bottomY y of the bottom edge (bottom-line of the last grouped staff)
 * @param shape   visual shape to draw
 */
public record BracketPlacement(double x, double topY, double bottomY, BracketShape shape) {

    /**
     * Visual shape of a {@link BracketPlacement}. Renderers pick a concrete
     * primitive for each value.
     */
    public enum BracketShape {
        /** Curly brace ("{"), used for grand-staff (piano) grouping. */
        BRACE,
        /**
         * Square bracket with ornamental curled tips, conventionally used
         * for instrument families in orchestral scores.
         */
        BRACKET,
        /**
         * Plain rectangular bracket without ornamental tips. Matches
         * MusicXML's {@code <group-symbol>square</group-symbol>}.
         */
        SQUARE,
        /**
         * Single thin vertical line without serifs, used when the source
         * explicitly requests a bracket style of {@code line}.
         */
        LINE
    }
}
