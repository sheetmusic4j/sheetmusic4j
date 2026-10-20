package com.sheetmusic4j.engraving;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
import com.sheetmusic4j.core.model.Placement;
import com.sheetmusic4j.core.model.Score;
import com.sheetmusic4j.core.model.Step;
import com.sheetmusic4j.core.model.TimeModification;
import com.sheetmusic4j.core.model.TimeSignature;
import com.sheetmusic4j.core.model.Tuplet;
import com.sheetmusic4j.engraving.layout.LayoutOptions;
import com.sheetmusic4j.engraving.layout.StaffLayout;
import com.sheetmusic4j.engraving.placement.TupletPlacement;

/**
 * Tuplet numbers and brackets go on the stem side unless the tuplet says
 * where, and show their own count when nested.
 */
class TupletPlacementTest {

    /** A 1/4 measure holding one triplet of eighths at the given pitch. */
    private static TupletPlacement layoutTriplet(Pitch pitch, Placement placement, int actualNotes) {
        int divisions = 3;
        Measure.Builder m = Measure.builder(1).attributes(Attributes.builder()
                .divisions(divisions)
                .keySignature(KeySignature.cMajor())
                .timeSignature(new TimeSignature(1, 4))
                .clef(Clef.treble())
                .build());
        for (int i = 0; i < 3; i++) {
            Note.Builder nb = Note.builder()
                    .pitch(pitch)
                    .duration(new Duration(1, divisions))
                    .type(NoteType.EIGHTH)
                    .timeModification(new TimeModification(3, 2));
            if (i == 0) {
                nb.addTuplet(new Tuplet(1, Tuplet.Type.START, true, actualNotes, 2, placement));
            }
            if (i == 2) {
                nb.addTuplet(new Tuplet(1, Tuplet.Type.STOP, true));
            }
            m.addElement(nb.build());
        }
        Score score = Score.builder().addPart(Part.builder("P1").addMeasure(m.build()).build()).build();
        StaffLayout staff = new Engraver().layout(score, LayoutOptions.defaults()).staves().get(0);
        List<TupletPlacement> tuplets = staff.tuplets();
        assertEquals(1, tuplets.size());
        return tuplets.get(0);
    }

    @Test
    void stemsUpPutTheTupletAbove() {
        assertTrue(layoutTriplet(new Pitch(Step.E, 4), Placement.DEFAULT, 0).above());
    }

    @Test
    void stemsDownPutTheTupletBelow() {
        TupletPlacement tuplet = layoutTriplet(new Pitch(Step.E, 5), Placement.DEFAULT, 0);
        assertFalse(tuplet.above());
    }

    @Test
    void explicitPlacementWinsOverTheStemSide() {
        assertTrue(layoutTriplet(new Pitch(Step.E, 5), Placement.ABOVE, 0).above());
        assertFalse(layoutTriplet(new Pitch(Step.E, 4), Placement.BELOW, 0).above());
    }

    @Test
    void tupletShowsItsOwnCountOverTheTimeModification() {
        assertEquals(3, layoutTriplet(new Pitch(Step.E, 4), Placement.DEFAULT, 0).number());
        assertEquals(5, layoutTriplet(new Pitch(Step.E, 4), Placement.DEFAULT, 5).number());
    }
}
