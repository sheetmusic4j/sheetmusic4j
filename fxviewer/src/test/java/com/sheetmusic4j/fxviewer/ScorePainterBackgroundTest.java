package com.sheetmusic4j.fxviewer;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
 * Sanity checks for the per-note background provider path added in
 * {@link ScorePainter#setNoteBackgroundProvider}: registering a background
 * causes the painter to call {@link RenderSurface#fillRoundedRect} once
 * per matched element, and doing so requires no re-layout of the score.
 */
class ScorePainterBackgroundTest {

    private static final RenderColor YELLOW =
            new RenderColor(255, 235, 59, 128);
    private static final RenderColor RED =
            new RenderColor(255, 0, 0, 128);

    private Score twoNoteScore(Note[] outNotes) {
        int divisions = 1;
        Measure.Builder m = Measure.builder(1).attributes(Attributes.builder()
                .divisions(divisions)
                .keySignature(KeySignature.cMajor())
                .timeSignature(TimeSignature.fourFour())
                .clef(Clef.treble())
                .build());
        Note n1 = Note.builder().pitch(new Pitch(Step.C, 4))
                .duration(new Duration(2, divisions))
                .type(NoteType.HALF)
                .build();
        Note n2 = Note.builder().pitch(new Pitch(Step.G, 4))
                .duration(new Duration(2, divisions))
                .type(NoteType.HALF)
                .build();
        m.addElement(n1);
        m.addElement(n2);
        outNotes[0] = n1;
        outNotes[1] = n2;
        return Score.builder()
                .addPart(Part.builder("P1").addMeasure(m.build()).build())
                .build();
    }

    @Test
    void backgroundProviderCausesFillRoundedRect() {
        Note[] notes = new Note[2];
        Score score = twoNoteScore(notes);
        LayoutResult layout = new Engraver().layout(score, LayoutOptions.defaults());

        Map<MusicElement, RenderColor> backgrounds = new IdentityHashMap<>();
        backgrounds.put(notes[0], YELLOW);

        BackgroundSpyingSurface surface = new BackgroundSpyingSurface();
        ScorePainter painter = new ScorePainter();
        painter.setNoteBackgroundProvider(e -> Optional.ofNullable(backgrounds.get(e)));
        painter.paint(surface, layout, layout.width(), layout.height());

        assertEquals(1, surface.roundedRects.size(),
                "one background rect must be drawn for one highlighted element");
        assertEquals(YELLOW, surface.roundedRects.get(0).color);
    }

    @Test
    void twoElementsWithDistinctBackgroundsProduceTwoCalls() {
        Note[] notes = new Note[2];
        Score score = twoNoteScore(notes);
        LayoutResult layout = new Engraver().layout(score, LayoutOptions.defaults());

        Map<MusicElement, RenderColor> backgrounds = new IdentityHashMap<>();
        backgrounds.put(notes[0], YELLOW);
        backgrounds.put(notes[1], RED);

        BackgroundSpyingSurface surface = new BackgroundSpyingSurface();
        ScorePainter painter = new ScorePainter();
        painter.setNoteBackgroundProvider(e -> Optional.ofNullable(backgrounds.get(e)));
        painter.paint(surface, layout, layout.width(), layout.height());

        assertEquals(2, surface.roundedRects.size(),
                "two highlighted elements must yield two fillRoundedRect calls");
        List<RenderColor> colors = surface.roundedRects.stream()
                .map(r -> r.color).toList();
        assertTrue(colors.contains(YELLOW));
        assertTrue(colors.contains(RED));
    }

    @Test
    void unknownElementIsNoOp() {
        Note[] notes = new Note[2];
        Score score = twoNoteScore(notes);
        LayoutResult layout = new Engraver().layout(score, LayoutOptions.defaults());

        Map<MusicElement, RenderColor> backgrounds = new IdentityHashMap<>();
        // A note that is not part of the score: should be silently ignored,
        // no exceptions, no fillRoundedRect calls.
        Note stranger = Note.builder().pitch(new Pitch(Step.C, 4))
                .duration(new Duration(1, 1)).build();
        backgrounds.put(stranger, YELLOW);

        BackgroundSpyingSurface surface = new BackgroundSpyingSurface();
        ScorePainter painter = new ScorePainter();
        painter.setNoteBackgroundProvider(e -> Optional.ofNullable(backgrounds.get(e)));
        painter.paint(surface, layout, layout.width(), layout.height());

        assertTrue(surface.roundedRects.isEmpty(),
                "a background for an element that isn't in the score must "
                        + "produce no fillRoundedRect calls");
    }

    @Test
    void nullProviderIsSafe() {
        Note[] notes = new Note[2];
        Score score = twoNoteScore(notes);
        LayoutResult layout = new Engraver().layout(score, LayoutOptions.defaults());

        BackgroundSpyingSurface surface = new BackgroundSpyingSurface();
        ScorePainter painter = new ScorePainter();
        painter.setNoteBackgroundProvider(null);
        painter.paint(surface, layout, layout.width(), layout.height());
        assertNotNull(surface);
        assertTrue(surface.roundedRects.isEmpty(),
                "a null provider must not draw any backgrounds");
    }

    @Test
    void backgroundIsIndependentOfHighlight() {
        Note[] notes = new Note[2];
        Score score = twoNoteScore(notes);
        LayoutResult layout = new Engraver().layout(score, LayoutOptions.defaults());

        // Only the first note has a background; the second has a tint.
        Map<MusicElement, RenderColor> backgrounds = new IdentityHashMap<>();
        backgrounds.put(notes[0], YELLOW);
        Map<MusicElement, RenderColor> tints = new IdentityHashMap<>();
        tints.put(notes[1], RED);

        BackgroundSpyingSurface surface = new BackgroundSpyingSurface();
        ScorePainter painter = new ScorePainter();
        painter.setNoteColorProvider(e -> Optional.ofNullable(tints.get(e)));
        painter.setNoteBackgroundProvider(e -> Optional.ofNullable(backgrounds.get(e)));
        painter.paint(surface, layout, layout.width(), layout.height());

        // Exactly one background rect (for note 0), and RED must have been
        // pushed as fill/stroke for note 1's tint pass.
        assertEquals(1, surface.roundedRects.size());
        assertEquals(YELLOW, surface.roundedRects.get(0).color);
        assertTrue(surface.fillsSet.contains(RED)
                        || surface.strokesSet.contains(RED),
                "the tint colour must reach the surface even when only the "
                        + "other note carries a background");
    }

    @Test
    void mutatingBackgroundsDoesNotReEngrave() {
        // Repainting with different backgrounds should not touch the
        // Engraver: we call it exactly once here and drive multiple
        // paints from the single layout.
        Note[] notes = new Note[2];
        Score score = twoNoteScore(notes);
        Engraver engraver = new Engraver();
        LayoutResult layout = engraver.layout(score, LayoutOptions.defaults());

        Map<MusicElement, RenderColor> backgrounds = new IdentityHashMap<>();
        ScorePainter painter = new ScorePainter();
        painter.setNoteBackgroundProvider(e -> Optional.ofNullable(backgrounds.get(e)));

        BackgroundSpyingSurface s1 = new BackgroundSpyingSurface();
        painter.paint(s1, layout, layout.width(), layout.height());
        assertEquals(0, s1.roundedRects.size(), "no highlights => no rects");

        backgrounds.put(notes[0], YELLOW);
        BackgroundSpyingSurface s2 = new BackgroundSpyingSurface();
        painter.paint(s2, layout, layout.width(), layout.height());
        assertEquals(1, s2.roundedRects.size(), "one highlight => one rect");

        backgrounds.clear();
        BackgroundSpyingSurface s3 = new BackgroundSpyingSurface();
        painter.paint(s3, layout, layout.width(), layout.height());
        assertEquals(0, s3.roundedRects.size(), "cleared => no rects");
        assertFalse(s3.roundedRects.stream().anyMatch(r -> r.color.equals(YELLOW)));
    }

    @Test
    void alphaChannelPassesThroughToSurface() {
        Note[] notes = new Note[2];
        Score score = twoNoteScore(notes);
        LayoutResult layout = new Engraver().layout(score, LayoutOptions.defaults());

        RenderColor translucent = new RenderColor(0, 128, 255, 64);
        Map<MusicElement, RenderColor> backgrounds = new IdentityHashMap<>();
        backgrounds.put(notes[0], translucent);

        BackgroundSpyingSurface surface = new BackgroundSpyingSurface();
        ScorePainter painter = new ScorePainter();
        painter.setNoteBackgroundProvider(e -> Optional.ofNullable(backgrounds.get(e)));
        painter.paint(surface, layout, layout.width(), layout.height());

        assertEquals(1, surface.roundedRects.size());
        assertEquals(64, surface.roundedRects.get(0).color.alpha(),
                "alpha channel must survive from provider to surface");
    }

    @Test
    void backgroundStyleControlsRectangleSize() {
        Note[] notes = new Note[2];
        Score score = twoNoteScore(notes);
        LayoutResult layout = new Engraver().layout(score, LayoutOptions.defaults());

        Map<MusicElement, RenderColor> backgrounds = new IdentityHashMap<>();
        backgrounds.put(notes[0], YELLOW);

        ScorePainter painter = new ScorePainter();
        painter.setNoteBackgroundProvider(e -> Optional.ofNullable(backgrounds.get(e)));

        painter.setNoteBackgroundStyle(NoteBackgroundStyle.defaults());
        BackgroundSpyingSurface wide = new BackgroundSpyingSurface();
        painter.paint(wide, layout, layout.width(), layout.height());

        painter.setNoteBackgroundStyle(NoteBackgroundStyle.tight());
        BackgroundSpyingSurface tight = new BackgroundSpyingSurface();
        painter.paint(tight, layout, layout.width(), layout.height());

        assertEquals(1, wide.roundedRects.size());
        assertEquals(1, tight.roundedRects.size());
        RoundedRect w = wide.roundedRects.get(0);
        RoundedRect t = tight.roundedRects.get(0);
        assertTrue(t.w < w.w, "the tight style must produce a narrower background rect");
        assertTrue(t.h < w.h, "the tight style must produce a shorter background rect");
        assertTrue(t.aw < w.aw, "the tight style must use a smaller corner arc");
    }

    @Test
    void backgroundStyleOffsetYShiftsRectDown() {
        Note[] notes = new Note[2];
        Score score = twoNoteScore(notes);
        LayoutResult layout = new Engraver().layout(score, LayoutOptions.defaults());

        Map<MusicElement, RenderColor> backgrounds = new IdentityHashMap<>();
        backgrounds.put(notes[0], YELLOW);

        ScorePainter painter = new ScorePainter();
        painter.setNoteBackgroundProvider(e -> Optional.ofNullable(backgrounds.get(e)));

        painter.setNoteBackgroundStyle(NoteBackgroundStyle.defaults());
        BackgroundSpyingSurface centred = new BackgroundSpyingSurface();
        painter.paint(centred, layout, layout.width(), layout.height());

        painter.setNoteBackgroundStyle(NoteBackgroundStyle.defaults().withOffsetY(1.0));
        BackgroundSpyingSurface shifted = new BackgroundSpyingSurface();
        painter.paint(shifted, layout, layout.width(), layout.height());

        RoundedRect c = centred.roundedRects.get(0);
        RoundedRect s = shifted.roundedRects.get(0);
        assertTrue(s.y > c.y, "a positive offsetY must move the background rect down");
        assertEquals(c.h, s.h, 1e-9, "offsetY must not change the rect height");
    }

    @Test
    void nullBackgroundStyleRestoresDefaults() {
        ScorePainter painter = new ScorePainter();
        painter.setNoteBackgroundStyle(NoteBackgroundStyle.tight());
        painter.setNoteBackgroundStyle(null);
        assertEquals(NoteBackgroundStyle.defaults(), painter.getNoteBackgroundStyle());
    }

    private static final class BackgroundSpyingSurface implements RenderSurface {
        final List<RoundedRect> roundedRects = new ArrayList<>();
        final List<RenderColor> fillsSet = new ArrayList<>();
        final List<RenderColor> strokesSet = new ArrayList<>();

        @Override public void setStroke(RenderColor color) { strokesSet.add(color); }
        @Override public void setFill(RenderColor color) { fillsSet.add(color); }
        @Override public void setLineWidth(double width) { }
        @Override public void fillRect(double x, double y, double w, double h) { }
        @Override public void strokeLine(double x1, double y1, double x2, double y2) { }
        @Override public void fillOval(double x, double y, double w, double h) { }
        @Override public void strokeOval(double x, double y, double w, double h) { }
        @Override public void strokeText(String text, double x, double y) { }
        @Override public void fillRoundedRect(double x, double y, double w, double h,
                                              double aw, double ah, RenderColor c) {
            roundedRects.add(new RoundedRect(x, y, w, h, aw, ah, c));
        }
    }

    private record RoundedRect(double x, double y, double w, double h,
                               double aw, double ah, RenderColor color) {
    }
}
