package com.sheetmusic4j.engraving;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.sheetmusic4j.core.model.Attributes;
import com.sheetmusic4j.core.model.Clef;
import com.sheetmusic4j.core.model.Duration;
import com.sheetmusic4j.core.model.KeySignature;
import com.sheetmusic4j.core.model.Lyric;
import com.sheetmusic4j.core.model.Measure;
import com.sheetmusic4j.core.model.Note;
import com.sheetmusic4j.core.model.Part;
import com.sheetmusic4j.core.model.Pitch;
import com.sheetmusic4j.core.model.Rest;
import com.sheetmusic4j.core.model.Score;
import com.sheetmusic4j.core.model.Step;
import com.sheetmusic4j.core.model.Syllabic;
import com.sheetmusic4j.core.model.TimeSignature;
import com.sheetmusic4j.engraving.glyph.MarkingCategory;
import com.sheetmusic4j.engraving.layout.LayoutOptions;
import com.sheetmusic4j.engraving.layout.LayoutResult;
import com.sheetmusic4j.engraving.layout.NoteAnchor;
import com.sheetmusic4j.engraving.placement.StemPlacement;

/**
 * Onset-column spacing, voices and hidden categories.
 */
class VoicesAndColumnsTest {

    private static Note note(Step step, int octave, int quarters, int staff, int voice) {
        return Note.builder().pitch(new Pitch(step, octave)).duration(new Duration(quarters, 1))
                .staff(staff).voice(voice).build();
    }

    private static Score score(Part... parts) {
        Score.Builder score = Score.builder();
        for (Part part : parts) {
            score.addPart(part);
        }
        return score.build();
    }

    private static Attributes attributes(int staves) {
        Attributes.Builder a = Attributes.builder().divisions(1).keySignature(KeySignature.cMajor())
                .timeSignature(TimeSignature.fourFour()).staves(staves);
        if (staves > 1) {
            a.addClef(Clef.treble()).addClef(Clef.bass());
        } else {
            a.clef(Clef.treble());
        }
        return a.build();
    }

    private static Map<Double, Double> xByOnsetOnStaff(LayoutResult layout, int systemStaffIndex) {
        Map<Double, Double> xs = new HashMap<>();
        for (NoteAnchor anchor : layout.noteAnchors()) {
            if (anchor.staffIndex() == systemStaffIndex) {
                xs.put(anchor.onsetQuarters(), anchor.x());
            }
        }
        return xs;
    }

    @Test
    void simultaneousNotesOnDifferentStavesShareAnX() {
        // Treble: a whole note's worth as half + quarter + quarter; bass:
        // four quarters. Beats 1 and 3 coincide.
        Measure measure = Measure.builder(1).attributes(attributes(2))
                .addElement(note(Step.E, 5, 2, 1, 1))
                .addElement(note(Step.D, 5, 1, 1, 1))
                .addElement(note(Step.C, 5, 1, 1, 1))
                .addElement(note(Step.C, 3, 1, 2, 5))
                .addElement(note(Step.D, 3, 1, 2, 5))
                .addElement(note(Step.E, 3, 1, 2, 5))
                .addElement(note(Step.F, 3, 1, 2, 5))
                .build();
        LayoutResult layout = new Engraver().layout(
                score(Part.builder("P1").name("Piano").addMeasure(measure).build()), LayoutOptions.defaults());

        Map<Double, Double> treble = xByOnsetOnStaff(layout, 0);
        Map<Double, Double> bass = xByOnsetOnStaff(layout, 1);
        assertEquals(treble.get(0.0), bass.get(0.0), 1e-9);
        assertEquals(treble.get(2.0), bass.get(2.0), 1e-9);
        assertEquals(treble.get(3.0), bass.get(3.0), 1e-9);
        assertTrue(bass.get(1.0) > bass.get(0.0) && bass.get(1.0) < bass.get(2.0));
    }

    @Test
    void secondVoiceDoesNotLengthenTheMeasureAndStemsDown() {
        Note upper = note(Step.E, 5, 4, 1, 1);
        Note lower = note(Step.C, 5, 4, 1, 2);
        Note upperQuarter = note(Step.G, 4, 1, 1, 1);
        Note lowerQuarter = note(Step.G, 4, 1, 1, 2);
        Measure m1 = Measure.builder(1).attributes(attributes(1))
                .addElement(upper).addElement(lower).build();
        Measure m2 = Measure.builder(2)
                .addElement(upperQuarter).addElement(note(Step.A, 4, 3, 1, 1))
                .addElement(lowerQuarter).addElement(note(Step.F, 4, 3, 1, 2))
                .build();
        LayoutResult layout = new Engraver().layout(
                score(Part.builder("P1").addMeasure(m1).addMeasure(m2).build()), LayoutOptions.defaults());

        assertEquals(8.0, layout.totalDurationQuarters(), 1e-9, "voices must sound together, not in sequence");
        Map<Note, StemPlacement> stems = new HashMap<>();
        for (StemPlacement stem : layout.staves().get(0).stems()) {
            stems.put((Note) stem.elementRef(), stem);
        }
        // Same pitch, different voices: voice 1 stems up, voice 2 down.
        assertTrue(stems.get(upperQuarter).y2() < stems.get(upperQuarter).y1(), "voice 1 stem must point up");
        assertTrue(stems.get(lowerQuarter).y2() > stems.get(lowerQuarter).y1(), "voice 2 stem must point down");
    }

    @Test
    void invisibleRestsAreNotDrawnButDelayTheirVoice() {
        Rest spacer = Rest.builder().duration(new Duration(2, 1)).voice(2).visible(false).build();
        Note late = note(Step.C, 5, 2, 1, 2);
        Measure measure = Measure.builder(1).attributes(attributes(1))
                .addElement(note(Step.E, 5, 4, 1, 1))
                .addElement(spacer).addElement(late)
                .build();
        LayoutResult layout = new Engraver().layout(
                score(Part.builder("P1").addMeasure(measure).build()), LayoutOptions.defaults());

        assertFalse(layout.noteAnchors().stream().anyMatch(a -> a.elementRef() == spacer));
        assertFalse(layout.staves().get(0).glyphs().stream().anyMatch(g -> g.elementRef() == spacer));
        NoteAnchor anchor = layout.noteAnchors().stream().filter(a -> a.elementRef() == late).findFirst().orElseThrow();
        assertEquals(2.0, anchor.onsetQuarters(), 1e-9);
    }

    @Test
    void hiddenCategoriesFreeTheirSpace() {
        Note sung = Note.builder().pitch(new Pitch(Step.C, 5)).duration(new Duration(4, 1))
                .addLyric(new Lyric("la", Syllabic.SINGLE, 1)).build();
        Score score = Score.builder().workTitle("Title")
                .addPart(Part.builder("P1").name("Voice")
                        .addMeasure(Measure.builder(1).attributes(attributes(1)).addElement(sung).build()).build())
                .addPart(Part.builder("P2").name("Piano")
                        .addMeasure(Measure.builder(1).attributes(attributes(1))
                                .addElement(note(Step.C, 4, 4, 1, 1)).build()).build())
                .build();
        LayoutResult shown = new Engraver().layout(score, LayoutOptions.defaults());
        LayoutResult hidden = new Engraver().layout(score, LayoutOptions.builder()
                .hiddenCategories(EnumSet.of(MarkingCategory.LYRIC, MarkingCategory.TITLE, MarkingCategory.PART_LABEL))
                .build());

        assertTrue(hidden.texts().stream().noneMatch(t -> t.category() == MarkingCategory.LYRIC
                || t.category() == MarkingCategory.TITLE || t.category() == MarkingCategory.PART_LABEL));
        assertTrue(hidden.staves().get(0).y() < shown.staves().get(0).y(), "hidden title must free its space");
        double gapShown = shown.staves().get(1).y() - shown.staves().get(0).y();
        double gapHidden = hidden.staves().get(1).y() - hidden.staves().get(0).y();
        assertTrue(gapHidden < gapShown, "hidden lyrics must free their space below the staff");
        assertTrue(hidden.staves().get(0).x() < shown.staves().get(0).x(), "hidden labels must free their margin");
    }

    @Test
    void longLyricsPushNotesApart() {
        Score shortWords = lyricScore("a", "b");
        Score longWords = lyricScore("wonderfully", "extraordinary");
        // A narrow system compresses the measure to its minimum, where only the
        // lyric constraint keeps the syllables apart.
        LayoutOptions options = LayoutOptions.builder().systemWidth(250).build();
        List<NoteAnchor> s = new Engraver().layout(shortWords, options).noteAnchors();
        List<NoteAnchor> l = new Engraver().layout(longWords, options).noteAnchors();
        double shortGap = s.get(1).x() - s.get(0).x();
        double longGap = l.get(1).x() - l.get(0).x();
        assertTrue(longGap > shortGap, "long syllables need more room: " + shortGap + " vs " + longGap);
        double fontSize = options.staffLineGap() * 1.4;
        double needed = (options.textMetrics().width("wonderfully", fontSize)
                + options.textMetrics().width("extraordinary", fontSize)) / 2;
        assertTrue(longGap >= needed, "syllables must not overlap");
    }

    private static Score lyricScore(String first, String second) {
        Measure measure = Measure.builder(1).attributes(attributes(1))
                .addElement(Note.builder().pitch(new Pitch(Step.C, 5)).duration(new Duration(1, 4))
                        .addLyric(new Lyric(first, Syllabic.SINGLE, 1)).build())
                .addElement(Note.builder().pitch(new Pitch(Step.D, 5)).duration(new Duration(1, 4))
                        .addLyric(new Lyric(second, Syllabic.SINGLE, 1)).build())
                .addElement(Rest.builder().duration(new Duration(7, 2)).build())
                .build();
        return Score.builder().addPart(Part.builder("P1").addMeasure(measure).build()).build();
    }
}
