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
import com.sheetmusic4j.core.model.Score;
import com.sheetmusic4j.core.model.Step;
import com.sheetmusic4j.core.model.TimeSignature;

/**
 * Tests for the system-wide left barline emitted by Task 6.
 */
class EngraverSystemBarlineTest {

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

    private static Part namedPart(String id, String name) {
        return Part.builder(id).name(name)
                .addMeasure(measureWithAttributes(1).addElement(wholeC()).build())
                .build();
    }

    @Test
    void emitsOneLeftBarlinePerSystemNotOnePerStaff() {
        Score score = Score.builder()
                .addPart(namedPart("P1", "A"))
                .addPart(namedPart("P2", "B"))
                .addPart(namedPart("P3", "C"))
                .build();
        LayoutResult layout = new Engraver().layout(score, LayoutOptions.defaults());
        assertEquals(1, layout.systems().size(), "expected a single system");

        SystemLayout system = layout.systems().get(0);
        List<SystemBarline> barlines = system.barlines();
        assertEquals(1, barlines.size(), "expected exactly one system barline, got " + barlines);

        SystemBarline bar = barlines.get(0);
        StaffLayout firstStaff = system.staves().get(0);
        StaffLayout lastStaff = system.staves().get(system.staves().size() - 1);
        assertEquals(firstStaff.lineY(0), bar.topY(), 1e-6);
        assertEquals(lastStaff.lineY(4), bar.bottomY(), 1e-6);
        assertEquals(SystemBarline.LineStyle.THIN, bar.style());
    }

    @Test
    void systemBarlineXMatchesStaffLeftEdge() {
        Score score = Score.builder()
                .addPart(namedPart("P1", "Voice"))
                .build();
        LayoutResult layout = new Engraver().layout(score, LayoutOptions.defaults());
        SystemLayout system = layout.systems().get(0);
        StaffLayout staff = system.staves().get(0);
        SystemBarline bar = system.barlines().get(0);
        assertEquals(staff.x(), bar.x(), 1e-6,
                "system barline must sit at the same x as the staff's left edge");
    }

    @Test
    void noBarlinesForEmptyScore() {
        Score score = Score.builder().build();
        LayoutResult layout = new Engraver().layout(score, LayoutOptions.defaults());
        assertEquals(1, layout.systems().size());
        assertTrue(layout.systems().get(0).barlines().isEmpty(),
                "empty score must not carry any system barline");
    }

    @Test
    void barlineEmittedInEverySystem() {
        // 20 whole-note measures on a narrow system → multiple rows.
        int divisions = 1;
        Part.Builder pb = Part.builder("P1").name("Voice");
        for (int i = 0; i < 20; i++) {
            Measure.Builder mb = i == 0 ? measureWithAttributes(i + 1) : Measure.builder(i + 1);
            mb.addElement(Note.builder().pitch(new Pitch(Step.C, 4))
                    .duration(new Duration(4, divisions)).type(NoteType.WHOLE).build());
            pb.addMeasure(mb.build());
        }
        Score score = Score.builder().addPart(pb.build()).build();

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
        for (SystemLayout s : layout.systems()) {
            assertEquals(1, s.barlines().size(), "every non-empty system must carry a barline");
        }
    }
}
