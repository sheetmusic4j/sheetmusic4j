package com.sheetmusic4j.core.abc;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.sheetmusic4j.core.model.Accidental;
import com.sheetmusic4j.core.model.Beam;
import com.sheetmusic4j.core.model.Chord;
import com.sheetmusic4j.core.model.KeySignature;
import com.sheetmusic4j.core.model.Measure;
import com.sheetmusic4j.core.model.MusicElement;
import com.sheetmusic4j.core.model.Note;
import com.sheetmusic4j.core.model.NoteType;
import com.sheetmusic4j.core.model.Part;
import com.sheetmusic4j.core.model.Score;
import com.sheetmusic4j.core.model.Slur;
import com.sheetmusic4j.core.model.Step;
import com.sheetmusic4j.core.model.TimeSignature;
import com.sheetmusic4j.core.model.Tuplet;

class AbcReaderTest {

    private static Score read(String abc) {
        return new AbcReader().read(new ByteArrayInputStream(abc.getBytes(StandardCharsets.UTF_8)));
    }

    private static List<Note> notesOf(Part part) {
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
    void readsHeaderFields() {
        String abc = "X:1\nT:Test Tune\nC:Alice\nM:3/4\nL:1/8\nK:G\nGABc|\n";
        Score score = read(abc);
        assertEquals("Test Tune", score.workTitle().orElse(null));
        assertFalse(score.creators().isEmpty());
        assertEquals("Alice", score.creators().get(0).name());

        Part part = score.parts().get(0);
        Measure first = part.measures().get(0);
        assertTrue(first.attributes().isPresent());
        assertEquals(new TimeSignature(3, 4), first.attributes().get().timeSignature().orElseThrow());
        assertEquals(new KeySignature(1), first.attributes().get().keySignature().orElseThrow());
    }

    @Test
    void readsCMajorScaleSample() throws Exception {
        String abc;
        try (InputStream in = getClass().getResourceAsStream("/abc/c-major-scale.abc")) {
            assertNotNull(in);
            abc = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        Score score = read(abc);
        assertEquals("C Major Scale", score.workTitle().orElse(null));
        Part part = score.parts().get(0);
        assertEquals(2, part.measures().size());

        List<Note> notes = notesOf(part);
        assertEquals(8, notes.size());
        Step[] expected = {Step.C, Step.D, Step.E, Step.F, Step.G, Step.A, Step.B, Step.C};
        int[] octaves = {4, 4, 4, 4, 4, 4, 4, 5};
        for (int i = 0; i < notes.size(); i++) {
            assertEquals(expected[i], notes.get(i).pitch().step(), "step at " + i);
            assertEquals(octaves[i], notes.get(i).pitch().octave(), "octave at " + i);
        }
    }

    @Test
    void readsAccidentalsAndOctaves() {
        String abc = "X:1\nT:Test\nM:4/4\nL:1/8\nK:C\n^F _B c' C,|\n";
        List<Note> notes = notesOf(read(abc).parts().get(0));
        assertEquals(Step.F, notes.get(0).pitch().step());
        assertEquals(1, notes.get(0).pitch().alter());
        assertEquals(4, notes.get(0).pitch().octave());
        assertTrue(notes.get(0).displayedAccidental().isPresent());
        assertEquals(Accidental.SHARP, notes.get(0).displayedAccidental().get());

        assertEquals(Step.B, notes.get(1).pitch().step());
        assertEquals(-1, notes.get(1).pitch().alter());
        assertEquals(4, notes.get(1).pitch().octave());

        assertEquals(Step.C, notes.get(2).pitch().step());
        assertEquals(6, notes.get(2).pitch().octave());

        assertEquals(Step.C, notes.get(3).pitch().step());
        assertEquals(3, notes.get(3).pitch().octave());
    }

    @Test
    void readsBrokenRhythm() {
        String abc = "X:1\nT:Test\nM:4/4\nL:1/8\nK:C\nA>B|\n";
        List<Note> notes = notesOf(read(abc).parts().get(0));
        assertEquals(2, notes.size());
        // Under L:1/8 (divisions=96): eighth = 48. Dotted eighth = 72. Sixteenth = 24.
        assertEquals(72, notes.get(0).duration().value());
        assertEquals(24, notes.get(1).duration().value());
    }

    @Test
    void brokenRhythmNotesGetDottedTypeAndBeam() {
        // A dotted eighth followed by a sixteenth: both should render with
        // their proper written type/dots (not just the right raw duration)
        // and should be beamed together as a pair.
        String abc = "X:1\nT:Test\nM:4/4\nL:1/8\nK:C\nA>B|\n";
        List<Note> notes = notesOf(read(abc).parts().get(0));
        assertEquals(2, notes.size());

        Note dottedEighth = notes.get(0);
        assertEquals(NoteType.EIGHTH, dottedEighth.type());
        assertEquals(1, dottedEighth.dots());
        assertEquals(List.of(new Beam(1, Beam.State.BEGIN)), dottedEighth.beams());

        Note sixteenth = notes.get(1);
        assertEquals(NoteType.SIXTEENTH, sixteenth.type());
        assertEquals(0, sixteenth.dots());
        assertEquals(
                List.of(new Beam(1, Beam.State.END), new Beam(2, Beam.State.BACKWARD_HOOK)),
                sixteenth.beams());
    }

    @Test
    void plainEighthNotesAreBeamedWhenAdjacent() {
        String abc = "X:1\nT:Test\nM:4/4\nL:1/8\nK:C\nABC D|\n";
        List<Note> notes = notesOf(read(abc).parts().get(0));
        assertEquals(4, notes.size());
        assertEquals(new Beam(1, Beam.State.BEGIN), notes.get(0).beams().get(0));
        assertEquals(new Beam(1, Beam.State.CONTINUE), notes.get(1).beams().get(0));
        assertEquals(new Beam(1, Beam.State.END), notes.get(2).beams().get(0));
        // Separated by a space from the first three, so it stands alone (unbeamed).
        assertTrue(notes.get(3).beams().isEmpty());
    }

    @Test
    void restBreaksBeamGroup() {
        String abc = "X:1\nT:Test\nM:4/4\nL:1/8\nK:C\nABzCD|\n";
        List<Note> notes = notesOf(read(abc).parts().get(0));
        assertEquals(4, notes.size());
        // A, B beamed together; the rest between B and C breaks the group so
        // C, D form their own separate pair rather than one run of four.
        assertEquals(new Beam(1, Beam.State.BEGIN), notes.get(0).beams().get(0));
        assertEquals(new Beam(1, Beam.State.END), notes.get(1).beams().get(0));
        assertEquals(new Beam(1, Beam.State.BEGIN), notes.get(2).beams().get(0));
        assertEquals(new Beam(1, Beam.State.END), notes.get(3).beams().get(0));
    }

    @Test
    void singleTuneTitleIsNotAlsoUsedAsPartName() {
        String abc = "X:1\nT:Broken rhythm markers\nM:3/4\nK:C\nA B C|\n";
        Score score = read(abc);
        assertEquals("Broken rhythm markers", score.workTitle().orElse(null));
        // The title already renders once as the score heading; reusing it as
        // the part's name would draw it a second time as a staff label.
        assertNull(score.parts().get(0).name());
    }

    @Test
    void secondTuneInMultiTuneFileKeepsItsTitleAsPartLabel() {
        String abc = "X:1\nT:First\nM:4/4\nK:C\nA B C D|\n\nX:2\nT:Second\nM:4/4\nK:C\nE F G A|\n";
        Score score = read(abc);
        assertEquals(2, score.parts().size());
        assertEquals("First", score.workTitle().orElse(null));
        assertNull(score.parts().get(0).name());
        assertEquals("Second", score.parts().get(1).name());
    }

    @Test
    void readsTiesAndSlurs() {
        String abc = "X:1\nT:Test\nM:4/4\nL:1/4\nK:C\nA-A (AB)|\n";
        List<Note> notes = notesOf(read(abc).parts().get(0));
        assertEquals(4, notes.size());
        assertTrue(notes.get(0).tieStart());
        assertTrue(notes.get(1).tieStop());
        // Slurs on notes 2 and 3.
        boolean hasSlurStart = notes.get(2).slurs().stream().anyMatch(s -> s.type() == Slur.Type.START);
        boolean hasSlurStop = notes.get(3).slurs().stream().anyMatch(s -> s.type() == Slur.Type.STOP);
        assertTrue(hasSlurStart, "expected slur start on note 2");
        assertTrue(hasSlurStop, "expected slur stop on note 3");
    }

    @Test
    void readsChordAndTuplet() {
        String abc = "X:1\nT:Test\nM:4/4\nL:1/4\nK:C\n[CEG] (3ABc|\n";
        Part part = read(abc).parts().get(0);
        List<MusicElement> els = part.measures().get(0).elements();
        assertTrue(els.get(0) instanceof Chord);
        Chord chord = (Chord) els.get(0);
        assertEquals(3, chord.notes().size());

        // The last three elements should be tuplet notes with time modification 3:2.
        List<Note> notes = notesOf(part);
        // notes: 3 chord notes + 3 tuplet notes = 6
        assertEquals(6, notes.size());
        Note tupletNote = notes.get(3);
        assertTrue(tupletNote.timeModification().isPresent());
        assertEquals(3, tupletNote.timeModification().get().actualNotes());
        assertEquals(2, tupletNote.timeModification().get().normalNotes());
        // Tuplet start marker on first tuplet note.
        boolean hasStart = tupletNote.tuplets().stream().anyMatch(t -> t.type() == Tuplet.Type.START);
        assertTrue(hasStart);
        // Tuplet stop on the third.
        boolean hasStop = notes.get(5).tuplets().stream().anyMatch(t -> t.type() == Tuplet.Type.STOP);
        assertTrue(hasStop);
    }

    @Test
    void readsMinorKey() {
        String abc = "X:1\nT:Test\nM:4/4\nL:1/4\nK:Am\nABcd|\n";
        Score score = read(abc);
        KeySignature k = score.parts().get(0).measures().get(0)
                .attributes().orElseThrow()
                .keySignature().orElseThrow();
        assertEquals(0, k.fifths());
    }

    @Test
    void readsLyricsLine() {
        String abc = "X:1\nT:Test\nM:4/4\nL:1/4\nK:C\nCDEF|\nw:do re mi fa\n";
        List<Note> notes = notesOf(read(abc).parts().get(0));
        assertEquals("do", notes.get(0).lyrics().get(0).text());
        assertEquals("re", notes.get(1).lyrics().get(0).text());
        assertEquals("mi", notes.get(2).lyrics().get(0).text());
        assertEquals("fa", notes.get(3).lyrics().get(0).text());
    }

    @Test
    void ignoresUnsupportedDecorations() {
        String abc = "X:1\nT:Test\nM:4/4\nL:1/4\nK:C\n!f!C !mp!D E F|\n";
        List<Note> notes = notesOf(read(abc).parts().get(0));
        assertEquals(4, notes.size());
        assertEquals(Step.C, notes.get(0).pitch().step());
    }

    @Test
    void appliesKeySignatureToUnaccidentedNotes() {
        // In G major, F should be F#.
        String abc = "X:1\nT:Test\nM:4/4\nL:1/4\nK:G\nFGAB|\n";
        List<Note> notes = notesOf(read(abc).parts().get(0));
        assertEquals(1, notes.get(0).pitch().alter());
    }

    @Test
    void collectsPostTuneText() {
        String abc = "X:1\nT:Test\nM:4/4\nL:1/4\nK:C\nCDEF|\nW:Verse one line one\nW:Verse one line two\n";
        Part part = read(abc).parts().get(0);
        assertEquals(2, part.postTuneText().size());
        assertEquals("Verse one line one", part.postTuneText().get(0));
        assertEquals("Verse one line two", part.postTuneText().get(1));
    }

    @Test
    void measureAccidentalsCarryWithinMeasure() {
        // Within a measure, ^F F should both be F#. After a bar, the second
        // measure resets and F falls back to key alteration (C major = natural).
        String abc = "X:1\nT:Test\nM:4/4\nL:1/4\nK:C\n^FFFF|FGAB|\n";
        List<Note> notes = notesOf(read(abc).parts().get(0));
        assertEquals(1, notes.get(0).pitch().alter());
        assertEquals(1, notes.get(1).pitch().alter());
        assertEquals(1, notes.get(2).pitch().alter());
        assertEquals(1, notes.get(3).pitch().alter());
        assertEquals(0, notes.get(4).pitch().alter(), "bar resets accidentals");
    }
}
