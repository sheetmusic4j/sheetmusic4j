package com.sheetmusic4j.pdf.recognize;

/**
 * Five staff lines found in the page: where they start and end, and the y of the top and bottom line (y grows
 * downward). A vertical position is expressed in <em>steps</em>: half staff spaces counted up from the bottom line,
 * so lines are even steps and spaces odd ones.
 */
public record Staff(double x0, double x1, double top, double bottom) {

    public double space() {
        return (bottom - top) / 4;
    }

    public double midY() {
        return (top + bottom) / 2;
    }

    /**
     * Distance in y from a point to the staff's lines; zero inside the staff.
     */
    public double distanceTo(double y) {
        return y < top ? top - y : Math.max(0, y - bottom);
    }

    public int stepAt(double y) {
        return (int) Math.round((bottom - y) / (space() / 2));
    }
}
