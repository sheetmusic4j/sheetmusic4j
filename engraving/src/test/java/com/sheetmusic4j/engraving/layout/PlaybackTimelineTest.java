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
}
