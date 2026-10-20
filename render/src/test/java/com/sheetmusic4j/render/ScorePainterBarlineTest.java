package com.sheetmusic4j.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.sheetmusic4j.core.model.Barline;
import com.sheetmusic4j.engraving.glyph.BarlineGeometry;
import com.sheetmusic4j.engraving.layout.LayoutResult;
import com.sheetmusic4j.engraving.layout.MeasureLayout;
import com.sheetmusic4j.engraving.layout.StaffLayout;
import com.sheetmusic4j.engraving.layout.SystemLayout;

class ScorePainterBarlineTest {

    private static final double GAP = 10.0;

    /** Vertical lines drawn: their x and the line width in effect. */
    private record Line(double x, double width) {
    }

    private static List<Line> paint(MeasureLayout... measures) {
        StaffLayout staff = new StaffLayout(0, 100, 400, GAP, List.of(measures), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        LayoutResult layout = new LayoutResult(List.of(new SystemLayout(0, 100, 400, List.of(staff))), 400, 300);
        List<Line> lines = new ArrayList<>();
        double[] width = {1.0};
        RenderSurface surface = new RenderSurface() {
            @Override public void setStroke(RenderColor color) { }
            @Override public void setFill(RenderColor color) { }
            @Override public void setLineWidth(double w) { width[0] = w; }
            @Override public void fillRect(double x, double y, double w, double h) { }
            @Override public void strokeLine(double x1, double y1, double x2, double y2) {
                if (x1 == x2) {
                    lines.add(new Line(x1, width[0]));
                }
            }
            @Override public void fillOval(double x, double y, double w, double h) { }
            @Override public void strokeOval(double x, double y, double w, double h) { }
            @Override public void strokeText(String text, double x, double y) { }
        };
        new ScorePainter().paint(surface, layout, 400, 300);
        return lines;
    }

    @Test
    void finalBarlineIsThinThenThickInsideTheMeasure() {
        List<Line> lines = paint(new MeasureLayout(1, 0, 400, 0, 4,
                new Barline(Barline.Style.FINAL, Barline.Repeat.NONE), false, null));
        Line thick = lines.stream().filter(l -> l.width() == BarlineGeometry.THICK * GAP).findFirst().orElseThrow();
        assertEquals(400 - BarlineGeometry.THICK * GAP / 2, thick.x(), 1e-9, "thick line ends flush with the staff");
        assertTrue(lines.stream().anyMatch(l -> l.width() == BarlineGeometry.THIN * GAP && l.x() < thick.x()));
    }

    @Test
    void plainBarlineBeforeAnOpeningRepeatIsNotDrawnTwice() {
        List<Line> lines = paint(
                new MeasureLayout(1, 0, 200, 0, 4, null, false, null),
                new MeasureLayout(2, 200, 200, 4, 8, null, true, null));
        long thinAtBoundary = lines.stream()
                .filter(l -> l.width() == BarlineGeometry.THIN * GAP && Math.abs(l.x() - 200) < BarlineGeometry.THIN * GAP)
                .count();
        assertEquals(0, thinAtBoundary, "the repeat's thick line replaces the plain barline");
        assertTrue(lines.stream().anyMatch(l -> l.width() == BarlineGeometry.THICK * GAP));
    }
}
