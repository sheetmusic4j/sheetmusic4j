package com.sheetmusic4j.core.lilypond;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import org.junit.jupiter.api.Test;

import com.sheetmusic4j.core.model.Chord;
import com.sheetmusic4j.core.model.Measure;
import com.sheetmusic4j.core.model.MusicElement;
import com.sheetmusic4j.core.model.Note;
import com.sheetmusic4j.core.model.Part;
import com.sheetmusic4j.core.model.Score;

/**
 * Regression tests for the LilyPond examples pulled from
 * {@code lilypond.org/examples.html} into {@code fxdemo/src/test/resources/lilypond/}.
 * These files exercise features (variable definitions, {@code \context},
 * {@code \repeat volta}, {@code \\new PianoStaff}, polyphony {@code << … >>})
 * that the MVP does not fully model but should at least tolerate: each file
 * must parse and produce at least one note in the resulting {@link Score}.
 */
class LilyPondRealWorldTest {

    private static Path fixture(String name) {
        // Located relative to core/ working dir.
        return Paths.get("..", "fxdemo", "src", "test", "resources", "lilypond", name);
    }

    private static int countNotes(Score score) {
        int total = 0;
        for (Part part : score.parts()) {
            for (Measure m : part.measures()) {
                for (MusicElement el : m.elements()) {
                    if (el instanceof Note) {
                        total++;
                    } else if (el instanceof Chord c) {
                        total += c.notes().size();
                    }
                }
            }
        }
        return total;
    }

    private static Score readFixture(String name) throws Exception {
        Path file = fixture(name);
        assumeTrue(Files.exists(file), name + " fixture not available");
        String ly = Files.readString(file, StandardCharsets.UTF_8);
        return new LilyPondReader().read(new ByteArrayInputStream(
                ly.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void bachBwv610ProducesNotes() throws Exception {
        Score score = readFixture("bach-bwv610.ly");
        assertFalse(score.parts().isEmpty());
        int notes = countNotes(score);
        assertTrue(notes >= 20,
                "bach-bwv610.ly should produce at least 20 notes, got " + notes);
    }

    @Test
    void chartProducesNotes() throws Exception {
        Score score = readFixture("chart.ly");
        assertFalse(score.parts().isEmpty());
        int notes = countNotes(score);
        assertTrue(notes >= 8,
                "chart.ly should produce at least 8 notes, got " + notes);
    }

    @Test
    void theoryProducesNotes() throws Exception {
        Score score = readFixture("theory.ly");
        assertFalse(score.parts().isEmpty());
        int notes = countNotes(score);
        assertTrue(notes >= 4,
                "theory.ly should produce at least 4 notes, got " + notes);
    }

    @Test
    void aucunSnippetProducesNotes() throws Exception {
        Score score = readFixture("aucun-snippet.ly");
        assertFalse(score.parts().isEmpty());
        int notes = countNotes(score);
        assertTrue(notes >= 8,
                "aucun-snippet.ly should produce at least 8 notes, got " + notes);
    }

    @Test
    void sestoFullSurvivesBookWrapper() throws Exception {
        // sesto-full.ly wraps its music in `\book { \score { … } }`. Read
        // from a stream, its `\include "sesto.ily"` cannot be resolved, but
        // the parser must still descend into the \book wrapper to reach the
        // \score body rather than swallowing the whole score block.
        Score score = readFixture("sesto-full.ly");
        assertFalse(score.parts().isEmpty(),
                "parser must reach the \\score body inside \\book { … }");
        assertFalse(score.parts().get(0).measures().isEmpty(),
                "the score's staff should surface at least one measure of "
                        + "attributes even when notes come from an unavailable include");
    }

    @Test
    void sestoPianoReadsItsIncludedMusic() throws Exception {
        // Read from the file, `\include "sesto.ily"` resolves next to it:
        // the header, vocal line, lyrics and part-combined violins all come from there.
        Path file = fixture("sesto-piano.ly");
        assumeTrue(Files.exists(file.resolveSibling("sesto.ily")), "sesto.ily not available");
        Score score = new LilyPondReader().read(file);
        assertTrue(score.workTitle().orElse("").contains("Giulio Cesare"));
        assertTrue(countNotes(score) >= 100, "expected the included notes, got " + countNotes(score));
        assertTrue(score.parts().get(0).measures().stream()
                .flatMap(m -> m.elements().stream())
                .anyMatch(e -> e instanceof Note n && !n.lyrics().isEmpty()), "expected lyrics on the vocal line");
    }
    }
