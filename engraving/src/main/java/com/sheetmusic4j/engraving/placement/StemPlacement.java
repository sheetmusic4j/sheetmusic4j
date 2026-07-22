package com.sheetmusic4j.engraving.placement;

import com.sheetmusic4j.core.model.MusicElement;

/**
 * A positioned note stem, drawn as a straight vertical line from the
 * notehead-side endpoint ({@code y1}) to the tip ({@code y2}).
 *
 * <p>Unlike a fixed-length glyph, this carries its own endpoints so a
 * beamed note's stem can be lengthened to reach the beam's actual height
 * (shared across every note in the beam run and clamped to clear the
 * staff), rather than always extending a standard distance from its own
 * notehead.
 *
 * @param x          horizontal position
 * @param y1         y at the notehead side
 * @param y2         y at the tip (beam/flag side)
 * @param elementRef identity link back to the {@link MusicElement} that
 *                   owns this stem, used by the painter's colour provider
 *                   so a highlighted note tints its stem alongside its
 *                   notehead. {@code null} for stems produced by callers
 *                   pre-dating the element link.
 */
public record StemPlacement(double x, double y1, double y2, MusicElement elementRef) {

    /**
     * Backwards-compatible constructor for callers that pre-date the
     * element identity link.
     */
    public StemPlacement(double x, double y1, double y2) {
        this(x, y1, y2, null);
    }
}
