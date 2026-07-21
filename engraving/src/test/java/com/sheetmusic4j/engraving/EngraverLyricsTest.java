package com.sheetmusic4j.engraving;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.sheetmusic4j.core.model.Attributes;
import com.sheetmusic4j.core.model.Chord;
import com.sheetmusic4j.core.model.Clef;
import com.sheetmusic4j.core.model.Duration;
import com.sheetmusic4j.core.model.KeySignature;
import com.sheetmusic4j.core.model.Lyric;
import com.sheetmusic4j.core.model.Measure;
import com.sheetmusic4j.core.model.Note;
import com.sheetmusic4j.core.model.NoteType;
import com.sheetmusic4j.core.model.Part;
import com.sheetmusic4j.core.model.Pitch;
import com.sheetmusic4j.core.model.Score;
import com.sheetmusic4j.core.model.Step;
import com.sheetmusic4j.core.model.Syllabic;
import com.sheetmusic4j.core.model.TimeSignature;

class EngraverLyricsTest {

    private static final int DIVISIONS = 1;

    private static Measure.Builder emptyMeasureWithAttributes(int number) {
        return Measure.builder(number).attributes(Attributes.builder()
                .divisions(DIVISIONS)
                .keySignature(KeySignature.cMajor())
                .timeSignature(TimeSignature.fourFour())
                .clef(Clef.treble())
                .build());
    }

    private static Note quarterC(Lyric... lyrics) {
        Note.Builder b = Note.builder()
                .pitch(new Pitch(Step.C, 4))
                .duration(new Duration(1, DIVISIONS))
                .type(NoteType.QUARTER);
        for (Lyric l : lyrics) {
            b.addLyric(l);
        }
        return b.build();
    }

    private static Score scoreWithSingleLyricNote(Lyric lyric) {
        Measure measure = emptyMeasureWithAttributes(1)
                .addElement(quarterC(lyric))
                .build();
        Part part = Part.builder("P1").name("Voice").addMeasure(measure).build();
        return Score.builder().addPart(part).build();
    }

    private static List<TextPlacement> lyricTexts(LayoutResult layout) {
        return layout.texts().stream()
                .filter(t -> t.category() == MarkingCategory.LYRIC)
                .toList();
    }

    @Test
    void emitsLyricTextPlacementUnderNotehead() {
        Score score = scoreWithSingleLyricNote(new Lyric("La", Syllabic.SINGLE, 1));
        LayoutOptions options = LayoutOptions.defaults();
        LayoutResult layout = new Engraver().layout(score, options);
        List<TextPlacement> lyrics = lyricTexts(layout);
        assertEquals(1, lyrics.size());
        TextPlacement lyric = lyrics.get(0);
        assertEquals("La", lyric.text());
        assertEquals(TextPlacement.Align.CENTER, lyric.align());
        assertEquals(MarkingCategory.LYRIC, lyric.category());

        StaffLayout staff = layout.staves().get(0);
        // The lyric anchor x sits under a placed notehead in the same staff.
        double noteheadX = staff.glyphs().stream()
                .filter(g -> g.glyph() == Glyph.NOTEHEAD_BLACK)
                .mapToDouble(GlyphPlacement::x)
                .findFirst().orElseThrow();
        assertEquals(noteheadX, lyric.x(), 1e-6);

        // The lyric baseline sits below the last staff line.
        double staffBottom = staff.y() + options.staffHeight();
        assertTrue(lyric.y() > staffBottom,
                "lyric baseline (" + lyric.y() + ") must sit below staff bottom (" + staffBottom + ")");
    }

    @Test
    void syllabicSuffixAppendedForBeginAndMiddle() {
        assertEquals("Ma-", firstLyricText(new Lyric("Ma", Syllabic.BEGIN, 1)));
        assertEquals("gni-", firstLyricText(new Lyric("gni", Syllabic.MIDDLE, 1)));
        assertEquals("cat", firstLyricText(new Lyric("cat", Syllabic.END, 1)));
        assertEquals("La", firstLyricText(new Lyric("La", Syllabic.SINGLE, 1)));
    }

    private String firstLyricText(Lyric lyric) {
        LayoutResult layout = new Engraver().layout(scoreWithSingleLyricNote(lyric),
                LayoutOptions.defaults());
        return lyricTexts(layout).get(0).text();
    }

    @Test
    void lyricsPushNextPartDownward() {
        LayoutOptions options = LayoutOptions.defaults();

        // Two parts: part 1 sings; part 2 is instrumental.
        Score withLyrics = twoPartScore(true);
        Score withoutLyrics = twoPartScore(false);

        LayoutResult withLayout = new Engraver().layout(withLyrics, options);
        LayoutResult withoutLayout = new Engraver().layout(withoutLyrics, options);

        double secondStaffWith = withLayout.staves().get(1).y();
        double secondStaffWithout = withoutLayout.staves().get(1).y();
        assertTrue(secondStaffWith > secondStaffWithout,
                "second part must sit lower when the first part carries lyrics (with="
                        + secondStaffWith + ", without=" + secondStaffWithout + ")");
    }

    private static Score twoPartScore(boolean firstPartHasLyric) {
        Note first = firstPartHasLyric
                ? quarterC(new Lyric("La", Syllabic.SINGLE, 1))
                : quarterC();
        Measure m1 = emptyMeasureWithAttributes(1).addElement(first).build();
        Measure m2 = emptyMeasureWithAttributes(1).addElement(quarterC()).build();
        Part p1 = Part.builder("P1").name("Voice").addMeasure(m1).build();
        Part p2 = Part.builder("P2").name("Piano").addMeasure(m2).build();
        return Score.builder().addPart(p1).addPart(p2).build();
    }

    @Test
    void chordUsesPrimaryNoteLyric() {
        Note primary = Note.builder()
                .pitch(new Pitch(Step.C, 4))
                .duration(new Duration(1, DIVISIONS))
                .type(NoteType.QUARTER)
                .addLyric(new Lyric("La", Syllabic.SINGLE, 1))
                .build();
        Note secondary = Note.builder()
                .pitch(new Pitch(Step.E, 4))
                .duration(new Duration(1, DIVISIONS))
                .type(NoteType.QUARTER)
                .build();
        Chord chord = new Chord(List.of(primary, secondary));
        Measure measure = emptyMeasureWithAttributes(1).addElement(chord).build();
        Score score = Score.builder()
                .addPart(Part.builder("P1").name("Voice").addMeasure(measure).build())
                .build();

        LayoutResult layout = new Engraver().layout(score, LayoutOptions.defaults());
        List<TextPlacement> lyrics = lyricTexts(layout);
        assertEquals(1, lyrics.size());
        assertEquals("La", lyrics.get(0).text());
    }

    @Test
    void verseTwoIsNotRendered() {
        Score score = scoreWithSingleLyricNote(new Lyric("V2only", Syllabic.SINGLE, 2));
        LayoutResult layout = new Engraver().layout(score, LayoutOptions.defaults());
        assertTrue(lyricTexts(layout).isEmpty(),
                "verse >= 2 lyrics must not be rendered in the MVP (verse-1 only)");
    }

    @Test
    void firstVerseRenderedWhileSecondVerseIsSkipped() {
        Note note = Note.builder()
                .pitch(new Pitch(Step.C, 4))
                .duration(new Duration(1, DIVISIONS))
                .type(NoteType.QUARTER)
                .addLyric(new Lyric("La", Syllabic.SINGLE, 1))
                .addLyric(new Lyric("Le", Syllabic.SINGLE, 2))
                .build();
        Measure measure = emptyMeasureWithAttributes(1).addElement(note).build();
        Score score = Score.builder()
                .addPart(Part.builder("P1").addMeasure(measure).build())
                .build();
        LayoutResult layout = new Engraver().layout(score, LayoutOptions.defaults());
        List<TextPlacement> lyrics = lyricTexts(layout);
        assertEquals(1, lyrics.size());
        assertEquals("La", lyrics.get(0).text());
    }

    @Test
    void noLyricReservationForPurelyInstrumentalPart() {
        LayoutOptions options = LayoutOptions.defaults();

        Score instrumental = Score.builder()
                .addPart(Part.builder("P1")
                        .addMeasure(emptyMeasureWithAttributes(1).addElement(quarterC()).build())
                        .build())
                .build();
        LayoutResult layout = new Engraver().layout(instrumental, options);
        assertTrue(lyricTexts(layout).isEmpty());
    }
}
