package com.sheetmusic4j.engraving;

/**
 * A positioned tie connecting two notes of the same pitch.
 *
 * @param x1     x of the tie start
 * @param y1     y of the tie start
 * @param x2     x of the tie end
 * @param y2     y of the tie end
 * @param curveUp whether the tie's peak curves upward (false = downward)
 */
public record TiePlacement(double x1, double y1, double x2, double y2, boolean curveUp) {
}
