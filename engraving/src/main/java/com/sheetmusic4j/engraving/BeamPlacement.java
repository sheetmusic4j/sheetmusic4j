package com.sheetmusic4j.engraving;

/**
 * A positioned beam segment connecting the stem tips of two beamed notes.
 *
 * <p>Level 1 corresponds to a single (primary) beam (eighth-note flag
 * equivalent). Higher levels stack below (for stem-up notes) or above (for
 * stem-down notes) to encode sixteenth, thirty-second, and further
 * subdivisions.
 *
 * @param x1     stem-tip x of the first beamed note
 * @param y1     stem-tip y of the first beamed note
 * @param x2     stem-tip x of the last beamed note
 * @param y2     stem-tip y of the last beamed note
 * @param level  1 for the primary beam, 2 for the second (sixteenth), etc.
 * @param stemUp whether the beamed group has stems up
 */
public record BeamPlacement(double x1, double y1, double x2, double y2, int level, boolean stemUp) {
}
