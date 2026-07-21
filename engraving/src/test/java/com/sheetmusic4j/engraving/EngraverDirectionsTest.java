package com.sheetmusic4j.engraving;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.sheetmusic4j.core.model.Attributes;
import com.sheetmusic4j.core.model.Clef;
import com.sheetmusic4j.core.model.Direction;
import com.sheetmusic4j.core.model.DirectionType;
import com.sheetmusic4j.core.model.Duration;
import com.sheetmusic4j.core.model.DynamicMark;
import com.sheetmusic4j.core.model.KeySignature;
import com.sheetmusic4j.core.model.Measure;
import com.sheetmusic4j.core.model.MusicElement;
import com.sheetmusic4j.core.model.Note;
import com.sheetmusic4j.core.model.NoteType;
import com.sheetmusic4j.core.model.Part;
import com.sheetmusic4j.core.model.Pitch;
import com.sheetmusic4j.core.model.Placement;
import com.sheetmusic4j.core.model.Score;
import com.sheetmusic4j.core.model.Step;
import com.sheetmusic4j.core.model.TimeSignature;

class EngraverDirectionsTest {

    private static final int DIVISIONS = 1;

    private static Measure.Builder measureWithAttributes(int number) {
        return Measure.builder(number).attributes(Attributes.builder()
                .divisions(DIVISIONS)
                .keySignature(KeySignature.cMajor())
                .timeSignature(TimeSignature.fourFour())
                .clef(Clef.treble())
                .build());
    }

    private static Note quarter(Step step) {
        return Note.builder()
                .pitch(new Pitch(step, 4))
                .duration(new Duration(1, DIVISIONS))
                .type(NoteType.QUARTER)
                .build();
    }

    private static Score singleDirectionScore(Direction direction, MusicElement... rest) {
        Measure.Builder mb = measureWithAttributes(1).addElement(direction);
        for (MusicElement el : rest) {
            mb.addElement(el);
        }
        return Score.builder()
                .addPart(Part.builder("P1").addMeasure(mb.build()).build())
                .build();
    }

    private static List<TextPlacement> textsOf(LayoutResult layout, MarkingCategory category) {
        return layout.texts().stream()
                .filter(t -> t.category() == category)
                .toList();
    }

    private static List<GlyphPlacement> glyphsOf(LayoutResult layout, MarkingCategory category) {
        return layout.staves().stream()
                .flatMap(s -> s.glyphs().stream())
                .filter(g -> g.category() == category)
                .toList();
    }

    @Test
    void wordsDirectionEmitsTextAboveStaff() {
        Direction dir = new Direction(
                new DirectionType.Words("Andantino", false, true), Placement.ABOVE);
        Score score = singleDirectionScore(dir, quarter(Step.C));

        LayoutOptions options = LayoutOptions.defaults();
        LayoutResult layout = new Engraver().layout(score, options);

        List<TextPlacement> directions = textsOf(layout, MarkingCategory.DIRECTION);
        assertEquals(1, directions.size());
        TextPlacement placement = directions.get(0);
        assertEquals("Andantino", placement.text());

        double staffY = layout.staves().get(0).y();
        assertTrue(placement.y() < staffY,
                "words direction y (" + placement.y() + ") must sit above staff y (" + staffY + ")");
    }

    @Test
    void metronomeDirectionEmitsTempoText() {
        Direction dir = new Direction(
                new DirectionType.Metronome(NoteType.QUARTER, false, 60), Placement.ABOVE);
        Score score = singleDirectionScore(dir, quarter(Step.C));

        LayoutResult layout = new Engraver().layout(score, LayoutOptions.defaults());
        List<TextPlacement> tempos = textsOf(layout, MarkingCategory.TEMPO);
        assertEquals(1, tempos.size());
        TextPlacement placement = tempos.get(0);
        assertTrue(placement.text().contains("60"),
                "metronome text (" + placement.text() + ") should contain 60");
        double staffY = layout.staves().get(0).y();
        assertTrue(placement.y() < staffY);
    }

    @Test
    void dynamicDirectionEmitsGlyphBelowStaff() {
        Direction dir = new Direction(
                new DirectionType.Dynamic(DynamicMark.F), Placement.BELOW);
        Score score = singleDirectionScore(dir, quarter(Step.C));

        LayoutOptions options = LayoutOptions.defaults();
        LayoutResult layout = new Engraver().layout(score, options);

        List<GlyphPlacement> dynamics = glyphsOf(layout, MarkingCategory.DYNAMIC);
        assertEquals(1, dynamics.size());
        GlyphPlacement dyn = dynamics.get(0);
        assertEquals(Glyph.DYNAMIC_F, dyn.glyph());

        StaffLayout staff = layout.staves().get(0);
        double staffBottom = staff.y() + options.staffHeight();
        assertTrue(dyn.y() > staffBottom,
                "dynamic y (" + dyn.y() + ") must sit below staff bottom (" + staffBottom + ")");
    }

    @Test
    void defaultPlacementForWordsIsAbove() {
        Direction dir = new Direction(
                new DirectionType.Words("dolce", true, false), Placement.DEFAULT);
        Score score = singleDirectionScore(dir, quarter(Step.C));
        LayoutResult layout = new Engraver().layout(score, LayoutOptions.defaults());
        TextPlacement placement = textsOf(layout, MarkingCategory.DIRECTION).get(0);
        double staffY = layout.staves().get(0).y();
        assertTrue(placement.y() < staffY);
    }

    @Test
    void defaultPlacementForDynamicsIsBelow() {
        Direction dir = new Direction(
                new DirectionType.Dynamic(DynamicMark.PP), Placement.DEFAULT);
        Score score = singleDirectionScore(dir, quarter(Step.C));
        LayoutOptions options = LayoutOptions.defaults();
        LayoutResult layout = new Engraver().layout(score, options);
        GlyphPlacement dyn = glyphsOf(layout, MarkingCategory.DYNAMIC).get(0);
        StaffLayout staff = layout.staves().get(0);
        assertTrue(dyn.y() > staff.y() + options.staffHeight());
    }

    @Test
    void directionAttachesToNextNoteX() {
        Direction dir = new Direction(
                new DirectionType.Dynamic(DynamicMark.MF), Placement.BELOW);
        Score score = singleDirectionScore(dir, quarter(Step.C), quarter(Step.D));
        LayoutResult layout = new Engraver().layout(score, LayoutOptions.defaults());
        GlyphPlacement dyn = glyphsOf(layout, MarkingCategory.DYNAMIC).get(0);
        double firstNoteX = layout.staves().get(0).glyphs().stream()
                .filter(g -> g.glyph() == Glyph.NOTEHEAD_BLACK)
                .mapToDouble(GlyphPlacement::x)
                .findFirst().orElseThrow();
        assertEquals(firstNoteX, dyn.x(), 1e-6);
    }

    @Test
    void directionAtEndOfMeasureAttachesToLastNoteX() {
        Direction dir = new Direction(
                new DirectionType.Dynamic(DynamicMark.MF), Placement.BELOW);
        Measure measure = measureWithAttributes(1)
                .addElement(quarter(Step.C))
                .addElement(quarter(Step.D))
                .addElement(dir)
                .build();
        Score score = Score.builder()
                .addPart(Part.builder("P1").addMeasure(measure).build())
                .build();
        LayoutResult layout = new Engraver().layout(score, LayoutOptions.defaults());
        GlyphPlacement dyn = glyphsOf(layout, MarkingCategory.DYNAMIC).get(0);
        double[] noteXs = layout.staves().get(0).glyphs().stream()
                .filter(g -> g.glyph() == Glyph.NOTEHEAD_BLACK)
                .mapToDouble(GlyphPlacement::x)
                .toArray();
        assertEquals(noteXs[noteXs.length - 1], dyn.x(), 1e-6);
    }

    @Test
    void directionsAbovePushStavesDown() {
        LayoutOptions options = LayoutOptions.defaults();
        Score without = Score.builder()
                .addPart(Part.builder("P1")
                        .addMeasure(measureWithAttributes(1).addElement(quarter(Step.C)).build())
                        .build())
                .build();
        Direction dir = new Direction(
                new DirectionType.Words("Andantino", false, true), Placement.ABOVE);
        Score with = Score.builder()
                .addPart(Part.builder("P1")
                        .addMeasure(measureWithAttributes(1)
                                .addElement(dir)
                                .addElement(quarter(Step.C))
                                .build())
                        .build())
                .build();

        double yWithout = new Engraver().layout(without, options).staves().get(0).y();
        double yWith = new Engraver().layout(with, options).staves().get(0).y();
        assertTrue(yWith > yWithout,
                "above-direction must push first staff down (with=" + yWith
                        + ", without=" + yWithout + ")");
    }

    @Test
    void dynamicDirectionPushesNextPartDown() {
        LayoutOptions options = LayoutOptions.defaults();
        Measure m1WithDyn = measureWithAttributes(1)
                .addElement(new Direction(
                        new DirectionType.Dynamic(DynamicMark.F), Placement.BELOW))
                .addElement(quarter(Step.C))
                .build();
        Measure m1Plain = measureWithAttributes(1).addElement(quarter(Step.C)).build();
        Measure m2 = measureWithAttributes(1).addElement(quarter(Step.C)).build();

        Score withDyn = Score.builder()
                .addPart(Part.builder("P1").addMeasure(m1WithDyn).build())
                .addPart(Part.builder("P2").addMeasure(m2).build())
                .build();
        Score withoutDyn = Score.builder()
                .addPart(Part.builder("P1").addMeasure(m1Plain).build())
                .addPart(Part.builder("P2").addMeasure(m2).build())
                .build();

        double yWith = new Engraver().layout(withDyn, options).staves().get(1).y();
        double yWithout = new Engraver().layout(withoutDyn, options).staves().get(1).y();
        assertTrue(yWith > yWithout,
                "below-dynamic must push next part down (with=" + yWith
                        + ", without=" + yWithout + ")");
    }

    @Test
    void unrelatedGlyphsRemainCategoryNote() {
        Direction dir = new Direction(
                new DirectionType.Dynamic(DynamicMark.MF), Placement.BELOW);
        Score score = singleDirectionScore(dir, quarter(Step.C));
        LayoutResult layout = new Engraver().layout(score, LayoutOptions.defaults());
        GlyphPlacement notehead = layout.staves().get(0).glyphs().stream()
                .filter(g -> g.glyph() == Glyph.NOTEHEAD_BLACK)
                .findFirst().orElseThrow();
        assertEquals(MarkingCategory.NOTE, notehead.category(),
                "existing glyphs must remain in the NOTE category");
    }

    @Test
    void rehearsalMarkEmitsBoxedTextAboveStaff() {
        Direction dir = new Direction(
                new DirectionType.Rehearsal("A"), Placement.ABOVE);
        Score score = singleDirectionScore(dir, quarter(Step.C));

        LayoutOptions options = LayoutOptions.defaults();
        LayoutResult layout = new Engraver().layout(score, options);

        List<TextPlacement> rehearsals = textsOf(layout, MarkingCategory.REHEARSAL);
        assertEquals(1, rehearsals.size());
        TextPlacement placement = rehearsals.get(0);
        assertEquals("A", placement.text());
        assertTrue(placement.boxed(),
                "rehearsal mark placement must carry boxed=true");

        double staffY = layout.staves().get(0).y();
        assertTrue(placement.y() < staffY,
                "rehearsal mark y (" + placement.y() + ") must sit above staff y (" + staffY + ")");
    }

    @Test
    void rehearsalMarkDefaultPlacementIsAbove() {
        Direction dir = new Direction(
                new DirectionType.Rehearsal("12"), Placement.DEFAULT);
        Score score = singleDirectionScore(dir, quarter(Step.C));

        LayoutResult layout = new Engraver().layout(score, LayoutOptions.defaults());
        TextPlacement placement = textsOf(layout, MarkingCategory.REHEARSAL).get(0);
        double staffY = layout.staves().get(0).y();
        assertTrue(placement.y() < staffY);
    }

    @Test
    void metronomeDirectionSmuflMappingCoversNewGlyphs() {
        // Sanity-check: every declared DynamicMark maps to a Glyph, so the
        // engraver's switch is exhaustive.
        for (DynamicMark mark : DynamicMark.values()) {
            Direction dir = new Direction(new DirectionType.Dynamic(mark), Placement.BELOW);
            Score score = singleDirectionScore(dir, quarter(Step.C));
            LayoutResult layout = new Engraver().layout(score, LayoutOptions.defaults());
            List<GlyphPlacement> dynamics = glyphsOf(layout, MarkingCategory.DYNAMIC);
            assertEquals(1, dynamics.size(), "mark=" + mark);
            assertNotNull(dynamics.get(0).glyph(), "mark=" + mark);
        }
    }
}
