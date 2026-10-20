package com.sheetmusic4j.core.lilypond;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sheetmusic4j.core.model.Accidental;
import com.sheetmusic4j.core.model.Barline;
import com.sheetmusic4j.core.model.Chord;
import com.sheetmusic4j.core.model.ClefSign;
import com.sheetmusic4j.core.model.Direction;
import com.sheetmusic4j.core.model.DirectionType;
import com.sheetmusic4j.core.model.Fraction;
import com.sheetmusic4j.core.model.GroupSymbol;
import com.sheetmusic4j.core.model.Measure;
import com.sheetmusic4j.core.model.MeasureTiming;
import com.sheetmusic4j.core.model.MusicElement;
import com.sheetmusic4j.core.model.Note;
import com.sheetmusic4j.core.model.Part;
import com.sheetmusic4j.core.model.Rest;
import com.sheetmusic4j.core.model.Score;
import com.sheetmusic4j.core.model.Step;

/**
 * Musical semantics of the LilyPond reader: time, staves, voices and the
 * notation LilyPond leaves implicit.
 */
class LilyPondInterpretationTest {

    private static Score read(String ly) {
        return new LilyPondReader().read(new ByteArrayInputStream(ly.getBytes(StandardCharsets.UTF_8)));
    }

    private static List<Note> notes(Part part) {
        List<Note> out = new ArrayList<>();
        for (Measure m : part.measures()) {
            for (MusicElement el : m.elements()) {
                if (el instanceof Note n) {
                    out.add(n);
                } else if (el instanceof Chord c) {
                    out.addAll(c.notes());
                }
            }
        }
        return out;
    }

    @Test
    void keySignatureDoesNotChangeWrittenPitches() {
        // In LilyPond `b` is B natural whatever the key; the key only decides what is printed.
        List<Note> notes = notes(read("{ \\key f \\major b'4 bes'4 b'4 c''4 }").parts().get(0));
        assertEquals(0, notes.get(0).pitch().alter());
        assertEquals(Accidental.NATURAL, notes.get(0).displayedAccidental().orElse(null));
        assertEquals(-1, notes.get(1).pitch().alter());
        assertEquals(Accidental.FLAT, notes.get(1).displayedAccidental().orElse(null));
        assertEquals(Accidental.NATURAL, notes.get(2).displayedAccidental().orElse(null));
        assertTrue(notes.get(3).displayedAccidental().isEmpty());
    }

    @Test
    void measuresFollowTheTimeSignatureWithoutBarChecks() {
        Part part = read("{ \\time 3/4 c'4 d' e' f' g' a' }").parts().get(0);
        assertEquals(2, part.measures().size());
        assertEquals(Fraction.of(3), MeasureTiming.of(part.measures().get(0)).duration());
    }

    @Test
    void partialMakesAPickupMeasure() {
        Part part = read("{ \\time 4/4 \\partial 4 g'4 | c''1 }").parts().get(0);
        assertEquals(2, part.measures().size());
        assertEquals(0, part.measures().get(0).number());
        assertEquals(Fraction.of(1), MeasureTiming.of(part.measures().get(0)).duration());
    }

    @Test
    void notesAcrossBarLinesAreSplitAndTied() {
        List<Note> notes = notes(read("{ \\time 2/4 c'2. d'4 }").parts().get(0));
        assertEquals(3, notes.size());
        assertTrue(notes.get(0).tieStart());
        assertTrue(notes.get(1).tieStop());
        assertEquals(Step.C, notes.get(1).pitch().step());
        assertEquals(Fraction.of(1), notes.get(1).duration().quarters());
    }

    @Test
    void spacersBecomeInvisibleRests() {
        Measure m = read("{ s2 c'2 }").parts().get(0).measures().get(0);
        Rest rest = (Rest) m.elements().get(0);
        assertFalse(rest.visible());
    }

    @Test
    void pianoStaffIsOnePartWithTwoStaves() {
        Score score = read("\\new PianoStaff << \\new Staff { c''1 } \\new Staff { \\clef bass c1 } >>");
        assertEquals(1, score.parts().size());
        Measure first = score.parts().get(0).measures().get(0);
        assertEquals(2, first.attributes().orElseThrow().staves().orElseThrow());
        assertEquals(ClefSign.F, first.attributes().orElseThrow().clefs().get(1).sign());
        assertEquals(2, notes(score.parts().get(0)).get(1).staff());
    }

    @Test
    void staffGroupMakesBracketedParts() {
        Score score = read("\\new StaffGroup << "
                + "\\new Staff \\with { instrumentName = \"Violin\" } { c''1 } "
                + "\\new Staff { \\set Staff.instrumentName = \"Viola\" \\clef alto c'1 } >>");
        assertEquals(2, score.parts().size());
        assertEquals("Violin", score.parts().get(0).name());
        assertEquals("Viola", score.parts().get(1).name());
        assertEquals(1, score.partGroups().size());
        assertEquals(GroupSymbol.BRACKET, score.partGroups().get(0).symbol());
    }

    @Test
    void voiceSeparatorMakesTwoVoicesWithOpposedStems() {
        List<Note> notes = notes(read("{ << { c''4 d'' } \\\\ { a'4 b' } >> }").parts().get(0));
        Note upper = notes.stream().filter(n -> n.pitch().step() == Step.C).findFirst().orElseThrow();
        Note lower = notes.stream().filter(n -> n.pitch().step() == Step.A).findFirst().orElseThrow();
        assertTrue(upper.voice() != lower.voice());
        assertEquals(Boolean.TRUE, upper.stemUp().orElse(null));
        assertEquals(Boolean.FALSE, lower.stemUp().orElse(null));
    }

    @Test
    void changeStaffMovesNotesToTheOtherStaff() {
        Score score = read("\\new PianoStaff << "
                + "\\new Staff = \"up\" { c''4 \\change Staff = \"down\" c4 c4 c4 } "
                + "\\new Staff = \"down\" { \\clef bass s1 } >>");
        List<Note> notes = notes(score.parts().get(0));
        assertEquals(1, notes.get(0).staff());
        assertTrue(notes.stream().filter(n -> n.pitch().octave() == 3).allMatch(n -> n.staff() == 2));
    }

    @Test
    void readsGermanNoteNames() {
        List<Note> notes = notes(read("\\language \"deutsch\" { h'4 b'4 es'4 }").parts().get(0));
        assertEquals(Step.B, notes.get(0).pitch().step());
        assertEquals(0, notes.get(0).pitch().alter());
        assertEquals(-1, notes.get(1).pitch().alter());
        assertEquals(Step.E, notes.get(2).pitch().step());
        assertEquals(-1, notes.get(2).pitch().alter());
    }

    @Test
    void readsEnglishNoteNamesFromInclude() {
        List<Note> notes = notes(read("\\include \"english.ly\" { fs'4 bf'4 }").parts().get(0));
        assertEquals(1, notes.get(0).pitch().alter());
        assertEquals(-1, notes.get(1).pitch().alter());
    }

    @Test
    void repeatWithAlternativesSetsBarLinesAndEndings() {
        Part part = read("{ c'1 \\repeat volta 2 { d'1 } \\alternative { { e'1 } { f'1 } } g'1 }").parts().get(0);
        assertEquals(5, part.measures().size());
        assertTrue(part.measures().get(1).leadingRepeatStart());
        assertEquals("1.", part.measures().get(2).ending().orElse(null));
        assertEquals(Barline.Repeat.BACKWARD, part.measures().get(2).barline().orElseThrow().repeat());
        assertEquals("2.", part.measures().get(3).ending().orElse(null));
    }

    @Test
    void lyricsSkipNotesInsideASlur() {
        List<Note> notes = notes(read("{ c'4( d') e'2 } \\addlyrics { la -- lo }").parts().get(0));
        assertEquals("la", notes.get(0).lyrics().get(0).text());
        assertTrue(notes.get(1).lyrics().isEmpty());
        assertEquals("lo", notes.get(2).lyrics().get(0).text());
    }

    @Test
    void lyricsToFindsTheNamedVoice() {
        Score score = read("<< \\new Staff { \\new Voice = \"v\" { c'4 d' e' f' } } "
                + "\\new Lyrics \\lyricsto \"v\" { one two } >>");
        List<Note> notes = notes(score.parts().get(0));
        assertEquals("one", notes.get(0).lyrics().get(0).text());
        assertEquals("two", notes.get(1).lyrics().get(0).text());
    }

    @Test
    void skipsArgumentsOfSchemeDefinedFunctions() {
        String ly = "moveDyn = #(define-event-function (x y event) (number? number? ly:event?) #{ #event #})\n"
                + "{ c'4 \\moveDyn -1.5 2 ^\\ff d'4 e'2 }";
        Part part = read(ly).parts().get(0);
        assertEquals(3, notes(part).size());
        assertEquals(1, part.measures().size());
        assertTrue(part.measures().get(0).elements().stream().anyMatch(e -> e instanceof Direction d
                && d.type() instanceof DirectionType.Dynamic));
    }

    @Test
    void durationMultipliersMayBeSpacedOut() {
        List<Note> notes = notes(read("{ c'4 * 1/2 d'8 }").parts().get(0));
        assertEquals(2, notes.size());
        assertEquals(Fraction.of(1, 2), notes.get(0).duration().quarters());
    }

    @Test
    void transposeShiftsPitches() {
        List<Note> notes = notes(read("\\transpose c d { c'4 fis'4 }").parts().get(0));
        assertEquals(Step.D, notes.get(0).pitch().step());
        assertEquals(Step.G, notes.get(1).pitch().step());
        assertEquals(1, notes.get(1).pitch().alter());
    }

    @Test
    void octaveClefsKeepTheirTransposition() {
        Measure m = read("{ \\clef \"treble_8\" e4 }").parts().get(0).measures().get(0);
        assertEquals(-1, m.attributes().orElseThrow().clefs().get(0).octaveChange());
    }

    @Test
    void chordNamesGoToTheStaffBelow() {
        Score score = read("<< \\new ChordNames \\chordmode { c1 g1 } \\new Staff { c'1 d'1 } >>");
        long harmonies = score.parts().get(0).measures().stream()
                .flatMap(m -> m.elements().stream())
                .filter(e -> e instanceof com.sheetmusic4j.core.model.Harmony)
                .count();
        assertEquals(2, harmonies);
    }

    @Test
    void stemDirectionOverrideOnTheStaffAppliesToItsNotes() {
        List<Note> notes = notes(read("\\new Staff \\with { \\override Stem.direction = #down } { c'4 d'4 }")
                .parts().get(0));
        assertEquals(Boolean.FALSE, notes.get(0).stemUp().orElse(null));
        List<Note> up = notes(read("{ \\override Stem.direction = #UP c''4 \\revert Stem.direction d''4 }")
                .parts().get(0));
        assertEquals(Boolean.TRUE, up.get(0).stemUp().orElse(null));
        assertTrue(up.get(1).stemUp().isEmpty());
    }

    @Test
    void nestedTupletsKeepTheirOwnCounts() {
        List<Note> notes = notes(read("{ \\tuplet 3/2 { \\tuplet 5/4 { c'16 d' e' f' g' } a'8 b'8 } r4 }")
                .parts().get(0));
        Note first = notes.get(0);
        assertEquals(15, first.timeModification().orElseThrow().actualNotes());
        List<Integer> counts = first.tuplets().stream()
                .filter(t -> t.type() == com.sheetmusic4j.core.model.Tuplet.Type.START)
                .map(com.sheetmusic4j.core.model.Tuplet::actualNotes)
                .sorted()
                .toList();
        assertEquals(List.of(3, 5), counts);
    }

    @Test
    void hiddenTupletNumbersDropTheIndicator() {
        List<Note> notes = notes(read("{ \\hide TupletNumber \\tuplet 3/2 { c'8 d' e' } r4 }").parts().get(0));
        assertTrue(notes.get(0).tuplets().isEmpty());
        assertEquals(3, notes.get(0).timeModification().orElseThrow().actualNotes());
    }

    @Test
    void partCombineMergesUnisonsAndKeepsDifferencesApart() {
        Part part = read("\\partCombine { c''4 e''4 r2 } { c''4 c''4 r2 }").parts().get(0);
        List<MusicElement> elements = part.measures().get(0).elements();
        // c'' in both parts: one note; e'' over c'': one chord; the shared rest: one rest.
        assertEquals(1, elements.stream().filter(e -> e instanceof Note).count());
        assertEquals(1, elements.stream().filter(e -> e instanceof Chord).count());
        assertEquals(1, elements.stream().filter(e -> e instanceof Rest r && r.visible()).count());
    }

    @Test
    void tempoHideNoteKeepsOnlyTheText() {
        Measure m = read("{ \\set Score.tempoHideNote = ##t \\tempo \"Allegro\" 4 = 120 c'1 }")
                .parts().get(0).measures().get(0);
        assertTrue(m.elements().stream().anyMatch(e -> e instanceof Direction d
                && d.type() instanceof DirectionType.Words));
        assertFalse(m.elements().stream().anyMatch(e -> e instanceof Direction d
                && d.type() instanceof DirectionType.Metronome));
    }

    @Test
    void includesFilesNextToTheScore(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("notes.ily"), "melody = { c'4 d' e' f' }\n");
        Path main = dir.resolve("main.ly");
        Files.writeString(main, "\\include \"notes.ily\"\n\\score { \\new Staff \\melody }\n");
        assertEquals(4, notes(new LilyPondReader().read(main).parts().get(0)).size());
    }

    @Test
    void ignoresIncludesOutsideTheScoreDirectory(@TempDir Path dir) throws Exception {
        Path sub = Files.createDirectory(dir.resolve("score"));
        Files.writeString(dir.resolve("secret.ily"), "melody = { c'4 d' e' f' }\n");
        Path main = sub.resolve("main.ly");
        Files.writeString(main, "\\include \"../secret.ily\"\nmelody = { g'1 }\n\\score { \\melody }\n");
        assertEquals(1, notes(new LilyPondReader().read(main).parts().get(0)).size());
    }
}
