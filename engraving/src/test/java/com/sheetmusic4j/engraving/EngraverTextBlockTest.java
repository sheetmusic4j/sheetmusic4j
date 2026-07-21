package com.sheetmusic4j.engraving;

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
import com.sheetmusic4j.core.model.Score;
import com.sheetmusic4j.core.model.Step;
import com.sheetmusic4j.core.model.TimeSignature;

class EngraverTextBlockTest {

    private static Score minimalScore(String workTitle, String movementTitle) {
        int divisions = 1;
        Measure.Builder m = Measure.builder(1).attributes(Attributes.builder()
                .divisions(divisions)
                .keySignature(KeySignature.cMajor())
                .timeSignature(TimeSignature.fourFour())
                .clef(Clef.treble())
                .build());
        m.addElement(Note.builder().pitch(new Pitch(Step.C, 4)).duration(new Duration(4, divisions))
                .type(NoteType.WHOLE).build());

        Score.Builder score = Score.builder().addPart(Part.builder("P1").addMeasure(m.build()).build());
        if (workTitle != null) {
            score.workTitle(workTitle);
        }
        if (movementTitle != null) {
            score.movementTitle(movementTitle);
        }
        return score.build();
    }

    @Test
    void emitsWorkTitleAsCenteredText() {
        LayoutResult layout = new Engraver().layout(minimalScore("Do-Re-Mi", null),
                LayoutOptions.defaults());
        assertEquals(1, layout.texts().size());
        TextPlacement t = layout.texts().get(0);
        assertEquals("Do-Re-Mi", t.text());
        assertEquals(TextPlacement.Align.CENTER, t.align());
        assertTrue(t.fontSize() > 0, "title should carry a positive font size");
    }

    @Test
    void emitsBothWorkAndMovementTitles() {
        LayoutResult layout = new Engraver().layout(
                minimalScore("Symphony No. 5", "I. Allegro con brio"),
                LayoutOptions.defaults());
        assertEquals(2, layout.texts().size());
        assertEquals("Symphony No. 5", layout.texts().get(0).text());
        assertEquals("I. Allegro con brio", layout.texts().get(1).text());
    }

    @Test
    void emptyMetadataProducesNoText() {
        LayoutResult layout = new Engraver().layout(minimalScore(null, null),
                LayoutOptions.defaults());
        assertTrue(layout.texts().isEmpty());
    }

    @Test
    void titleBlockPushesFirstStaffDownward() {
        LayoutOptions defaults = LayoutOptions.defaults();
        LayoutResult withTitle = new Engraver().layout(minimalScore("Piece", null), defaults);
        LayoutResult withoutTitle = new Engraver().layout(minimalScore(null, null), defaults);
        double titledY = withTitle.staves().get(0).y();
        double untitledY = withoutTitle.staves().get(0).y();
        assertTrue(titledY > untitledY,
                "title block must push the first staff down (with-title=" + titledY
                        + ", without-title=" + untitledY + ")");
    }

    @Test
    void duplicateWorkAndMovementTitleEmitsOnlyOne() {
        LayoutResult layout = new Engraver().layout(minimalScore("Do-Re-Mi", "Do-Re-Mi"),
                LayoutOptions.defaults());
        assertEquals(1, layout.texts().size(),
                "identical work/movement titles should not be duplicated");
        assertFalse(layout.texts().isEmpty());
    }
}
