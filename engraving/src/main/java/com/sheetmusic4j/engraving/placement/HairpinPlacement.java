package com.sheetmusic4j.engraving.placement;

/**
 * A positioned crescendo/diminuendo hairpin (dynamics wedge).
 *
 * @param x1         x of the narrow (closed) end
 * @param x2         x of the wide (open) end
 * @param y          vertical centre line the hairpin opens/closes around
 * @param halfHeight half the maximum opening height, in layout units
 * @param crescendo  {@code true} for an opening "&lt;" hairpin (soft to
 *                   loud), {@code false} for a closing "&gt;" hairpin
 */
public record HairpinPlacement(double x1, double x2, double y, double halfHeight, boolean crescendo) {
}
