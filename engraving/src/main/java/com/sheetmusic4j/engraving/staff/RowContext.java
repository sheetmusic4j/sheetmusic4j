package com.sheetmusic4j.engraving.staff;

import java.util.List;

import com.sheetmusic4j.engraving.analysis.MeasureTimeline;
import com.sheetmusic4j.engraving.layout.LayoutOptions;
import com.sheetmusic4j.engraving.spacing.MeasureColumns;

/**
 * Everything shared by all staves of one row (system).
 *
 * @param options          layout options
 * @param firstRow         whether this is the first row of the layout
 * @param start            first measure index in the row
 * @param endExclusive     measure index after the row's last measure
 * @param rowWidths        width per measure in the row, the first one including the header
 * @param rowHeader        width of the clef/key/time header at the row start
 * @param timeline         musical onset and duration per measure index
 * @param pixelsPerQuarter global time scale in STRIP mode, {@code 0} in PAGE mode
 * @param columns          onset columns of every measure index of the score
 * @param contentOffsets   per measure in the row, the offset from the
 *                         measure's left edge at which every staff starts
 *                         placing notes - the latest across all staves
 */
public record RowContext(LayoutOptions options, boolean firstRow, int start, int endExclusive,
                         List<Double> rowWidths, double rowHeader, MeasureTimeline timeline,
                         double pixelsPerQuarter, List<MeasureColumns> columns, double[] contentOffsets) {

    /**
     * Onset columns of measure {@code measureIdx}.
     */
    public MeasureColumns columns(int measureIdx) {
        return columns.get(measureIdx);
    }

    /**
     * Offset from the left edge of measure {@code measureIdx} (which must be
     * in this row) at which notes are placed.
     */
    public double contentOffset(int measureIdx) {
        return contentOffsets[measureIdx - start];
    }

    /**
     * Whether the row uses time-proportional STRIP placement.
     */
    public boolean isStrip() {
        return pixelsPerQuarter > 0;
    }
}
