package com.sheetmusic4j.engraving;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.sheetmusic4j.core.model.Attributes;
import com.sheetmusic4j.core.model.Clef;
import com.sheetmusic4j.core.model.Creator;
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

    private static Score minimalScoreWithCreators(Creator... creators) {
        Score.Builder builder = baseScoreBuilder("Symphony", null);
        for (Creator c : creators) {
            builder.addCreator(c);
        }
        return builder.build();
    }

    private static Score.Builder baseScoreBuilder(String workTitle, String movementTitle) {
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
        return score;
    }

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
        void titlePlacementsCarryCorrectCategories() {
        LayoutResult layout = new Engraver().layout(
                minimalScore("Symphony No. 5", "I. Allegro con brio"),
                LayoutOptions.defaults());
        assertEquals(2, layout.texts().size());
        assertEquals(TextPlacement.Category.TITLE, layout.texts().get(0).category());
        assertEquals(TextPlacement.Category.SUBTITLE, layout.texts().get(1).category());
        }

        @Test
        void emitsComposerRightAndLyricistLeft() {
        Score score = minimalScoreWithCreators(
                new Creator("composer", "J. S. Bach"),
                new Creator("lyricist", "Anon."));
        LayoutOptions options = LayoutOptions.defaults();
        LayoutResult layout = new Engraver().layout(score, options);

        long creatorCount = layout.texts().stream()
                .filter(t -> t.category() == TextPlacement.Category.CREATOR)
                .count();
        assertEquals(2, creatorCount, "expected two CREATOR placements");

        TextPlacement composer = layout.texts().stream()
                .filter(t -> t.text().equals("J. S. Bach"))
                .findFirst().orElseThrow();
        assertEquals(TextPlacement.Category.CREATOR, composer.category());
        assertEquals(TextPlacement.Align.RIGHT, composer.align());
        assertEquals(options.systemWidth() - options.rightMargin(), composer.x(), 1e-6);

        TextPlacement lyricist = layout.texts().stream()
                .filter(t -> t.text().equals("Anon."))
                .findFirst().orElseThrow();
        assertEquals(TextPlacement.Category.CREATOR, lyricist.category());
        assertEquals(TextPlacement.Align.LEFT, lyricist.align());
        assertEquals(options.leftMargin(), lyricist.x(), 1e-6);

        // Composer and lyricist share the same visual row.
        assertEquals(composer.y(), lyricist.y(), 1e-6);
        }

        @Test
        void creatorRowsPushFirstStaffDownward() {
        LayoutOptions defaults = LayoutOptions.defaults();
        Score.Builder builder = baseScoreBuilder(null, null);
        Score withoutCreators = builder.build();

        Score.Builder builderWith = baseScoreBuilder(null, null);
        builderWith.addCreator(new Creator("composer", "Someone"));
        Score withCreators = builderWith.build();

        double yWithout = new Engraver().layout(withoutCreators, defaults).staves().get(0).y();
        double yWith = new Engraver().layout(withCreators, defaults).staves().get(0).y();
        assertTrue(yWith > yWithout,
                "creators must push the first staff down (with=" + yWith
                        + ", without=" + yWithout + ")");
        }

        @Test
        void multipleRightColumnCreatorsStackVertically() {
        Score score = minimalScoreWithCreators(
                new Creator("composer", "A"),
                new Creator("arranger", "B"));
        LayoutResult layout = new Engraver().layout(score, LayoutOptions.defaults());
        TextPlacement a = layout.texts().stream()
                .filter(t -> t.text().equals("A")).findFirst().orElseThrow();
        TextPlacement b = layout.texts().stream()
                .filter(t -> t.text().equals("B")).findFirst().orElseThrow();
        assertTrue(b.y() > a.y(), "second right-column creator must sit below the first");
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
