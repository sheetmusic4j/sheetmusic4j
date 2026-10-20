package com.sheetmusic4j.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
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
import com.sheetmusic4j.core.model.NoteType;
import com.sheetmusic4j.core.model.Part;
import com.sheetmusic4j.core.model.Pitch;
import com.sheetmusic4j.core.model.Score;
import com.sheetmusic4j.core.model.Step;
import com.sheetmusic4j.core.model.TimeSignature;
import com.sheetmusic4j.engraving.Engraver;
import com.sheetmusic4j.engraving.layout.LayoutOptions;
import com.sheetmusic4j.engraving.layout.LayoutResult;
import com.sheetmusic4j.engraving.layout.NoteAnchor;

/** The painter reports each element's primitives between begin/endElement, and hit-testing finds the element. */
class ScorePainterElementGroupTest {

    private static final class Recorder implements RenderSurface {
        final List<Object> begun = new ArrayList<>();
        int depth;
        int drawnInside;

        @Override public void beginElement(Object source) {
            assertEquals(0, depth, "groups must not nest");
            depth++;
            begun.add(source);
        }
        @Override public void endElement() { depth--; }
        private void drew() { if (depth > 0) { drawnInside++; } }
        @Override public void setStroke(RenderColor c) { }
        @Override public void setFill(RenderColor c) { }
        @Override public void setLineWidth(double w) { }
        @Override public void fillRect(double x, double y, double w, double h) { drew(); }
        @Override public void strokeLine(double a, double b, double c, double d) { drew(); }
        @Override public void fillOval(double x, double y, double w, double h) { drew(); }
        @Override public void strokeOval(double x, double y, double w, double h) { drew(); }
        @Override public void strokeText(String t, double x, double y) { drew(); }
    }

    private static Score score(Note note) {
        Measure.Builder m = Measure.builder(1).attributes(Attributes.builder()
                .divisions(1).keySignature(KeySignature.cMajor())
                .timeSignature(TimeSignature.fourFour()).clef(Clef.treble()).build());
        m.addElement(note);
        return Score.builder().addPart(Part.builder("P1").addMeasure(m.build()).build()).build();
    }

    @Test
    void notePrimitivesAreGroupedAndHitTestFindsIt() {
        Note note = Note.builder().pitch(new Pitch(Step.C, 4))
                .duration(new Duration(1, 1)).type(NoteType.QUARTER).build();
        LayoutResult layout = new Engraver().layout(score(note), LayoutOptions.defaults());

        Recorder recorder = new Recorder();
        new ScorePainter().paint(recorder, layout, layout.width(), layout.height());

        assertEquals(0, recorder.depth);
        assertFalse(recorder.begun.isEmpty());
        assertTrue(recorder.begun.stream().allMatch(o -> o == note));
        assertTrue(recorder.drawnInside > 0);

        NoteAnchor anchor = layout.noteAnchors().get(0);
        assertSame(note, layout.hitTest(anchor.x(), anchor.y()).orElseThrow().elementRef());
        assertTrue(layout.hitTest(anchor.x() + 500, anchor.y() + 500).isEmpty());
    }
}
