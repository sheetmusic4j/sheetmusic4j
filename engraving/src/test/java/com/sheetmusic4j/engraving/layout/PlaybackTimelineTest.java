package com.sheetmusic4j.engraving.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import com.sheetmusic4j.core.model.Rest;
import com.sheetmusic4j.core.model.Score;
import com.sheetmusic4j.core.model.Step;
import com.sheetmusic4j.core.model.TimeSignature;
import com.sheetmusic4j.engraving.Engraver;

class PlaybackTimelineTest {

    private final Note first = note(Step.C, 1);
    private final Note second = note(Step.E, 2);

    /** One 4/4 measure: a quarter rest, a quarter C and a half E. */
    private LayoutResult layout() {
        Measure measure = Measure.builder(1)
                .attributes(Attributes.builder()
                        .divisions(1)
                        .keySignature(KeySignature.cMajor())
                        .timeSignature(TimeSignature.fourFour())
                        .clef(Clef.treble())
                        .build())
                .addElement(Rest.builder().duration(new Duration(1, 1)).build())
                .addElement(first)
                .addElement(second)
                .build();
        Score score = Score.builder().addPart(Part.builder("P1").addMeasure(measure).build()).build();
        return new Engraver().layout(score, LayoutOptions.defaults());
    }

    private static Note note(Step step, int quarters) {
        return Note.builder().pitch(new Pitch(step, 4)).duration(new Duration(quarters, 1)).build();
    }

    @Test
    void soundingAnchorsSkipRests() {
        List<NoteAnchor> sounding = PlaybackTimeline.of(layout()).soundingAnchors();
        assertEquals(2, sounding.size());
        assertSame(first, sounding.get(0).elementRef());
        assertSame(second, sounding.get(1).elementRef());
    }

    @Test
    void soundingAtFollowsEachNoteWindow() {
        PlaybackTimeline timeline = PlaybackTimeline.of(layout());
        assertTrue(timeline.soundingAt(0.5).isEmpty(), "only the rest plays at 0.5");
        assertSame(first, timeline.soundingAt(1.0).get(0).elementRef());
        assertSame(second, timeline.soundingAt(2.0).get(0).elementRef());
        assertSame(second, timeline.soundingAt(3.9).get(0).elementRef());
        assertTrue(timeline.soundingAt(4.0).isEmpty(), "windows are half-open");
    }

    @Test
    void cursorPassesThroughNoteheadsAndGlidesBetweenThem() {
        LayoutResult layout = layout();
        PlaybackTimeline timeline = PlaybackTimeline.of(layout);
        NoteAnchor c = layout.noteAnchors().get(1);
        NoteAnchor e = layout.noteAnchors().get(2);
        assertEquals(c.x(), timeline.cursorX(1.0), 1e-9);
        assertEquals(e.x(), timeline.cursorX(2.0), 1e-9);
        assertEquals((c.x() + e.x()) / 2, timeline.cursorX(1.5), 1e-9);
        assertTrue(timeline.cursorX(3.0) > e.x(), "keeps moving while the last note is held");
        assertEquals(layout.xAtTime(4.0), timeline.cursorX(10.0), 1e-9, "clamped to the end");
    }

    @Test
    void emptyLayoutFallsBackToMeasureTime() {
        LayoutResult empty = new LayoutResult(List.of(), 0, 0);
        PlaybackTimeline timeline = PlaybackTimeline.of(empty);
        assertEquals(0.0, timeline.cursorX(1.0));
        assertTrue(timeline.soundingAnchors().isEmpty());
    }

    @Test
    void sliceKeepsOnlySystemsInTheWindow() {
        LayoutResult layout = layout();
        double bottom = layout.height();
        assertEquals(layout.systems().size(), layout.slice(0, bottom).systems().size());
        LayoutResult far = layout.slice(bottom + 10_000, bottom + 20_000);
        assertTrue(far.systems().isEmpty());
        assertTrue(far.noteAnchors().isEmpty());
        assertEquals(layout.width(), far.width());
        assertEquals(10_000, far.height(), 1e-9);
    }
    /** {@code measures} 4/4 measures of quarter notes, engraved on a narrow page so they wrap. */
    private static LayoutResult page(int measures) {
        Part.Builder part = Part.builder("P1");
        for (int m = 1; m <= measures; m++) {
            Measure.Builder measure = Measure.builder(m);
            if (m == 1) {
                measure.attributes(Attributes.builder().divisions(1).keySignature(KeySignature.cMajor())
                        .timeSignature(TimeSignature.fourFour()).clef(Clef.treble()).build());
            }
            for (int i = 0; i < 4; i++) {
                measure.addElement(note(Step.values()[i], 1));
            }
            part.addMeasure(measure.build());
        }
        Score score = Score.builder().addPart(part.build()).build();
        return new Engraver().layout(score, LayoutOptions.defaults().toBuilder().systemWidth(500).build());
    }

    @Test
    void cursorAtFollowsTheSystemThatPlays() {
        LayoutResult layout = page(12);
        assertTrue(layout.systems().size() > 1, "the page wraps");
        PlaybackTimeline timeline = PlaybackTimeline.of(layout);
        CursorPlacement start = timeline.cursorAt(0);
        assertEquals(0, start.systemIndex());
        assertEquals(layout.systems().get(0).staves().get(0).y(), start.top(), 1e-9);
        assertTrue(start.bottom() > start.top());

        CursorPlacement end = timeline.cursorAt(48);
        assertEquals(layout.systems().size() - 1, end.systemIndex());

        // Within one system the cursor only moves right; it never sweeps back before a line break.
        double previousX = -1;
        int previousSystem = 0;
        for (double t = 0; t <= 48; t += 0.25) {
            CursorPlacement at = timeline.cursorAt(t);
            assertTrue(at.systemIndex() >= previousSystem, "systems advance");
            if (at.systemIndex() == previousSystem) {
                assertTrue(at.x() >= previousX - 1e-9, "x grows within a system at " + t);
            }
            previousX = at.x();
            previousSystem = at.systemIndex();
        }
    }

    @Test
    void cursorAtMatchesCursorXOnOneSystem() {
        LayoutResult layout = layout();
        PlaybackTimeline timeline = PlaybackTimeline.of(layout);
        for (double t = 0; t <= 4; t += 0.5) {
            assertEquals(timeline.cursorX(t), timeline.cursorAt(t).x(), 1e-9);
        }
    }

    @Test
    void revealStepCountsStartedOnsets() {
        PlaybackTimeline timeline = PlaybackTimeline.of(layout());
        // Onsets: rest at 0, C at 1, E at 2.
        assertEquals(0, timeline.revealStep(-0.5));
        assertEquals(1, timeline.revealStep(0));
        assertEquals(1, timeline.revealStep(0.99));
        assertEquals(2, timeline.revealStep(1));
        assertEquals(3, timeline.revealStep(2));
        assertEquals(3, timeline.revealStep(100));
    }

    @Test
    void anchorsBetweenIsHalfOpen() {
        PlaybackTimeline timeline = PlaybackTimeline.of(layout());
        List<NoteAnchor> between = timeline.anchorsBetween(0, 1);
        assertEquals(1, between.size());
        assertSame(first, between.get(0).elementRef());
        assertEquals(2, timeline.anchorsBetween(2, 0).size(), "order of the bounds does not matter");
    }
}
