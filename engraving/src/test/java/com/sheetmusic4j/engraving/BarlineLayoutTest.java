package com.sheetmusic4j.engraving;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.sheetmusic4j.core.model.Attributes;
import com.sheetmusic4j.core.model.Barline;
import com.sheetmusic4j.core.model.Clef;
import com.sheetmusic4j.core.model.Duration;
import com.sheetmusic4j.core.model.KeySignature;
import com.sheetmusic4j.core.model.Measure;
import com.sheetmusic4j.core.model.Note;
import com.sheetmusic4j.core.model.Part;
import com.sheetmusic4j.core.model.Pitch;
import com.sheetmusic4j.core.model.Score;
import com.sheetmusic4j.core.model.Step;
import com.sheetmusic4j.core.model.TimeSignature;
import com.sheetmusic4j.engraving.glyph.BarlineGeometry;
import com.sheetmusic4j.engraving.layout.LayoutOptions;
import com.sheetmusic4j.engraving.layout.LayoutResult;
import com.sheetmusic4j.engraving.layout.MeasureLayout;
import com.sheetmusic4j.engraving.layout.NoteAnchor;
import com.sheetmusic4j.engraving.layout.StaffLayout;

/**
 * Barlines and repeat signs: room is reserved for them, and a combined
 * end/start repeat splits across a system break.
 */
class BarlineLayoutTest {

    private static Note half(Step step) {
        return Note.builder().pitch(new Pitch(step, 4)).duration(new Duration(2, 1)).build();
    }

    private static Measure.Builder measure(int number) {
        Measure.Builder m = Measure.builder(number).addElement(half(Step.A)).addElement(half(Step.A));
        if (number == 1) {
            m.attributes(Attributes.builder().divisions(1).keySignature(KeySignature.cMajor())
                    .timeSignature(TimeSignature.fourFour()).clef(Clef.treble()).build());
        }
        return m;
    }

    private static LayoutResult layout(List<Measure> measures, double systemWidth) {
        Part.Builder part = Part.builder("P1");
        measures.forEach(part::addMeasure);
        return new Engraver().layout(Score.builder().addPart(part.build()).build(),
                LayoutOptions.builder().systemWidth(systemWidth).build());
    }

    @Test
    void repeatDotsKeepClearOfTheFollowingNote() {
        List<Measure> measures = List.of(
                measure(1).barline(new Barline(Barline.Style.DOUBLE, Barline.Repeat.BOTH)).build(),
                measure(2).build(),
                measure(3).leadingRepeatStart(true).build());
        LayoutResult layout = layout(measures, 1000);
        StaffLayout staff = layout.staves().get(0);
        double gap = staff.lineGap();
        double dotReach = (BarlineGeometry.THICK / 2 + BarlineGeometry.SEPARATION + BarlineGeometry.THIN
                + BarlineGeometry.DOT_SEPARATION + BarlineGeometry.DOT_DIAMETER) * gap;
        double noteHalf = 0.59 * gap;

        MeasureLayout second = staff.measures().get(1);
        double firstNoteOfSecond = firstNoteX(layout, 2);
        assertTrue(firstNoteOfSecond - noteHalf >= second.x() + dotReach + 0.5 * gap,
                "notehead must clear the dots of the preceding '::'");

        MeasureLayout third = staff.measures().get(2);
        double forwardReach = (BarlineGeometry.THICK + BarlineGeometry.SEPARATION + BarlineGeometry.THIN
                + BarlineGeometry.DOT_SEPARATION + BarlineGeometry.DOT_DIAMETER) * gap;
        assertTrue(firstNoteX(layout, 3) - noteHalf >= third.leadingBarlineX() + forwardReach + 0.5 * gap,
                "notehead must clear the dots of its own '|:'");
    }

    @Test
    void endStartRepeatSplitsAcrossASystemBreak() {
        // Every measure ends with "::", so every system break splits one.
        List<Measure> measures = new ArrayList<>();
        for (int i = 1; i <= 12; i++) {
            measures.add(measure(i).barline(new Barline(Barline.Style.DOUBLE, Barline.Repeat.BOTH)).build());
        }
        List<StaffLayout> staves = layout(measures, 500).staves();
        assertTrue(staves.size() > 1, "test setup: the score must span several systems");
        for (int row = 0; row + 1 < staves.size(); row++) {
            List<MeasureLayout> rowMeasures = staves.get(row).measures();
            assertEquals(Barline.Repeat.BACKWARD, rowMeasures.get(rowMeasures.size() - 1).barline().repeat(),
                    "a system ends with just the repeat-end half");
            assertEquals(Barline.Repeat.BOTH, rowMeasures.get(0).barline().repeat(),
                    "inside a system '::' stays whole");
            MeasureLayout firstOfNextRow = staves.get(row + 1).measures().get(0);
            assertTrue(firstOfNextRow.leadingRepeatStart(), "the repeat-start half moves to the new system");
            assertTrue(firstOfNextRow.leadingBarlineX() > firstOfNextRow.x(),
                    "after the clef, not at the staff edge");
        }
    }

    private static double firstNoteX(LayoutResult layout, int measureNumber) {
        return layout.noteAnchors().stream()
                .filter(a -> a.measureNumber() == measureNumber)
                .mapToDouble(NoteAnchor::x)
                .min().orElseThrow();
    }
}
