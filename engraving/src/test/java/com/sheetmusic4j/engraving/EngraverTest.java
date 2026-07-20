package com.sheetmusic4j.engraving;

import com.sheetmusic4j.core.model.Attributes;
import com.sheetmusic4j.core.model.Clef;
import com.sheetmusic4j.core.model.Duration;
import com.sheetmusic4j.core.model.KeySignature;
import com.sheetmusic4j.core.model.Measure;
import com.sheetmusic4j.core.model.MusicElement;
import com.sheetmusic4j.core.model.Note;
import com.sheetmusic4j.core.model.Part;
import com.sheetmusic4j.core.model.Pitch;
import com.sheetmusic4j.core.model.Score;
import com.sheetmusic4j.core.model.Step;
import com.sheetmusic4j.core.model.TimeSignature;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
}
