package com.sheetmusic4j.engraving.placement;

/**
 * A positioned slur connecting the first and last note of a phrase.
 * Rendered identically to {@link TiePlacement} (a shallow two-segment
 * curve) but kept as a distinct type since slurs and ties are semantically
 * different markings that may diverge in styling later.
 *
 * @param x1      x of the slur start
 * @param y1      y of the slur start
 * @param x2      x of the slur end
 * @param y2      y of the slur end
 * @param curveUp whether the slur's peak curves upward (false = downward)
 */
public record SlurPlacement(double x1, double y1, double x2, double y2, boolean curveUp) {
}
