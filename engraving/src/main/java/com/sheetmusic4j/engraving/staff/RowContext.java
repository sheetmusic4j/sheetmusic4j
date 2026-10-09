package com.sheetmusic4j.engraving.staff;

import java.util.List;

import com.sheetmusic4j.engraving.analysis.MeasureTimeline;
import com.sheetmusic4j.engraving.layout.LayoutOptions;

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
 */
public record RowContext(LayoutOptions options, boolean firstRow, int start, int endExclusive,
                         List<Double> rowWidths, double rowHeader, MeasureTimeline timeline,
                         double pixelsPerQuarter) {
}
