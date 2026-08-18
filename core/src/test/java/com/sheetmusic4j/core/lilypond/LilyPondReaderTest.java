package com.sheetmusic4j.core.lilypond;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.sheetmusic4j.core.model.Chord;
import com.sheetmusic4j.core.model.KeySignature;
import com.sheetmusic4j.core.model.Measure;
import com.sheetmusic4j.core.model.MusicElement;
import com.sheetmusic4j.core.model.Note;
import com.sheetmusic4j.core.model.Part;
import com.sheetmusic4j.core.model.Score;
import com.sheetmusic4j.core.model.Slur;
import com.sheetmusic4j.core.model.Step;
import com.sheetmusic4j.core.model.TimeSignature;
import com.sheetmusic4j.core.model.Tuplet;

class LilyPondReaderTest {

    private static Score read(String ly) {
        return new LilyPondReader().read(new ByteArrayInputStream(ly.getBytes(StandardCharsets.UTF_8)));
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
        String ly = "\\version \"2.24.0\"\n"
                + "\\header {\n"
                + "    title = \"Test Tune\"\n"
                + "    composer = \"Alice\"\n"
                + "}\n"
                + "\\score { \\new Staff { \\time 3/4 \\key g \\major \\clef treble\n"
                + "  g'4 a'4 b'4 |\n"
                + "} }\n";
        Score score = read(ly);
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
        String ly;
        try (InputStream in = getClass().getResourceAsStream("/lilypond/c-major-scale.ly")) {
            assertNotNull(in);
            ly = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        Score score = read(ly);
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
    void readsAbsolutePitchesAndOctaves() {
        String ly = "\\score { \\new Staff { \\time 4/4 \\key c \\major \\clef treble\n"
                + "  c4 c'4 c''4 c,4 |\n"
                + "} }\n";
        List<Note> notes = notesOf(read(ly).parts().get(0));
        assertEquals(4, notes.size());
        assertEquals(3, notes.get(0).pitch().octave());
        assertEquals(4, notes.get(1).pitch().octave());
        assertEquals(5, notes.get(2).pitch().octave());
        assertEquals(2, notes.get(3).pitch().octave());
    }

    @Test
    void readsRelativeMode() {
        String ly = "\\score { \\new Staff { \\time 4/4 \\key c \\major \\clef treble\n"
                + "  \\relative c' { c4 d4 e4 f4 | g4 a4 b4 c4 }\n"
                + "} }\n";
        List<Note> notes = notesOf(read(ly).parts().get(0));
        assertEquals(8, notes.size());
        // c' starts at C4; each next step within a fourth stays close.
        assertEquals(4, notes.get(0).pitch().octave());
        assertEquals(Step.C, notes.get(0).pitch().step());
        assertEquals(Step.D, notes.get(1).pitch().step());
        assertEquals(Step.E, notes.get(2).pitch().step());
        // Should stay in octave 4 until wrap.
        assertEquals(4, notes.get(3).pitch().octave());
        assertEquals(4, notes.get(4).pitch().octave());
        // Last c wraps up to C5 (closest to previous B4).
        assertEquals(5, notes.get(7).pitch().octave());
    }

    @Test
    void readsAccidentals() {
        String ly = "\\score { \\new Staff { \\time 4/4 \\key c \\major \\clef treble\n"
                + "  fis'4 bes4 c''4 c,4 |\n"
                + "} }\n";
        List<Note> notes = notesOf(read(ly).parts().get(0));
        assertEquals(Step.F, notes.get(0).pitch().step());
        assertEquals(1, notes.get(0).pitch().alter());
        assertEquals(Step.B, notes.get(1).pitch().step());
        assertEquals(-1, notes.get(1).pitch().alter());
    }

    @Test
    void readsDottedDurationsAndCarry() {
        String ly = "\\score { \\new Staff { \\time 4/4 \\key c \\major \\clef treble\n"
                + "  c'8. d'16 e'4 f' | g'2 a'2 |\n"
                + "} }\n";
        List<Note> notes = notesOf(read(ly).parts().get(0));
        // Note 0: dotted eighth
        assertEquals(com.sheetmusic4j.core.model.NoteType.EIGHTH, notes.get(0).type());
        assertEquals(1, notes.get(0).dots());
        // Note 1: sixteenth
        assertEquals(com.sheetmusic4j.core.model.NoteType.SIXTEENTH, notes.get(1).type());
        // Note 2: quarter (explicit)
        assertEquals(com.sheetmusic4j.core.model.NoteType.QUARTER, notes.get(2).type());
        // Note 3: carry — should still be quarter
        assertEquals(com.sheetmusic4j.core.model.NoteType.QUARTER, notes.get(3).type());
    }

    @Test
    void readsChord() {
        String ly = "\\score { \\new Staff { \\time 4/4 \\key c \\major \\clef treble\n"
                + "  <c' e' g'>4 c'4 c'4 c'4 |\n"
                + "} }\n";
        Part part = read(ly).parts().get(0);
        List<MusicElement> els = part.measures().get(0).elements();
        assertTrue(els.get(0) instanceof Chord);
        Chord chord = (Chord) els.get(0);
        assertEquals(3, chord.notes().size());
    }

    @Test
    void readsTiesAndSlurs() {
        String ly = "\\score { \\new Staff { \\time 4/4 \\key c \\major \\clef treble\n"
                + "  c'4~ c'4 d'4( e'4) |\n"
                + "} }\n";
        List<Note> notes = notesOf(read(ly).parts().get(0));
        assertTrue(notes.get(0).tieStart());
        assertTrue(notes.get(1).tieStop());
        boolean hasSlurStart = notes.get(2).slurs().stream().anyMatch(s -> s.type() == Slur.Type.START);
        boolean hasSlurStop = notes.get(3).slurs().stream().anyMatch(s -> s.type() == Slur.Type.STOP);
        assertTrue(hasSlurStart);
        assertTrue(hasSlurStop);
    }

    @Test
    void readsTuplet() {
        String ly = "\\score { \\new Staff { \\time 4/4 \\key c \\major \\clef treble\n"
                + "  \\tuplet 3/2 { c'8 d'8 e'8 } f'4 g'4 |\n"
                + "} }\n";
        List<Note> notes = notesOf(read(ly).parts().get(0));
        assertEquals(5, notes.size());
        Note first = notes.get(0);
        assertTrue(first.timeModification().isPresent());
        assertEquals(3, first.timeModification().get().actualNotes());
        assertEquals(2, first.timeModification().get().normalNotes());
        boolean start = first.tuplets().stream().anyMatch(t -> t.type() == Tuplet.Type.START);
        assertTrue(start);
        boolean stop = notes.get(2).tuplets().stream().anyMatch(t -> t.type() == Tuplet.Type.STOP);
        assertTrue(stop);
    }

    @Test
    void readsBarRepeat() {
        String ly = "\\score { \\new Staff { \\time 4/4 \\key c \\major \\clef treble\n"
                + "  \\bar \"|:\" c'4 d'4 e'4 f'4 \\bar \":|\" |\n"
                + "  g'4 a'4 b'4 c''4 |\n"
                + "} }\n";
        Part part = read(ly).parts().get(0);
        assertFalse(part.measures().isEmpty());
        Measure first = part.measures().get(0);
        assertTrue(first.leadingRepeatStart() || first.barline().isPresent());
    }

    @Test
    void readsAddLyrics() {
        String ly = "\\score { \\new Staff { \\time 4/4 \\key c \\major \\clef treble\n"
                + "  c'4 d'4 e'4 f'4 |\n"
                + "}\n"
                + "\\addlyrics { do re mi fa }\n"
                + "}\n";
        List<Note> notes = notesOf(read(ly).parts().get(0));
        assertEquals("do", notes.get(0).lyrics().get(0).text());
        assertEquals("re", notes.get(1).lyrics().get(0).text());
        assertEquals("mi", notes.get(2).lyrics().get(0).text());
        assertEquals("fa", notes.get(3).lyrics().get(0).text());
    }

    @Test
    void readsChordMode() {
        String ly = "\\score { \\new Staff { \\time 4/4 \\key c \\major \\clef treble\n"
                + "  c'4 d'4 e'4 f'4 |\n"
                + "}\n"
                + "\\chordmode { c1:m }\n"
                + "}\n";
        Part part = read(ly).parts().get(0);
        boolean hasHarmony = false;
        for (Measure m : part.measures()) {
            for (MusicElement el : m.elements()) {
                if (el instanceof com.sheetmusic4j.core.model.Harmony) {
                    hasHarmony = true;
                }
            }
        }
        assertTrue(hasHarmony, "Expected at least one Harmony from \\chordmode");
    }

    @Test
    void stripsLineAndBlockComments() {
        String ly = "%{ big block comment %}\n"
                + "\\version \"2.24.0\" % trailing comment\n"
                + "\\score { \\new Staff { \\time 4/4 \\key c \\major \\clef treble\n"
                + "  c'4 d'4 e'4 f'4 |  %{ inline block %}\n"
                + "} }\n";
        Score score = read(ly);
        Part part = score.parts().get(0);
        assertEquals(4, notesOf(part).size());
    }
}
