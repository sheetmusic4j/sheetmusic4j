package com.sheetmusic4j.engraving.placement;

/**
 * A positioned tuplet indicator spanning the notes/rests of one tuplet run.
 *
 * @param x1      x of the run's first element
 * @param x2      x of the run's last element
 * @param y       y at which the number (and bracket, if drawn) sits
 * @param number  the displayed count (MusicXML {@code actual-notes}, e.g. 6
 *                for a sextuplet)
 * @param bracket whether a bracket line should be drawn above/below the run
 *                in addition to the number (commonly {@code false} for
 *                already-beamed groups)
 * @param above   whether the indicator sits above the run (stems up) or
 *                below it (stems down); the bracket's end ticks point
 *                towards the notes
 */
public record TupletPlacement(double x1, double x2, double y, int number, boolean bracket, boolean above) {

    /** A tuplet indicator above its run. */
    public TupletPlacement(double x1, double x2, double y, int number, boolean bracket) {
        this(x1, x2, y, number, bracket, true);
    }
}
