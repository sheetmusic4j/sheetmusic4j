package com.sheetmusic4j.engraving;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.sheetmusic4j.core.model.Attributes;
import com.sheetmusic4j.core.model.Beam;
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

class EngraverTest {

    private Score twoMeasureScale() {
        int divisions = 1;
        Step[] first = {Step.C, Step.D, Step.E, Step.F};
        Step[] second = {Step.G, Step.A, Step.B, Step.C};

        Measure.Builder m1 = Measure.builder(1).attributes(Attributes.builder()
                .divisions(divisions)
                .keySignature(KeySignature.cMajor())
                .timeSignature(TimeSignature.fourFour())
                .clef(Clef.treble())
                .build());
        for (Step step : first) {
            m1.addElement(Note.builder().pitch(new Pitch(step, 4)).duration(new Duration(1, divisions)).build());
        }
        Measure.Builder m2 = Measure.builder(2);
        for (int i = 0; i < second.length; i++) {
            int octave = i == second.length - 1 ? 5 : 4;
            m2.addElement(Note.builder().pitch(new Pitch(second[i], octave)).duration(new Duration(1, divisions)).build());
        }

        Part part = Part.builder("P1").name("Piano")
                .addMeasure(m1.build())
                .addMeasure(m2.build())
                .build();
        return Score.builder().addPart(part).build();
    }

    @Test
    void producesOneStaffPerPart() {
        LayoutResult layout = new Engraver().layout(twoMeasureScale(), LayoutOptions.defaults());
        assertEquals(1, layout.systems().size());
        assertEquals(1, layout.staves().size());
    }

    @Test
    void producesExpectedMeasureCount() {
        StaffLayout staff = new Engraver().layout(twoMeasureScale(), LayoutOptions.defaults()).staves().get(0);
        assertEquals(2, staff.measures().size());
    }

    @Test
    void measuresHaveMonotonicallyIncreasingX() {
        StaffLayout staff = new Engraver().layout(twoMeasureScale(), LayoutOptions.defaults()).staves().get(0);
        List<MeasureLayout> measures = staff.measures();
        for (int i = 1; i < measures.size(); i++) {
            assertTrue(measures.get(i).x() > measures.get(i - 1).x(),
                    "measure x positions must increase");
        }
    }

    @Test
    void noteGlyphsHaveMonotonicallyIncreasingX() {
        StaffLayout staff = new Engraver().layout(twoMeasureScale(), LayoutOptions.defaults()).staves().get(0);
        List<Double> noteXs = new ArrayList<>();
        for (GlyphPlacement glyph : staff.glyphs()) {
            if (glyph.glyph() == Glyph.NOTEHEAD_BLACK
                    || glyph.glyph() == Glyph.NOTEHEAD_HALF
                    || glyph.glyph() == Glyph.NOTEHEAD_WHOLE) {
                noteXs.add(glyph.x());
            }
        }
        assertEquals(8, noteXs.size());
        for (int i = 1; i < noteXs.size(); i++) {
            assertTrue(noteXs.get(i) > noteXs.get(i - 1), "note x positions must increase");
        }
    }

    @Test
    void hasGlyphsForClefAndNotes() {
        StaffLayout staff = new Engraver().layout(twoMeasureScale(), LayoutOptions.defaults()).staves().get(0);
        assertFalse(staff.glyphs().isEmpty());
        boolean hasClef = staff.glyphs().stream().anyMatch(g -> g.glyph() == Glyph.CLEF_G);
        assertTrue(hasClef, "expected a G clef glyph");
    }

    @Test
    void emitsKeySignatureAccidentals() {
        int divisions = 1;
        Measure.Builder m = Measure.builder(1).attributes(Attributes.builder()
                .divisions(divisions)
                .keySignature(new KeySignature(2))
                .timeSignature(TimeSignature.fourFour())
                .clef(Clef.treble())
                .build());
        m.addElement(Note.builder().pitch(new Pitch(Step.C, 4)).duration(new Duration(4, divisions)).type(NoteType.WHOLE).build());
        Score score = Score.builder().addPart(Part.builder("P1").addMeasure(m.build()).build()).build();

        StaffLayout staff = new Engraver().layout(score, LayoutOptions.defaults()).staves().get(0);
        long sharps = staff.glyphs().stream().filter(g -> g.glyph() == Glyph.ACCIDENTAL_SHARP).count();
        assertEquals(2, sharps, "D major should emit two sharps");
    }

    @Test
    void emitsFlagForUnbeamedEighth() {
        int divisions = 2;
        Measure.Builder m = Measure.builder(1).attributes(Attributes.builder()
                .divisions(divisions).clef(Clef.treble()).timeSignature(TimeSignature.fourFour()).build());
        m.addElement(Note.builder().pitch(new Pitch(Step.G, 4)).duration(new Duration(1, divisions)).type(NoteType.EIGHTH).build());
        Score score = Score.builder().addPart(Part.builder("P1").addMeasure(m.build()).build()).build();

        StaffLayout staff = new Engraver().layout(score, LayoutOptions.defaults()).staves().get(0);
        boolean hasFlag = staff.glyphs().stream().anyMatch(g ->
                g.glyph() == Glyph.FLAG_8TH_UP || g.glyph() == Glyph.FLAG_8TH_DOWN);
        assertTrue(hasFlag, "unbeamed eighth note should have a flag");
    }

    @Test
    void beamedNotesEmitBeamsNotFlags() {
        int divisions = 2;
        Measure.Builder m = Measure.builder(1).attributes(Attributes.builder()
                .divisions(divisions).clef(Clef.treble()).timeSignature(TimeSignature.fourFour()).build());
        m.addElement(Note.builder().pitch(new Pitch(Step.G, 4)).duration(new Duration(1, divisions)).type(NoteType.EIGHTH)
                .addBeam(new Beam(1, Beam.State.BEGIN)).build());
        m.addElement(Note.builder().pitch(new Pitch(Step.A, 4)).duration(new Duration(1, divisions)).type(NoteType.EIGHTH)
                .addBeam(new Beam(1, Beam.State.END)).build());
        Score score = Score.builder().addPart(Part.builder("P1").addMeasure(m.build()).build()).build();

        StaffLayout staff = new Engraver().layout(score, LayoutOptions.defaults()).staves().get(0);
        assertEquals(1, staff.beams().size(), "expected one beam segment for BEGIN..END");
        boolean hasFlag = staff.glyphs().stream().anyMatch(g ->
                g.glyph() == Glyph.FLAG_8TH_UP || g.glyph() == Glyph.FLAG_8TH_DOWN);
        assertFalse(hasFlag, "beamed notes must not carry flags");
    }

    @Test
    void emitsTieBetweenTiedNotes() {
        int divisions = 1;
        Measure.Builder m = Measure.builder(1).attributes(Attributes.builder()
                .divisions(divisions).clef(Clef.treble()).timeSignature(TimeSignature.fourFour()).build());
        m.addElement(Note.builder().pitch(new Pitch(Step.C, 4)).duration(new Duration(1, divisions))
                .type(NoteType.QUARTER).tieStart(true).build());
        m.addElement(Note.builder().pitch(new Pitch(Step.C, 4)).duration(new Duration(1, divisions))
                .type(NoteType.QUARTER).tieStop(true).build());
        Score score = Score.builder().addPart(Part.builder("P1").addMeasure(m.build()).build()).build();

        StaffLayout staff = new Engraver().layout(score, LayoutOptions.defaults()).staves().get(0);
        assertEquals(1, staff.ties().size());
    }

    @Test
    void multiStaffPartProducesTwoStaves() {
        int divisions = 1;
        Measure.Builder m = Measure.builder(1).attributes(Attributes.builder()
                .divisions(divisions)
                .staves(2)
                .addClef(Clef.treble())
                .addClef(Clef.bass())
                .timeSignature(TimeSignature.fourFour())
                .build());
        m.addElement(Note.builder().pitch(new Pitch(Step.C, 5)).duration(new Duration(4, divisions))
                .type(NoteType.WHOLE).staff(1).build());
        m.addElement(Note.builder().pitch(new Pitch(Step.C, 3)).duration(new Duration(4, divisions))
                .type(NoteType.WHOLE).staff(2).build());
        Score score = Score.builder().addPart(Part.builder("P1").addMeasure(m.build()).build()).build();

        LayoutResult layout = new Engraver().layout(score, LayoutOptions.defaults());
        assertEquals(2, layout.staves().size(), "grand-staff part must produce two StaffLayouts");

        long trebleClefs = layout.staves().get(0).glyphs().stream()
                .filter(g -> g.glyph() == Glyph.CLEF_G).count();
        long bassClefs = layout.staves().get(1).glyphs().stream()
                .filter(g -> g.glyph() == Glyph.CLEF_F).count();
        assertEquals(1, trebleClefs, "upper staff must carry a treble clef");
        assertEquals(1, bassClefs, "lower staff must carry a bass clef");

        long topNoteheads = layout.staves().get(0).glyphs().stream()
                .filter(g -> g.glyph() == Glyph.NOTEHEAD_WHOLE).count();
        long bottomNoteheads = layout.staves().get(1).glyphs().stream()
                .filter(g -> g.glyph() == Glyph.NOTEHEAD_WHOLE).count();
        assertEquals(1, topNoteheads, "only the staff-1 note should be routed to the top staff");
        assertEquals(1, bottomNoteheads, "only the staff-2 note should be routed to the bottom staff");
    }

    @Test
    void emitsAugmentationDot() {
        int divisions = 2;
        Measure.Builder m = Measure.builder(1).attributes(Attributes.builder()
                .divisions(divisions).clef(Clef.treble()).timeSignature(TimeSignature.fourFour()).build());
        m.addElement(Note.builder().pitch(new Pitch(Step.G, 4)).duration(new Duration(3, divisions))
                .type(NoteType.QUARTER).dots(1).build());
        Score score = Score.builder().addPart(Part.builder("P1").addMeasure(m.build()).build()).build();

        StaffLayout staff = new Engraver().layout(score, LayoutOptions.defaults()).staves().get(0);
        long dots = staff.glyphs().stream().filter(g -> g.glyph() == Glyph.AUG_DOT).count();
        assertEquals(1, dots);
        }
        }
