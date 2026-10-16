package com.sheetmusic4j.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

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

/** {@link ScorePainter#setRevealTime} paints only the music that has started, and {@link ScorePainter#setInkColor}. */
class ScorePainterRevealTest {

    /** Records the elements painted, how many primitives were drawn and the colours used. */
    private static final class Recorder implements RenderSurface {
        final Set<Object> elements = Collections.newSetFromMap(new IdentityHashMap<>());
        final List<RenderColor> colors = new ArrayList<>();
        int primitives;

        @Override public void setStroke(RenderColor c) { colors.add(c); }
        @Override public void setFill(RenderColor c) { colors.add(c); }
        @Override public void setLineWidth(double w) { }
        @Override public void fillRect(double x, double y, double w, double h) { primitives++; }
        @Override public void strokeLine(double a, double b, double c, double d) { primitives++; }
        @Override public void fillOval(double x, double y, double w, double h) { primitives++; }
        @Override public void strokeOval(double x, double y, double w, double h) { primitives++; }
        @Override public void strokeText(String t, double x, double y) { primitives++; }
        @Override public void beginElement(Object source) { elements.add(source); }
    }

    private final List<Note> notes = new ArrayList<>();

    /** Two 4/4 measures of eighth notes (beamed in pairs or more) on one strip. */
    private LayoutResult layout() {
        Part.Builder part = Part.builder("P1");
        for (int m = 1; m <= 2; m++) {
            Measure.Builder measure = Measure.builder(m);
            if (m == 1) {
                measure.attributes(Attributes.builder().divisions(2).keySignature(KeySignature.cMajor())
                        .timeSignature(TimeSignature.fourFour()).clef(Clef.treble()).build());
            }
            for (int i = 0; i < 8; i++) {
                Note note = Note.builder().pitch(new Pitch(Step.values()[i % 7], 4))
                        .duration(new Duration(1, 2)).build();
                notes.add(note);
                measure.addElement(note);
            }
            part.addMeasure(measure.build());
        }
        Score score = Score.builder().addPart(part.build()).build();
        return new Engraver().layout(score, LayoutOptions.defaults().toBuilder()
                .layoutMode(LayoutMode.STRIP).showTitleTexts(false).build());
    }

    private static Recorder paint(ScorePainter painter, LayoutResult layout) {
        Recorder recorder = new Recorder();
        painter.paint(recorder, layout, layout.width(), layout.height());
        return recorder;
    }

    @Test
    void everythingIsPaintedByDefault() {
        LayoutResult layout = layout();
        Recorder recorder = paint(new ScorePainter(), layout);
        assertTrue(recorder.elements.containsAll(notes));
    }

    @Test
    void onlyStartedNotesArePainted() {
        LayoutResult layout = layout();
        ScorePainter painter = new ScorePainter();
        painter.setRevealTime(1.0);
        Recorder recorder = paint(painter, layout);
        // Eighths start at 0, 0.5, 1.0, ...: the first three are revealed.
        assertTrue(recorder.elements.containsAll(notes.subList(0, 3)));
        for (Note hidden : notes.subList(3, notes.size())) {
            assertTrue(!recorder.elements.contains(hidden), "not yet started: " + hidden);
        }
    }

    @Test
    void revealGrowsTheInkAndStaffLinesStay() {
        LayoutResult layout = layout();
        ScorePainter painter = new ScorePainter();
        painter.setRevealTime(-1);
        int none = paint(painter, layout).primitives;
        assertTrue(none > 5, "staff lines and barlines are painted before anything starts");
        painter.setRevealTime(2);
        int some = paint(painter, layout).primitives;
        painter.setRevealTime(Double.NaN);
        int all = paint(painter, layout).primitives;
        assertTrue(none < some && some < all, none + " < " + some + " < " + all);
        painter.setRevealTime(Double.POSITIVE_INFINITY);
        assertEquals(all, paint(painter, layout).primitives);
    }

    @Test
    void inkColorReplacesBlack() {
        LayoutResult layout = layout();
        ScorePainter painter = new ScorePainter();
        RenderColor ink = new RenderColor(10, 20, 30);
        painter.setInkColor(ink);
        Recorder recorder = paint(painter, layout);
        assertTrue(recorder.colors.contains(ink));
        assertTrue(!recorder.colors.contains(RenderColor.BLACK));
        painter.setInkColor(null);
        assertEquals(RenderColor.BLACK, painter.getInkColor());
    }
}
