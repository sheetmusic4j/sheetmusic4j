package com.sheetmusic4j.engraving;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.sheetmusic4j.core.model.Attributes;
import com.sheetmusic4j.core.model.Clef;
import com.sheetmusic4j.core.model.ClefSign;
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

/**
 * Tests for the instrument label emission introduced in Task 6.
 */
class EngraverPartLabelTest {

    private static final int DIVISIONS = 1;

    private static Measure.Builder measureWithAttributes(int number) {
        return Measure.builder(number).attributes(Attributes.builder()
                .divisions(DIVISIONS)
                .keySignature(KeySignature.cMajor())
                .timeSignature(TimeSignature.fourFour())
                .clef(Clef.treble())
                .build());
    }

    private static Note wholeC() {
        return Note.builder()
                .pitch(new Pitch(Step.C, 4))
                .duration(new Duration(4, DIVISIONS))
                .type(NoteType.WHOLE)
                .build();
    }

    private static List<TextPlacement> partLabels(LayoutResult layout) {
        return layout.texts().stream()
                .filter(t -> t.category() == MarkingCategory.PART_LABEL)
                .toList();
    }

    private static Score singlePartScore(String name, String abbreviation, int measureCount) {
        Part.Builder pb = Part.builder("P1");
        if (name != null) {
            pb.name(name);
        }
        if (abbreviation != null) {
            pb.abbreviation(abbreviation);
        }
        for (int i = 0; i < measureCount; i++) {
            Measure.Builder mb = i == 0 ? measureWithAttributes(i + 1) : Measure.builder(i + 1);
            mb.addElement(wholeC());
            pb.addMeasure(mb.build());
        }
        return Score.builder().addPart(pb.build()).build();
    }

    @Test
    void emitsPartNameLabelOnFirstSystem() {
        Score score = singlePartScore("Voice", null, 1);
        LayoutResult layout = new Engraver().layout(score, LayoutOptions.defaults());

        List<TextPlacement> labels = partLabels(layout);
        assertEquals(1, labels.size(), "expected exactly one PART_LABEL placement");
        assertEquals("Voice", labels.get(0).text());
        assertEquals(TextPlacement.Align.LEFT, labels.get(0).align());
    }

    @Test
    void emitsAbbreviationOnLaterSystems() {
        Score score = singlePartScore("Voice", "Vo.", 20);

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
        assertTrue(layout.systems().size() >= 2,
                "expected at least two systems; got " + layout.systems().size());

        List<TextPlacement> labels = partLabels(layout);
        assertTrue(labels.size() >= 2, "expected at least one label per system, got " + labels);
        assertEquals("Voice", labels.get(0).text(), "first-row label must be the full name");
        assertEquals("Vo.", labels.get(1).text(), "second-row label must be the abbreviation");
    }

    @Test
    void fallsBackToNameWhenAbbreviationMissingOnLaterSystems() {
        Score score = singlePartScore("Voice", null, 20);

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
        assertTrue(layout.systems().size() >= 2);

        List<TextPlacement> labels = partLabels(layout);
        assertTrue(labels.size() >= 2);
        assertEquals("Voice", labels.get(0).text());
        assertEquals("Voice", labels.get(1).text(),
                "when abbreviation is null the full name is reused on continuation systems");
    }

    @Test
    void labelReserveShiftsStaffRight() {
        LayoutOptions options = LayoutOptions.defaults();

        Score unlabeled = singlePartScore(null, null, 1);
        LayoutResult withoutLabel = new Engraver().layout(unlabeled, options);
        double xWithoutLabel = withoutLabel.staves().get(0).x();

        Score labeled = singlePartScore("Bass Clarinet in B\u266D", null, 1);
        LayoutResult withLabel = new Engraver().layout(labeled, options);
        double xWithLabel = withLabel.staves().get(0).x();

        assertTrue(xWithLabel > xWithoutLabel,
                "labeled score staff x (" + xWithLabel + ") must exceed unlabeled ("
                        + xWithoutLabel + ")");
        // The reserve must roughly track the label length: at least half a
        // character-width per character.
        double gap = options.staffLineGap();
        double fontSize = gap * 1.4;
        double minExpected = "Bass Clarinet in B\u266D".length() * 0.55 * fontSize * 0.5;
        assertTrue(xWithLabel - xWithoutLabel >= minExpected,
                "reserve too small: delta=" + (xWithLabel - xWithoutLabel));
    }

    @Test
    void noLabelWhenPartNameIsNull() {
        Score score = singlePartScore(null, null, 1);
        LayoutOptions options = LayoutOptions.defaults();
        LayoutResult layout = new Engraver().layout(score, options);

        assertTrue(partLabels(layout).isEmpty(),
                "unlabeled score must not emit a PART_LABEL placement");
        // And the staff must still sit exactly at leftMargin — no reserve.
        assertEquals(options.leftMargin(), layout.staves().get(0).x(), 1e-6);
    }

    @Test
    void noLabelWhenPartNameIsBlank() {
        Score score = singlePartScore("   ", null, 1);
        LayoutResult layout = new Engraver().layout(score, LayoutOptions.defaults());
        assertTrue(partLabels(layout).isEmpty(),
                "blank part name must not emit a label");
    }

    @Test
    void partLabelSitsBetweenStavesOfMultiStaffPart() {
        // Build a 2-staff piano-style part.
        Attributes attr = Attributes.builder()
                .divisions(DIVISIONS)
                .staves(2)
                .addClef(new Clef(ClefSign.G, 2))
                .addClef(new Clef(ClefSign.F, 4))
                .build();
        Note noteS1 = Note.builder()
                .pitch(new Pitch(Step.C, 5))
                .duration(new Duration(4, DIVISIONS))
                .type(NoteType.WHOLE)
                .staff(1)
                .build();
        Note noteS2 = Note.builder()
                .pitch(new Pitch(Step.C, 3))
                .duration(new Duration(4, DIVISIONS))
                .type(NoteType.WHOLE)
                .staff(2)
                .build();
        Measure measure = Measure.builder(1)
                .attributes(attr)
                .addElement(noteS1)
                .addElement(noteS2)
                .build();
        Part part = Part.builder("P1").name("Piano").addMeasure(measure).build();
        Score score = Score.builder().addPart(part).build();

        LayoutOptions options = LayoutOptions.defaults();
        LayoutResult layout = new Engraver().layout(score, options);

        List<TextPlacement> labels = partLabels(layout);
        assertEquals(1, labels.size(),
                "grand-staff Piano must emit exactly ONE label per row, got " + labels);
        TextPlacement label = labels.get(0);
        assertEquals("Piano", label.text());

        List<StaffLayout> staves = layout.staves();
        assertEquals(2, staves.size());
        double topStaffTop = staves.get(0).lineY(0);
        double bottomStaffBottom = staves.get(1).lineY(4);
        assertTrue(label.y() > topStaffTop && label.y() < bottomStaffBottom,
                "label y (" + label.y() + ") must sit between the two staves ("
                        + topStaffTop + ".." + bottomStaffBottom + ")");
    }

    @Test
    void multiplePartsEmitOneLabelPerPart() {
        Part p1 = Part.builder("P1").name("Voice")
                .addMeasure(measureWithAttributes(1).addElement(wholeC()).build())
                .build();
        Part p2 = Part.builder("P2").name("Piano")
                .addMeasure(measureWithAttributes(1).addElement(wholeC()).build())
                .build();
        Score score = Score.builder().addPart(p1).addPart(p2).build();
        LayoutResult layout = new Engraver().layout(score, LayoutOptions.defaults());

        List<TextPlacement> labels = partLabels(layout);
        assertEquals(2, labels.size(), "expected one label per part per row");
        assertEquals("Voice", labels.get(0).text());
        assertEquals("Piano", labels.get(1).text());
    }

    @Test
    void labelUsesLeftMarginAnchor() {
        Score score = singlePartScore("Voice", null, 1);
        LayoutOptions options = LayoutOptions.defaults();
        LayoutResult layout = new Engraver().layout(score, options);
        TextPlacement label = partLabels(layout).get(0);
        assertNotNull(label);
        assertEquals(options.leftMargin(), label.x(), 1e-6,
                "label x anchor must be the raw leftMargin, not the shifted contentLeft");
    }

    @Test
    void partLabelIsCategorizedPartLabel() {
        Score score = singlePartScore("Voice", null, 1);
        LayoutResult layout = new Engraver().layout(score, LayoutOptions.defaults());
        boolean anyOther = layout.texts().stream()
                .anyMatch(t -> "Voice".equals(t.text()) && t.category() != MarkingCategory.PART_LABEL);
        assertFalse(anyOther, "\"Voice\" text must only be emitted under PART_LABEL");
    }
}
