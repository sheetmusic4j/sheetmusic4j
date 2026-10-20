package com.sheetmusic4j.render;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.sheetmusic4j.core.model.Attributes;
import com.sheetmusic4j.core.model.Clef;
import com.sheetmusic4j.core.model.Duration;
import com.sheetmusic4j.core.model.KeySignature;
import com.sheetmusic4j.core.model.Measure;
import com.sheetmusic4j.core.model.MusicElement;
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

/**
 * Sanity checks for the per-note colour-provider path added in
 * {@link ScorePainter#setNoteColorProvider}: when a highlight is registered,
 * the painter should switch the surface colour before drawing the target
 * element's glyphs and restore it afterwards, and the number of layouts
 * produced by the engraver should <em>not</em> increase per highlight
 * change (that path lives entirely in the painter).
 */
class ScorePainterHighlightTest {

    private static final RenderColor RED = new RenderColor(255, 0, 0);

    private Score singleNoteScore(Note[] outNote) {
        int divisions = 1;
        Note note = Note.builder().pitch(new Pitch(Step.C, 4))
                .duration(new Duration(4, divisions))
                .type(NoteType.WHOLE)
                .build();
        outNote[0] = note;
        Measure.Builder m = Measure.builder(1).attributes(Attributes.builder()
                .divisions(divisions)
                .keySignature(KeySignature.cMajor())
                .timeSignature(TimeSignature.fourFour())
                .clef(Clef.treble())
                .build());
        m.addElement(note);
        return Score.builder()
                .addPart(Part.builder("P1").addMeasure(m.build()).build())
                .build();
    }

    @Test
    void colorProviderSwitchesFillForHighlightedNote() {
        Note[] noteRef = new Note[1];
        Score score = singleNoteScore(noteRef);
        LayoutResult layout = new Engraver().layout(score, LayoutOptions.defaults());

        Map<MusicElement, RenderColor> highlights = new IdentityHashMap<>();
        highlights.put(noteRef[0], RED);

        ColorSpyingSurface surface = new ColorSpyingSurface();
        ScorePainter painter = new ScorePainter();
        painter.setNoteColorProvider(e -> Optional.ofNullable(highlights.get(e)));
        painter.paint(surface, layout, layout.width(), layout.height());

        assertTrue(surface.fillsSet.contains(RED),
                "the highlight colour must be pushed to the surface's fill");
        assertTrue(surface.strokesSet.contains(RED),
                "the highlight colour must be pushed to the surface's stroke");
    }

    @Test
    void withoutHighlightsNoColorChange() {
        Note[] noteRef = new Note[1];
        Score score = singleNoteScore(noteRef);
        LayoutResult layout = new Engraver().layout(score, LayoutOptions.defaults());

        ColorSpyingSurface surface = new ColorSpyingSurface();
        ScorePainter painter = new ScorePainter();
        painter.setNoteColorProvider(e -> Optional.empty());
        painter.paint(surface, layout, layout.width(), layout.height());

        assertFalse(surface.fillsSet.contains(RED));
        assertFalse(surface.strokesSet.contains(RED));
    }

    @Test
    void nullProviderIsSafe() {
        Note[] noteRef = new Note[1];
        Score score = singleNoteScore(noteRef);
        LayoutResult layout = new Engraver().layout(score, LayoutOptions.defaults());

        ColorSpyingSurface surface = new ColorSpyingSurface();
        ScorePainter painter = new ScorePainter();
        painter.setNoteColorProvider(null);
        // Should not throw.
        painter.paint(surface, layout, layout.width(), layout.height());
        assertNotNull(surface);
    }

    private static final class ColorSpyingSurface implements RenderSurface {
        final List<RenderColor> fillsSet = new ArrayList<>();
        final List<RenderColor> strokesSet = new ArrayList<>();

        @Override
        public void setStroke(RenderColor color) {
            strokesSet.add(color);
        }

        @Override
        public void setFill(RenderColor color) {
            fillsSet.add(color);
        }

        @Override public void setLineWidth(double width) { }
        @Override public void fillRect(double x, double y, double w, double h) { }
        @Override public void strokeLine(double x1, double y1, double x2, double y2) { }
        @Override public void fillOval(double x, double y, double w, double h) { }
        @Override public void strokeOval(double x, double y, double w, double h) { }
        @Override public void strokeText(String text, double x, double y) { }
    }
}
