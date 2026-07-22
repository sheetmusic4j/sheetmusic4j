package com.sheetmusic4j.engraving;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.sheetmusic4j.core.model.Attributes;
import com.sheetmusic4j.core.model.Clef;
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
 * Tests for the engraver's handling of {@link PartGroup part groups}.
 */
class EngraverPartGroupTest {

    private static final int DIVISIONS = 1;

    private static Part singleStaffPart(String id, String name) {
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

    private static LayoutResult layoutOf(Score score) {
        return new Engraver().layout(score, LayoutOptions.defaults());
    }

    @Test
    void singleBracketOverTwoPartsEmitsBracketPlacement() {
        Score score = Score.builder()
                .addPart(singleStaffPart("P1", "A"))
                .addPart(singleStaffPart("P2", "B"))
                .addPartGroup(new PartGroup(1, 0, 1, GroupSymbol.BRACKET, false,
                        null, null))
                .build();
        LayoutResult layout = layoutOf(score);
        SystemLayout system = layout.systems().get(0);
        List<BracketPlacement> brackets = system.brackets();
        assertEquals(1, brackets.size(), "expected exactly one bracket, got " + brackets);
        BracketPlacement bracket = brackets.get(0);
        assertEquals(BracketPlacement.BracketShape.BRACKET, bracket.shape());
        StaffLayout top = system.staves().get(0);
        StaffLayout bottom = system.staves().get(1);
        assertEquals(top.lineY(0), bracket.topY(), 1e-6);
        assertEquals(bottom.lineY(4), bracket.bottomY(), 1e-6);
        assertTrue(bracket.x() < top.x(),
                "bracket x (" + bracket.x() + ") must be left of staff x (" + top.x() + ")");
    }

    @Test
    void nestedGroupsSitAtDifferentX() {
        Score score = Score.builder()
                .addPart(singleStaffPart("P1", "A"))
                .addPart(singleStaffPart("P2", "B"))
                .addPart(singleStaffPart("P3", "C"))
                .addPart(singleStaffPart("P4", "D"))
                .addPartGroup(new PartGroup(1, 0, 3, GroupSymbol.BRACKET, false,
                        null, null))
                .addPartGroup(new PartGroup(2, 0, 1, GroupSymbol.BRACE, false,
                        null, null))
                .build();
        LayoutResult layout = layoutOf(score);
        SystemLayout system = layout.systems().get(0);
        List<BracketPlacement> brackets = system.brackets();
        BracketPlacement outer = null;
        BracketPlacement inner = null;
        for (BracketPlacement b : brackets) {
            if (b.shape() == BracketPlacement.BracketShape.BRACKET) {
                outer = b;
            } else if (b.shape() == BracketPlacement.BracketShape.BRACE) {
                inner = b;
            }
        }
        assertNotNull(outer, "expected an outer BRACKET placement, got " + brackets);
        assertNotNull(inner, "expected an inner BRACE placement, got " + brackets);
        assertTrue(outer.x() < inner.x(),
                "outer bracket x (" + outer.x() + ") must be left of inner brace x (" + inner.x() + ")");
    }

    @Test
    void groupBarlineYesAddsExtraSystemBarline() {
        Score score = Score.builder()
                .addPart(singleStaffPart("P1", "A"))
                .addPart(singleStaffPart("P2", "B"))
                .addPart(singleStaffPart("P3", "C"))
                .addPartGroup(new PartGroup(1, 0, 1, GroupSymbol.BRACKET, true,
                        null, null))
                .build();
        LayoutResult layout = layoutOf(score);
        SystemLayout system = layout.systems().get(0);
        assertEquals(2, system.barlines().size(),
                "expected system + group barline, got " + system.barlines());
    }

    @Test
    void groupSymbolLineEmitsThinLine() {
        Score score = Score.builder()
                .addPart(singleStaffPart("P1", "A"))
                .addPart(singleStaffPart("P2", "B"))
                .addPartGroup(new PartGroup(1, 0, 1, GroupSymbol.LINE, false,
                        null, null))
                .build();
        LayoutResult layout = layoutOf(score);
        SystemLayout system = layout.systems().get(0);
        assertEquals(1, system.brackets().size());
        assertEquals(BracketPlacement.BracketShape.LINE,
                system.brackets().get(0).shape());
    }

    @Test
    void groupSymbolNoneEmitsNothing() {
        Score score = Score.builder()
                .addPart(singleStaffPart("P1", "A"))
                .addPart(singleStaffPart("P2", "B"))
                .addPartGroup(new PartGroup(1, 0, 1, GroupSymbol.NONE, false,
                        null, null))
                .build();
        LayoutResult layout = layoutOf(score);
        SystemLayout system = layout.systems().get(0);
        assertTrue(system.brackets().isEmpty(),
                "GroupSymbol.NONE must not emit any bracket, got " + system.brackets());
    }

    @Test
    void groupNameAppearsAsPartLabel() {
        Score score = Score.builder()
                .addPart(singleStaffPart("P1", "H1"))
                .addPart(singleStaffPart("P2", "H2"))
                .addPartGroup(new PartGroup(1, 0, 1, GroupSymbol.BRACE, false,
                        "Horns in F", "Hn."))
                .build();
        LayoutResult layout = layoutOf(score);
        TextPlacement groupLabel = null;
        for (TextPlacement text : layout.texts()) {
            if (text.category() == MarkingCategory.PART_LABEL
                    && "Horns in F".equals(text.text())) {
                groupLabel = text;
                break;
            }
        }
        assertNotNull(groupLabel,
                "expected a PART_LABEL with the group name, texts=" + layout.texts());
        // Group label is right-aligned so its anchor sits at the bracket
        // column, i.e. between the page's left margin and the system's
        // content edge.
        SystemLayout system = layout.systems().get(0);
        StaffLayout staff = system.staves().get(0);
        assertTrue(groupLabel.x() < staff.x(),
                "group label x (" + groupLabel.x() + ") must sit left of staff x (" + staff.x() + ")");
        assertEquals(TextPlacement.Align.RIGHT, groupLabel.align());
    }

    @Test
    void groupAbbreviationUsedOnContinuationRows() {
        // 20 whole-note measures on a narrow system → multiple rows.
        Part.Builder pb1 = Part.builder("P1").name("H1").abbreviation("H1");
        Part.Builder pb2 = Part.builder("P2").name("H2").abbreviation("H2");
        for (int i = 0; i < 20; i++) {
            Measure.Builder m1 = i == 0
                    ? Measure.builder(i + 1).attributes(Attributes.builder()
                            .divisions(DIVISIONS)
                            .keySignature(KeySignature.cMajor())
                            .timeSignature(TimeSignature.fourFour())
                            .clef(Clef.treble())
                            .build())
                    : Measure.builder(i + 1);
            m1.addElement(Note.builder().pitch(new Pitch(Step.C, 4))
                    .duration(new Duration(4, DIVISIONS)).type(NoteType.WHOLE).build());
            pb1.addMeasure(m1.build());
            Measure.Builder m2 = i == 0
                    ? Measure.builder(i + 1).attributes(Attributes.builder()
                            .divisions(DIVISIONS)
                            .keySignature(KeySignature.cMajor())
                            .timeSignature(TimeSignature.fourFour())
                            .clef(Clef.treble())
                            .build())
                    : Measure.builder(i + 1);
            m2.addElement(Note.builder().pitch(new Pitch(Step.C, 4))
                    .duration(new Duration(4, DIVISIONS)).type(NoteType.WHOLE).build());
            pb2.addMeasure(m2.build());
        }
        Score score = Score.builder()
                .addPart(pb1.build())
                .addPart(pb2.build())
                .addPartGroup(new PartGroup(1, 0, 1, GroupSymbol.BRACE, false,
                        "Horns in F", "Hn."))
                .build();
        LayoutOptions defaults = LayoutOptions.defaults();
        LayoutOptions narrow = new LayoutOptions(
                defaults.staffLineGap(),
                defaults.staffSpacing(),
                600,
                defaults.leftMargin(),
                defaults.rightMargin(),
                defaults.topMargin(),
                defaults.measureMinWidth(),
                defaults.fontSize());
        LayoutResult layout = new Engraver().layout(score, narrow);
        assertTrue(layout.systems().size() >= 2, "expected at least 2 systems");
        long fullNameCount = layout.texts().stream()
                .filter(t -> t.category() == MarkingCategory.PART_LABEL
                        && "Horns in F".equals(t.text()))
                .count();
        long abbrCount = layout.texts().stream()
                .filter(t -> t.category() == MarkingCategory.PART_LABEL
                        && "Hn.".equals(t.text()))
                .count();
        assertEquals(1, fullNameCount, "full group name should appear once (on row 0)");
        assertEquals(layout.systems().size() - 1, abbrCount,
                "group abbreviation should appear on every continuation row");
    }
}
