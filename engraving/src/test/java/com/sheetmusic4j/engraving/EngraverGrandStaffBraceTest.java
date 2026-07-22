package com.sheetmusic4j.engraving;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.sheetmusic4j.core.model.Attributes;
import com.sheetmusic4j.core.model.Clef;
import com.sheetmusic4j.core.model.ClefSign;
import com.sheetmusic4j.core.model.Duration;
import com.sheetmusic4j.core.model.GroupSymbol;
import com.sheetmusic4j.core.model.KeySignature;
import com.sheetmusic4j.core.model.Measure;
import com.sheetmusic4j.core.model.Note;
import com.sheetmusic4j.core.model.NoteType;
import com.sheetmusic4j.core.model.Part;
import com.sheetmusic4j.core.model.PartGroup;
import com.sheetmusic4j.core.model.Pitch;
import com.sheetmusic4j.core.model.Score;
import com.sheetmusic4j.core.model.Step;
import com.sheetmusic4j.core.model.TimeSignature;

/**
 * Tests for the implicit grand-staff brace emitted by Task 6.
 */
class EngraverGrandStaffBraceTest {

    private static final int DIVISIONS = 1;

    private static Part grandStaffPiano(String id, String name) {
        Attributes attr = Attributes.builder()
                .divisions(DIVISIONS)
                .keySignature(KeySignature.cMajor())
                .timeSignature(TimeSignature.fourFour())
                .staves(2)
                .addClef(new Clef(ClefSign.G, 2))
                .addClef(new Clef(ClefSign.F, 4))
                .build();
        Note treble = Note.builder()
                .pitch(new Pitch(Step.C, 5))
                .duration(new Duration(4, DIVISIONS))
                .type(NoteType.WHOLE)
                .staff(1)
                .build();
        Note bass = Note.builder()
                .pitch(new Pitch(Step.C, 3))
                .duration(new Duration(4, DIVISIONS))
                .type(NoteType.WHOLE)
                .staff(2)
                .build();
        Measure measure = Measure.builder(1)
                .attributes(attr)
                .addElement(treble)
                .addElement(bass)
                .build();
        return Part.builder(id).name(name).addMeasure(measure).build();
    }

    private static Part voicePart(String id, String name) {
        Attributes attr = Attributes.builder()
                .divisions(DIVISIONS)
                .keySignature(KeySignature.cMajor())
                .timeSignature(TimeSignature.fourFour())
                .clef(Clef.treble())
                .build();
        Note note = Note.builder()
                .pitch(new Pitch(Step.C, 4))
                .duration(new Duration(4, DIVISIONS))
                .type(NoteType.WHOLE)
                .build();
        Measure measure = Measure.builder(1).attributes(attr).addElement(note).build();
        return Part.builder(id).name(name).addMeasure(measure).build();
    }

    @Test
    void piano2StavesEmitsBrace() {
        Score score = Score.builder().addPart(grandStaffPiano("P1", "Piano")).build();
        LayoutResult layout = new Engraver().layout(score, LayoutOptions.defaults());

        SystemLayout system = layout.systems().get(0);
        List<BracketPlacement> brackets = system.brackets();
        assertEquals(1, brackets.size(),
                "grand-staff piano must emit exactly one brace, got " + brackets);
        BracketPlacement brace = brackets.get(0);
        assertEquals(BracketPlacement.BracketShape.BRACE, brace.shape());

        assertEquals(2, system.staves().size());
        StaffLayout topStaff = system.staves().get(0);
        StaffLayout bottomStaff = system.staves().get(1);
        assertEquals(topStaff.lineY(0), brace.topY(), 1e-6);
        assertEquals(bottomStaff.lineY(4), brace.bottomY(), 1e-6);
        // Brace anchor sits to the left of the staff content edge.
        assertTrue(brace.x() < topStaff.x(),
                "brace x (" + brace.x() + ") must be left of staff x (" + topStaff.x() + ")");
    }

    @Test
    void singleStaffPartsHaveNoBrace() {
        Score score = Score.builder().addPart(voicePart("P1", "Voice")).build();
        LayoutResult layout = new Engraver().layout(score, LayoutOptions.defaults());
        assertTrue(layout.systems().get(0).brackets().isEmpty(),
                "single-staff part must not emit a brace");
    }

    @Test
    void multiplePartsEachMultiStaff() {
        Score score = Score.builder()
                .addPart(grandStaffPiano("P1", "Piano I"))
                .addPart(grandStaffPiano("P2", "Piano II"))
                .build();
        LayoutResult layout = new Engraver().layout(score, LayoutOptions.defaults());
        List<BracketPlacement> brackets = layout.systems().get(0).brackets();
        assertEquals(2, brackets.size(),
                "two grand-staff parts must yield two braces, got " + brackets);
        for (BracketPlacement br : brackets) {
            assertEquals(BracketPlacement.BracketShape.BRACE, br.shape());
        }
    }

    @Test
    void pianoBraceCoexistsWithOrchestralBracket() {
        Score score = Score.builder()
                .addPart(voicePart("P1", "V1"))
                .addPart(voicePart("P2", "V2"))
                .addPart(grandStaffPiano("P3", "Piano"))
                .addPartGroup(new PartGroup(1, 0, 2, GroupSymbol.BRACKET, false,
                        null, null))
                .build();
        LayoutResult layout = new Engraver().layout(score, LayoutOptions.defaults());
        SystemLayout system = layout.systems().get(0);
        List<BracketPlacement> brackets = system.brackets();
        BracketPlacement brace = null;
        BracketPlacement bracket = null;
        for (BracketPlacement b : brackets) {
            if (b.shape() == BracketPlacement.BracketShape.BRACE) {
                brace = b;
            } else if (b.shape() == BracketPlacement.BracketShape.BRACKET) {
                bracket = b;
            }
        }
        assertEquals(2, brackets.size(), "expected exactly 2 brackets, got " + brackets);
        assertTrue(brace != null && bracket != null,
                "expected both a BRACE and a BRACKET, got " + brackets);
        assertTrue(bracket.x() < brace.x(),
                "orchestral bracket (" + bracket.x() + ") must be left of piano brace (" + brace.x() + ")");
    }

    @Test
    void voicePlusPianoOnlyBracesThePiano() {
        Score score = Score.builder()
                .addPart(voicePart("P1", "Voice"))
                .addPart(grandStaffPiano("P2", "Piano"))
                .build();
        LayoutResult layout = new Engraver().layout(score, LayoutOptions.defaults());
        SystemLayout system = layout.systems().get(0);
        List<BracketPlacement> brackets = system.brackets();
        assertEquals(1, brackets.size(),
                "only the Piano part should be braced, got " + brackets);
        BracketPlacement brace = brackets.get(0);
        // The brace must span the piano's two staves (staves index 1 and 2).
        assertEquals(system.staves().get(1).lineY(0), brace.topY(), 1e-6);
        assertEquals(system.staves().get(2).lineY(4), brace.bottomY(), 1e-6);
    }
}
