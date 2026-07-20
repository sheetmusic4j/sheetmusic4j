package com.sheetmusic4j.fxdemo.reference;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * Detects five-line staff bounding boxes in a rendered sheet music image using a
 * horizontal-projection algorithm.
 *
 * <p>The algorithm collapses each row to its "ink" (dark-pixel) count, thresholds
 * that projection to find dark rows, then groups clusters of five roughly evenly
 * spaced dark rows into staff bounding boxes.
 *
 * <p>This is a poor-man's structural check: it does not attempt OMR. Its job is
 * simply to answer "did the reference produce N staves that overlap roughly the
 * bounding boxes our engraver produced?".
 */
public final class StaffDetector {

    /**
     * Grayscale threshold below which a pixel counts as "ink".
     */
    private static final int INK_THRESHOLD = 200;

    /**
     * Minimum fraction of a row's width that must be dark for the row to be
     * considered a candidate staff line.
     */
    private static final double DARK_ROW_FRACTION = 0.30;

    /**
     * Number of lines that make a staff.
     */
    private static final int LINES_PER_STAFF = 5;

    private StaffDetector() {
    }

    /**
     * Detect staff bounding boxes in the given image.
     *
     * @param image image to inspect
     * @return detected staff bounding boxes
     */
    public static List<Rectangle> detect(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        boolean[] darkRow = new boolean[height];
        int minDark = (int) Math.round(width * DARK_ROW_FRACTION);

        for (int y = 0; y < height; y++) {
            int ink = 0;
            for (int x = 0; x < width; x++) {
                int rgb = image.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                int gray = (int) Math.round(0.299 * r + 0.587 * g + 0.114 * b);
                if (gray < INK_THRESHOLD) {
                    ink++;
                    if (ink >= minDark) {
                        break;
                    }
                }
            }
            darkRow[y] = ink >= minDark;
        }

        List<int[]> bands = collapseBands(darkRow);
        return groupIntoStaves(bands, width);
    }

    /**
     * Collapse contiguous dark rows into {startY, endY} bands.
     */
    private static List<int[]> collapseBands(boolean[] darkRow) {
        List<int[]> bands = new ArrayList<>();
        int start = -1;
        for (int y = 0; y < darkRow.length; y++) {
            if (darkRow[y]) {
                if (start < 0) {
                    start = y;
                }
            } else if (start >= 0) {
                bands.add(new int[]{start, y - 1});
                start = -1;
            }
        }
        if (start >= 0) {
            bands.add(new int[]{start, darkRow.length - 1});
        }
        return bands;
    }

    private static List<Rectangle> groupIntoStaves(List<int[]> bands, int width) {
        List<Rectangle> staves = new ArrayList<>();
        if (bands.size() < LINES_PER_STAFF) {
            return staves;
        }
        int i = 0;
        while (i <= bands.size() - LINES_PER_STAFF) {
            int[] first = bands.get(i);
            int[] fifth = bands.get(i + LINES_PER_STAFF - 1);
            int span = fifth[1] - first[0];
            int gap = span / (LINES_PER_STAFF - 1);
            // A plausible staff has line spans that are much smaller than the gap.
            boolean plausible = gap > 2 && gap < 60 && (first[1] - first[0]) < gap;
            if (plausible) {
                staves.add(new Rectangle(0, first[0], width, span + 1));
                i += LINES_PER_STAFF;
            } else {
                i++;
            }
        }
        return staves;
    }
}
