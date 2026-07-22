package com.sheetmusic4j.engraving;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

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
import com.sheetmusic4j.engraving.glyph.Glyph;
import com.sheetmusic4j.engraving.layout.LayoutOptions;
import com.sheetmusic4j.engraving.layout.LayoutResult;
import com.sheetmusic4j.engraving.layout.MeasureLayout;
import com.sheetmusic4j.engraving.layout.StaffLayout;
import com.sheetmusic4j.engraving.placement.GlyphPlacement;

/**
 * Verifies that dense measures (many short notes) receive proportionally
 * more horizontal room than sparse measures, and that intra-measure
 * stepping respects the {@code MIN_NOTE_ADVANCE_GAPS} safety rail.
 */
class MeasureSpacingTest {

    @Test
    void denseMeasureIsWiderThanSparseMeasure() {
        LayoutOptions options = LayoutOptions.defaults();
        LayoutResult layout = new Engraver().layout(quartersThenSixteenths(), options);
        StaffLayout staff = layout.staves().get(0);
        List<MeasureLayout> measures = staff.measures();
        // Measure 1 = 4 quarters, measure 2 = 16 sixteenths (same total duration).
        double m1Width = measures.get(0).width();
        double m2Width = measures.get(1).width();
        // The first measure width includes the row-start header (clef/key/time),
        // so compare note-spacing capacity by removing that reserve.
        double header = Engraver.headerAdvance(Clef.treble(), KeySignature.cMajor(),
                TimeSignature.fourFour(), options);
        double m1ContentWidth = m1Width - header;
        assertTrue(m2Width > m1ContentWidth * 1.5,
                "16ths measure content should be at least 1.5x as wide as a 4-quarter content span; "
                        + "m1Content=" + m1ContentWidth + " m2=" + m2Width + " header=" + header);
    }

    @Test
    void denseMeasureNoteDeltasClearMinAdvance() {
        LayoutOptions options = LayoutOptions.defaults();
        LayoutResult layout = new Engraver().layout(quartersThenSixteenths(), options);
        StaffLayout staff = layout.staves().get(0);

        double m2Left = staff.measures().get(1).x();
        double m2Right = staff.measures().get(1).right();

        List<Double> noteXs = new ArrayList<>();
        for (GlyphPlacement g : staff.glyphs()) {
            if (g.glyph() != Glyph.NOTEHEAD_BLACK) {
                continue;
            }
            if (g.x() >= m2Left && g.x() <= m2Right) {
                noteXs.add(g.x());
            }
        }
        assertTrue(noteXs.size() >= 2, "expected multiple 16th noteheads in measure 2");

        double minDelta = 1.2 * options.staffLineGap() * 0.95; // MIN_NOTE_ADVANCE_GAPS * gap, small tolerance
        for (int i = 1; i < noteXs.size(); i++) {
            double delta = noteXs.get(i) - noteXs.get(i - 1);
            assertTrue(delta >= minDelta,
                    "consecutive 16th note x-delta must clear MIN_NOTE_ADVANCE_GAPS: delta=" + delta
                            + " min=" + minDelta);
        }
    }

    private static Score quartersThenSixteenths() {
        int divisions = 4; // 4 divisions per quarter -> 1 division = 16th
        Measure.Builder m1 = Measure.builder(1).attributes(Attributes.builder()
                .divisions(divisions)
                .keySignature(KeySignature.cMajor())
                .timeSignature(TimeSignature.fourFour())
                .clef(Clef.treble())
                .build());
        Step[] quarters = {Step.C, Step.D, Step.E, Step.F};
        for (Step s : quarters) {
            m1.addElement(Note.builder()
                    .pitch(new Pitch(s, 4))
                    .duration(new Duration(4, divisions))
                    .type(NoteType.QUARTER)
                    .build());
        }

        Measure.Builder m2 = Measure.builder(2);
        Step[] sixteenths = {
                Step.C, Step.D, Step.E, Step.F, Step.G, Step.A, Step.B, Step.C,
                Step.C, Step.D, Step.E, Step.F, Step.G, Step.A, Step.B, Step.C
        };
        for (int i = 0; i < sixteenths.length; i++) {
            int octave = i >= 7 && i <= 8 ? 5 : (i > 8 ? 5 : 4);
            Note.Builder nb = Note.builder()
                    .pitch(new Pitch(sixteenths[i], octave))
                    .duration(new Duration(1, divisions))
                    .type(NoteType.SIXTEENTH);
            // Beam groups of 4.
            int mod = i % 4;
            Beam.State state;
            if (mod == 0) {
                state = Beam.State.BEGIN;
            } else if (mod == 3) {
                state = Beam.State.END;
            } else {
                state = Beam.State.CONTINUE;
            }
            nb.addBeam(new Beam(1, state));
            nb.addBeam(new Beam(2, state));
            m2.addElement(nb.build());
        }

        Part part = Part.builder("P1").name("Piano")
                .addMeasure(m1.build())
                .addMeasure(m2.build())
                .build();
        return Score.builder().addPart(part).build();
    }
}
