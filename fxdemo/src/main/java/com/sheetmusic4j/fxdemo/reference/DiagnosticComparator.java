package com.sheetmusic4j.fxdemo.reference;

import com.sheetmusic4j.engraving.GlyphPlacement;
import com.sheetmusic4j.engraving.LayoutResult;
import com.sheetmusic4j.engraving.MeasureLayout;
import com.sheetmusic4j.engraving.StaffLayout;
import com.sheetmusic4j.fxdemo.ImageSimilarity;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * Performs a step-by-step comparison of a Sheet4j engraving against a reference
 * image (typically produced by {@link WebViewReferenceRenderer}).
 *
 * <p>The comparator produces a {@link Diagnostic} record which carries:
 * <ul>
 *   <li>an overall similarity score,</li>
 *   <li>the detected staff bounding boxes on both sides,</li>
 *   <li>per-measure similarity numbers keyed to the engraved measures,</li>
 *   <li>a "present in reference?" verdict for every {@link GlyphPlacement}.</li>
 * </ul>
 * These numbers turn a single opaque similarity value into a localized report
 * that pinpoints <em>which</em> measure/glyph differs.
 */
public final class DiagnosticComparator {

    /**
     * A per-measure similarity entry.
     */
    public record MeasureDiff(int staffIndex, int measureIndex, int measureNumber,
                              Rectangle renderedRect, Rectangle referenceRect,
                              double similarity) {
    }

    /**
     * A per-glyph presence entry.
     */
    public record GlyphPresence(int staffIndex, int glyphIndex, GlyphPlacement placement,
                                boolean presentInReference, double localInk) {
    }

    /**
     * Aggregated diagnostic result.
     */
    public record Diagnostic(
            double overallSimilarity,
            double renderedInkRatio,
            double referenceInkRatio,
            List<Rectangle> renderedStaves,
            List<Rectangle> referenceStaves,
            List<MeasureDiff> measures,
            List<GlyphPresence> glyphs) {

        public Diagnostic {
            renderedStaves = List.copyOf(renderedStaves);
            referenceStaves = List.copyOf(referenceStaves);
            measures = List.copyOf(measures);
            glyphs = List.copyOf(glyphs);
        }

        /**
         * Returns the worst-performing measures for logging in failure messages.
         */
        public List<MeasureDiff> worstMeasures(int limit) {
            return measures.stream()
                    .sorted((a, b) -> Double.compare(a.similarity(), b.similarity()))
                    .limit(limit)
                    .toList();
        }
    }

    /**
     * Sample window size (in pixels) used to check whether a glyph position has
     * ink in the reference image.
     */
    private static final int GLYPH_WINDOW = 12;

    public Diagnostic compare(BufferedImage rendered, BufferedImage reference, LayoutResult layout) {
        double overall = ImageSimilarity.similarity(rendered, reference);
        double renderedInk = ImageSimilarity.inkRatio(rendered);
        double referenceInk = ImageSimilarity.inkRatio(reference);

        List<Rectangle> renderedStaves = detectRenderedStaves(layout, rendered.getWidth(), rendered.getHeight());
        List<Rectangle> referenceStaves = StaffDetector.detect(reference);

        List<MeasureDiff> measureDiffs = new ArrayList<>();
        List<GlyphPresence> glyphPresences = new ArrayList<>();

        List<StaffLayout> staves = layout.staves();
        for (int si = 0; si < staves.size(); si++) {
            StaffLayout staff = staves.get(si);
            Rectangle renderedStaffBox = staffBox(staff, rendered.getWidth(), rendered.getHeight());
            Rectangle referenceStaffBox = si < referenceStaves.size()
                    ? referenceStaves.get(si)
                    : fallbackReferenceBox(reference, si, staves.size());

            for (int mi = 0; mi < staff.measures().size(); mi++) {
                MeasureLayout measure = staff.measures().get(mi);
                Rectangle renderedRect = clip(new Rectangle(
                        (int) Math.round(measure.x()), renderedStaffBox.y,
                        (int) Math.round(measure.width()), renderedStaffBox.height), rendered);
                Rectangle referenceRect = mapRectangle(renderedRect, renderedStaffBox, referenceStaffBox, reference);
                double sim = cropSimilarity(rendered, renderedRect, reference, referenceRect);
                measureDiffs.add(new MeasureDiff(si, mi, measure.number(), renderedRect, referenceRect, sim));
            }

            for (int gi = 0; gi < staff.glyphs().size(); gi++) {
                GlyphPlacement placement = staff.glyphs().get(gi);
                Rectangle window = mapPointToWindow(placement, renderedStaffBox, referenceStaffBox, reference);
                double localInk = ImageSimilarity.inkRatio(cropSafely(reference, window));
                glyphPresences.add(new GlyphPresence(si, gi, placement, localInk > 0.02, localInk));
            }
        }

        return new Diagnostic(overall, renderedInk, referenceInk,
                renderedStaves, referenceStaves, measureDiffs, glyphPresences);
    }

    private static List<Rectangle> detectRenderedStaves(LayoutResult layout, int width, int height) {
        List<Rectangle> boxes = new ArrayList<>();
        for (StaffLayout staff : layout.staves()) {
            boxes.add(staffBox(staff, width, height));
        }
        return boxes;
    }

    private static Rectangle staffBox(StaffLayout staff, int imageWidth, int imageHeight) {
        int y = (int) Math.round(staff.y());
        int h = (int) Math.round(staff.lineGap() * 4);
        int x = (int) Math.round(staff.x());
        int w = (int) Math.round(staff.width());
        Rectangle r = new Rectangle(x, y, w, h);
        return clip(r, imageWidth, imageHeight);
    }

    private static Rectangle fallbackReferenceBox(BufferedImage reference, int index, int total) {
        int bandHeight = reference.getHeight() / Math.max(1, total);
        return new Rectangle(0, index * bandHeight, reference.getWidth(), bandHeight);
    }

    private static Rectangle mapRectangle(Rectangle sourceRect, Rectangle sourceBox, Rectangle targetBox,
                                          BufferedImage targetImage) {
        if (sourceBox.width <= 0 || sourceBox.height <= 0) {
            return clip(new Rectangle(0, 0, targetImage.getWidth(), targetImage.getHeight()), targetImage);
        }
        double sx = (sourceRect.x - sourceBox.x) / (double) sourceBox.width;
        double sy = (sourceRect.y - sourceBox.y) / (double) sourceBox.height;
        double sw = sourceRect.width / (double) sourceBox.width;
        double sh = sourceRect.height / (double) sourceBox.height;
        int x = targetBox.x + (int) Math.round(sx * targetBox.width);
        int y = targetBox.y + (int) Math.round(sy * targetBox.height);
        int w = (int) Math.round(sw * targetBox.width);
        int h = (int) Math.round(sh * targetBox.height);
        return clip(new Rectangle(x, y, w, h), targetImage);
    }

    private static Rectangle mapPointToWindow(GlyphPlacement placement, Rectangle sourceBox, Rectangle targetBox,
                                              BufferedImage targetImage) {
        if (sourceBox.width <= 0 || sourceBox.height <= 0) {
            return new Rectangle(0, 0, GLYPH_WINDOW, GLYPH_WINDOW);
        }
        double sx = (placement.x() - sourceBox.x) / (double) sourceBox.width;
        double sy = (placement.y() - sourceBox.y) / (double) sourceBox.height;
        int cx = targetBox.x + (int) Math.round(sx * targetBox.width);
        int cy = targetBox.y + (int) Math.round(sy * targetBox.height);
        return clip(new Rectangle(cx - GLYPH_WINDOW / 2, cy - GLYPH_WINDOW / 2,
                GLYPH_WINDOW, GLYPH_WINDOW), targetImage);
    }

    private static double cropSimilarity(BufferedImage a, Rectangle ra, BufferedImage b, Rectangle rb) {
        BufferedImage cropA = cropSafely(a, ra);
        BufferedImage cropB = cropSafely(b, rb);
        if (cropA == null || cropB == null) {
            return 0.0;
        }
        return ImageSimilarity.similarity(cropA, cropB);
    }

    private static BufferedImage cropSafely(BufferedImage source, Rectangle r) {
        if (r == null || r.width <= 0 || r.height <= 0) {
            return null;
        }
        Rectangle clipped = clip(r, source);
        if (clipped.width <= 0 || clipped.height <= 0) {
            return null;
        }
        return source.getSubimage(clipped.x, clipped.y, clipped.width, clipped.height);
    }

    private static Rectangle clip(Rectangle r, BufferedImage image) {
        return clip(r, image.getWidth(), image.getHeight());
    }

    private static Rectangle clip(Rectangle r, int width, int height) {
        int x = Math.max(0, Math.min(r.x, width));
        int y = Math.max(0, Math.min(r.y, height));
        int right = Math.max(x, Math.min(r.x + r.width, width));
        int bottom = Math.max(y, Math.min(r.y + r.height, height));
        return new Rectangle(x, y, right - x, bottom - y);
    }
}
