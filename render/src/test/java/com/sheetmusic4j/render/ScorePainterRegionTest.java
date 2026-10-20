package com.sheetmusic4j.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.sheetmusic4j.core.model.Attributes;
import com.sheetmusic4j.core.model.Clef;
import com.sheetmusic4j.core.model.Duration;
import com.sheetmusic4j.core.model.KeySignature;
import com.sheetmusic4j.core.model.Measure;
import com.sheetmusic4j.core.model.Note;
import com.sheetmusic4j.core.model.Part;
import com.sheetmusic4j.core.model.Pitch;
import com.sheetmusic4j.core.model.Score;
import com.sheetmusic4j.core.model.Step;
import com.sheetmusic4j.core.model.TimeSignature;
import com.sheetmusic4j.engraving.Engraver;
import com.sheetmusic4j.engraving.layout.LayoutMode;
import com.sheetmusic4j.engraving.layout.LayoutOptions;
import com.sheetmusic4j.engraving.layout.LayoutResult;

/** {@link ScorePainter#paintRegion} skips what cannot reach the region and paints the rest as {@code paint} does. */
class ScorePainterRegionTest {

    /** Records the x of every primitive and SMuFL glyph. */
    private static final class Recorder implements RenderSurface {
        final List<Double> xs = new ArrayList<>();

        @Override public void setStroke(RenderColor c) { }
        @Override public void setFill(RenderColor c) { }
        @Override public void setLineWidth(double w) { }
        @Override public void fillRect(double x, double y, double w, double h) { xs.add(x); }
        @Override public void strokeLine(double a, double b, double c, double d) { xs.add(Math.max(a, c)); }
        @Override public void fillOval(double x, double y, double w, double h) { xs.add(x); }
        @Override public void strokeOval(double x, double y, double w, double h) { xs.add(x); }
        @Override public void strokeText(String t, double x, double y) { xs.add(x); }
    }

    /** A long one-line strip: 40 measures of quarter notes. */
    private static LayoutResult strip() {
        Part.Builder part = Part.builder("P1");
        for (int m = 1; m <= 40; m++) {
            Measure.Builder measure = Measure.builder(m);
            if (m == 1) {
                measure.attributes(Attributes.builder().divisions(1).keySignature(KeySignature.cMajor())
                        .timeSignature(TimeSignature.fourFour()).clef(Clef.treble()).build());
            }
            for (int i = 0; i < 4; i++) {
                measure.addElement(Note.builder().pitch(new Pitch(Step.values()[i], 4))
                        .duration(new Duration(1, 1)).build());
            }
            part.addMeasure(measure.build());
        }
        Score score = Score.builder().addPart(part.build()).build();
        return new Engraver().layout(score, LayoutOptions.defaults().toBuilder()
                .layoutMode(LayoutMode.STRIP).showTitleTexts(false).build());
    }

    @Test
    void fullRegionPaintsTheSameAsPaint() {
        LayoutResult layout = strip();
        ScorePainter painter = new ScorePainter();
        painter.setPageColor(null);
        Recorder all = new Recorder();
        painter.paint(all, layout, layout.width(), layout.height());
        Recorder region = new Recorder();
        painter.paintRegion(region, layout, 0, 0, layout.width(), layout.height());
        assertEquals(all.xs.size(), region.xs.size());
    }

    @Test
    void narrowRegionOnlyPaintsNearbyInk() {
        LayoutResult layout = strip();
        ScorePainter painter = new ScorePainter();
        painter.setPageColor(null);
        Recorder all = new Recorder();
        painter.paint(all, layout, layout.width(), layout.height());

        double x0 = layout.width() / 2;
        double x1 = x0 + 200;
        Recorder region = new Recorder();
        painter.paintRegion(region, layout, x0, 0, x1, layout.height());

        assertTrue(region.xs.size() > 5, "the region has ink");
        assertTrue(region.xs.size() * 5 < all.xs.size(), "most of the strip is skipped");
        double margin = 8 * layout.staves().get(0).lineGap() + 50;
        assertTrue(region.xs.stream().allMatch(x -> x >= x0 - margin && x <= x1 + margin),
                "nothing far outside the region is drawn");
    }
}
