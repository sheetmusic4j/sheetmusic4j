package com.sheetmusic4j.engraving;

/**
 * The horizontal extent of a single measure on a staff.
 *
 * @param number the measure number
 * @param x      left edge of the measure
 * @param width  width of the measure
 */
public record MeasureLayout(int number, double x, double width) {

    public double right() {
        return x + width;
    }
}
