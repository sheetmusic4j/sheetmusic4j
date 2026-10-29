package com.sheetmusic4j.engraving;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import com.sheetmusic4j.core.model.Slur;
import com.sheetmusic4j.core.model.Step;
import com.sheetmusic4j.core.model.TimeSignature;
import com.sheetmusic4j.engraving.layout.LayoutOptions;
import com.sheetmusic4j.engraving.layout.LayoutResult;
import com.sheetmusic4j.engraving.layout.StaffLayout;
import com.sheetmusic4j.engraving.placement.SlurPlacement;
import com.sheetmusic4j.engraving.placement.TiePlacement;

/**
 * A tie or slur between two notes that land in different systems is drawn as two half arcs: from the first note to
 * the end of its staff, and from the start of the next staff to the second note.
 */
class TieAcrossSystemsTest {

    /** Two whole-note bars joined by a tie and a slur; {@code breakBetween} puts bar 2 on a new system. */
    private static LayoutResult layout(boolean breakBetween) {
        Measure first = Measure.builder(1)
                .attributes(Attributes.builder().divisions(1).keySignature(KeySignature.cMajor())
                        .timeSignature(TimeSignature.fourFour()).clef(Clef.treble()).build())
                .addElement(Note.builder().pitch(new Pitch(Step.G, 4)).duration(new Duration(4, 1))
                        .type(NoteType.WHOLE).tieStart(true).addSlur(new Slur(1, Slur.Type.START, Placement.ABOVE)).build())
                .build();
        Measure second = Measure.builder(2).forceSystemBreak(breakBetween)
                .addElement(Note.builder().pitch(new Pitch(Step.G, 4)).duration(new Duration(4, 1))
                        .type(NoteType.WHOLE).tieStop(true).addSlur(new Slur(1, Slur.Type.STOP, Placement.ABOVE)).build())
                .build();
        Score score = Score.builder().addPart(Part.builder("P1").addMeasure(first).addMeasure(second).build()).build();
        return new Engraver().layout(score, LayoutOptions.defaults());
    }

    @Test
    void tieInOneSystemIsOneArc() {
        List<StaffLayout> staves = layout(false).staves();

        assertEquals(1, staves.size());
        assertEquals(1, staves.get(0).ties().size());
        assertEquals(1, staves.get(0).slurs().size());
    }

    @Test
    void tieAcrossASystemBreakIsDrawnInBothSystems() {
        List<StaffLayout> staves = layout(true).staves();

        assertEquals(2, staves.size(), "the break makes two systems");
        StaffLayout upper = staves.get(0);
        StaffLayout lower = staves.get(1);
        double upperEnd = upper.x() + upper.width();

        TiePlacement runsOn = upper.ties().get(0);
        assertEquals(upperEnd, runsOn.x2(), 1e-9, "the first half reaches the end of the staff");
        assertTrue(runsOn.x1() < runsOn.x2());

        TiePlacement continues = lower.ties().get(0);
        assertTrue(continues.x1() < continues.x2(), "the second half ends at its note");
        assertTrue(continues.x1() >= lower.x(), "and starts inside the staff");
        assertEquals(1, lower.ties().size());
    }

    @Test
    void slurAcrossASystemBreakIsDrawnInBothSystems() {
        List<StaffLayout> staves = layout(true).staves();

        SlurPlacement runsOn = staves.get(0).slurs().get(0);
        assertEquals(staves.get(0).x() + staves.get(0).width(), runsOn.x2(), 1e-9);
        SlurPlacement continues = staves.get(1).slurs().get(0);
        assertTrue(continues.x1() < continues.x2());
        assertTrue(runsOn.curveUp() && continues.curveUp(), "the placement is kept in both halves");
    }
}
