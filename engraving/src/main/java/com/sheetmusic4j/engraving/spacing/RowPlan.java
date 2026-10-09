package com.sheetmusic4j.engraving.spacing;

import java.util.ArrayList;
import java.util.List;

import com.sheetmusic4j.core.model.Measure;
import com.sheetmusic4j.engraving.layout.LayoutMode;
import com.sheetmusic4j.engraving.layout.LayoutOptions;
import com.sheetmusic4j.engraving.analysis.MeasureTimeline;
import com.sheetmusic4j.engraving.analysis.PartInfo;

/**
 * How the score's measures are split into rows (systems) and how wide each
 * measure is before per-row stretching.
 *
 * @param rows             {@code [start, endExclusive)} measure ranges, one per row
 * @param baseWidths       nominal width per measure index
 * @param staffWidth       width available to a staff (header + measures)
 * @param systemWidth      total width of a system including margins
 * @param pixelsPerQuarter global time scale in STRIP mode, {@code 0} in PAGE mode
 */
public record RowPlan(List<int[]> rows, List<Double> baseWidths, double staffWidth, double systemWidth,
               double pixelsPerQuarter) {

    /**
     * Plan the rows for the given layout mode.
     *
     * <p>In STRIP mode the horizontal axis is time-proportional: every
     * measure is sized to its musical duration via one global
     * pixels-per-quarter, so a constant-tempo playback cursor moves at
     * constant pixel speed (equal note durations occupy equal width, with no
     * notation "fill" gap before a barline), and everything sits on one row.
     * In PAGE mode measures are sized by their content weight and packed
     * greedily into rows of {@link LayoutOptions#systemWidth()}.
     */
    public static RowPlan of(List<PartInfo> parts, MeasureTimeline timeline, int measureCount,
                      double contentLeft, LayoutOptions options) {
        double firstSystemHeader = HorizontalSpacing.maxHeaderAdvanceAtRowStart(parts, 0, options);
        List<Double> sharedMinWidths = HorizontalSpacing.sharedMeasureMinWidths(parts, measureCount, options);
        if (options.layoutMode() == LayoutMode.STRIP) {
            double pixelsPerQuarter = HorizontalSpacing.stripPixelsPerQuarter(parts, options);
            List<Double> baseWidths = new ArrayList<>(measureCount);
            double contentSum = 0.0;
            for (int m = 0; m < measureCount; m++) {
                double durQ = timeline.duration(m);
                double w = durQ > 0 ? durQ * pixelsPerQuarter : sharedMinWidths.get(m);
                baseWidths.add(w);
                contentSum += w;
            }
            double systemWidth = contentLeft + firstSystemHeader + contentSum + options.rightMargin();
            double staffWidth = firstSystemHeader + contentSum;
            List<int[]> rows = new ArrayList<>();
            rows.add(new int[]{0, measureCount});
            return new RowPlan(rows, baseWidths, staffWidth, systemWidth, pixelsPerQuarter);
        }
        List<Double> sharedWeights = HorizontalSpacing.sharedMeasureWeights(parts, measureCount);
        double systemWidth = options.systemWidth();
        double staffWidth = options.systemWidth() - contentLeft - options.rightMargin();
        List<Double> baseWidths = HorizontalSpacing.distributeWidths(sharedWeights,
                Math.max(1.0, staffWidth - firstSystemHeader), sharedMinWidths);
        List<int[]> rows = computeRowRanges(baseWidths, staffWidth, firstSystemHeader, parts, options);
        return new RowPlan(rows, baseWidths, staffWidth, systemWidth, 0.0);
    }

    /**
     * Greedy row-break: pack measures into rows so the sum of their base
     * widths plus the row's header does not exceed the available staff
     * width. Each row contains at least one measure.
     */
    private static List<int[]> computeRowRanges(List<Double> baseWidths, double staffWidth,
                                                double firstSystemHeader, List<PartInfo> parts,
                                                LayoutOptions options) {
        List<int[]> rows = new ArrayList<>();
        int n = baseWidths.size();
        if (n == 0) {
            return rows;
        }
        int start = 0;
        double header = firstSystemHeader;
        double cursor = header;
        for (int i = 0; i < n; i++) {
            double w = baseWidths.get(i);
            if (i > start && (cursor + w > staffWidth + 1e-6 || forcesBreak(parts, i))) {
                rows.add(new int[]{start, i});
                start = i;
                header = HorizontalSpacing.maxHeaderAdvanceAtRowStart(parts, start, options);
                cursor = header + w;
            } else {
                cursor += w;
            }
        }
        rows.add(new int[]{start, n});
        return rows;
    }

    /**
     * Whether any part explicitly requires a fresh system to start at
     * measure index {@code i} (e.g. an ABC mid-tune section title), rather
     * than being packed onto the end of the previous row.
     */
    private static boolean forcesBreak(List<PartInfo> parts, int i) {
        for (PartInfo p : parts) {
            List<Measure> measures = p.measures();
            if (i < measures.size() && measures.get(i).forceSystemBreak()) {
                return true;
            }
        }
        return false;
    }
}
