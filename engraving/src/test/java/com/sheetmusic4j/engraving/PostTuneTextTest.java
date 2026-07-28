package com.sheetmusic4j.engraving;

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
import com.sheetmusic4j.engraving.glyph.MarkingCategory;
import com.sheetmusic4j.engraving.layout.LayoutOptions;
import com.sheetmusic4j.engraving.layout.LayoutResult;
import com.sheetmusic4j.engraving.layout.StaffLayout;
import com.sheetmusic4j.engraving.placement.TextPlacement;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that ABC {@code W:} "words after tune" lines carried on a
 * {@link Part} are turned into {@link TextPlacement} entries that render
 * below the last system.
 */
class PostTuneTextTest {

    private static Score scoreWith(List<String> postTuneText) {
        int divisions = 1;
        Measure measure = Measure.builder(1)
                .attributes(Attributes.builder()
                        .divisions(divisions)
                        .keySignature(KeySignature.cMajor())
                        .timeSignature(TimeSignature.fourFour())
                        .clef(Clef.treble())
                        .build())
                .addElement(Note.builder()
                        .pitch(new Pitch(Step.C, 4))
                        .duration(new Duration(1, divisions))
                        .type(NoteType.QUARTER)
                        .build())
                .build();
        Part.Builder pb = Part.builder("P1").name("Voice").addMeasure(measure);
        for (String line : postTuneText) {
            pb.addPostTuneText(line);
        }
        return Score.builder().addPart(pb.build()).build();
    }

    private static List<TextPlacement> postTuneTexts(LayoutResult layout, List<String> expectedLines) {
        // Filter by literal text since post-tune text shares the LYRIC
        // category with per-note lyrics. The scoreWith() helper carries no
        // per-note lyrics, so any LYRIC placement whose text matches one of
        // the expected lines is a post-tune entry.
        return layout.texts().stream()
                .filter(t -> t.category() == MarkingCategory.LYRIC)
                .filter(t -> expectedLines.contains(t.text()))
                .toList();
    }

    @Test
    void emitsOneTextPlacementPerPostTuneLine() {
        List<String> lines = List.of(
                "Hey, the dusty miller, and his dusty coat;",
                "He will win a shilling, or he spend a groat.");
        Score score = scoreWith(lines);

        LayoutOptions options = LayoutOptions.defaults();
        LayoutResult layout = new Engraver().layout(score, options);

        List<TextPlacement> emitted = postTuneTexts(layout, lines);
        assertEquals(2, emitted.size(),
                "expected one TextPlacement per W: line, got " + layout.texts());
        assertEquals(lines.get(0), emitted.get(0).text());
        assertEquals(lines.get(1), emitted.get(1).text());
    }

    @Test
    void postTuneTextLinesSitBelowLastStaff() {
        List<String> lines = List.of("post-tune verse one", "post-tune verse two");
        Score score = scoreWith(lines);
        LayoutOptions options = LayoutOptions.defaults();
        LayoutResult layout = new Engraver().layout(score, options);

        StaffLayout lastStaff = layout.staves().get(layout.staves().size() - 1);
        double staffBottom = lastStaff.y() + options.staffHeight();

        List<TextPlacement> emitted = postTuneTexts(layout, lines);
        for (TextPlacement t : emitted) {
            assertTrue(t.y() > staffBottom,
                    "post-tune text baseline (" + t.y()
                            + ") must sit below the last staff bottom (" + staffBottom + ")");
        }
        // Lines are emitted top-to-bottom in the order they were added.
        assertTrue(emitted.get(1).y() > emitted.get(0).y(),
                "second post-tune line must sit below the first (got "
                        + emitted.get(0).y() + " vs " + emitted.get(1).y() + ")");
    }

    @Test
    void layoutHeightGrowsToCoverPostTuneText() {
        Score without = scoreWith(List.of());
        Score with = scoreWith(List.of("line one", "line two", "line three"));
        LayoutOptions options = LayoutOptions.defaults();

        LayoutResult layoutWithout = new Engraver().layout(without, options);
        LayoutResult layoutWith = new Engraver().layout(with, options);

        assertTrue(layoutWith.height() > layoutWithout.height(),
                "layout height must grow when post-tune text is present (with="
                        + layoutWith.height() + ", without=" + layoutWithout.height() + ")");
    }
}
