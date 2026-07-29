package com.sheetmusic4j.core.guitarpro;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import org.junit.jupiter.api.Test;

import com.sheetmusic4j.core.io.ScoreFile;
import com.sheetmusic4j.core.model.Attributes;
import com.sheetmusic4j.core.model.Chord;
import com.sheetmusic4j.core.model.Measure;
import com.sheetmusic4j.core.model.MusicElement;
import com.sheetmusic4j.core.model.Note;
import com.sheetmusic4j.core.model.Part;
import com.sheetmusic4j.core.model.Rest;
import com.sheetmusic4j.core.model.Score;

class GuitarProImporterTest {

    /**
     * Sample GuitarPro files live in the {@code fxdemo} module's test
     * resources. Locate one relative to the module base directory (surefire
     * runs with the module directory as the working directory); the test is
     * skipped when the fixture cannot be found so the build stays green in
     * environments that do not ship the samples.
     */
    private static Path fixture(String relative) {
        Path[] candidates = {
                Path.of("src/test/resources/guitarpro", relative),
                Path.of("../fxdemo/src/test/resources/guitarpro", relative),
        };
        for (Path candidate : candidates) {
            if (Files.exists(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    @Test
    void importsGpFileIntoScore() {
        Path path = fixture("death-painkiller/death-painkiller.gp");
        assumeTrue(path != null, "GuitarPro .gp fixture not available");

        Score score = new GuitarProImporter().fromGuitarPro(path);

        assertFalse(score.parts().isEmpty(), "expected at least one part");
        Part part = score.parts().get(0);
        assertFalse(part.measures().isEmpty(), "expected at least one measure");

        Measure first = part.measures().get(0);
        assertTrue(first.attributes().isPresent(), "first measure carries attributes");
        Attributes attributes = first.attributes().get();
        assertEquals(960, attributes.divisions().orElseThrow());
        assertTrue(attributes.timeSignature().isPresent(), "first measure carries a time signature");
        assertTrue(attributes.staves().orElse(1) >= 1);
        assertEquals(attributes.staves().orElse(1), attributes.clefs().size(),
                "one clef per staff on the first measure");

        assertTrue(hasMusicElement(part), "expected notes, chords or rests");
        assertTrue(allStavesInRange(part), "every element's staff index is within the declared staves");
    }

    @Test
    void scoreFileDispatchesGuitarProExtension() {
        Path path = fixture("death-painkiller/death-painkiller.gp");
        assumeTrue(path != null, "GuitarPro .gp fixture not available");

        Score score = ScoreFile.load(path);

        assertFalse(score.parts().isEmpty());
    }

    @Test
    void rejectsInvalidData() {
        GuitarProImporter importer = new GuitarProImporter();
        assertThrows(GuitarProException.class,
                () -> importer.fromGuitarPro(new ByteArrayInputStream(new byte[]{0, 1, 2})));
    }

    private static boolean hasMusicElement(Part part) {
        for (Measure measure : part.measures()) {
            for (MusicElement element : measure.elements()) {
                if (element instanceof Note || element instanceof Chord || element instanceof Rest) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean allStavesInRange(Part part) {
        int staves = part.measures().isEmpty()
                ? 1
                : part.measures().get(0).attributes().flatMap(Attributes::staves).orElse(1);
        int maxStaff = Math.max(staves, 1);
        for (Measure measure : part.measures()) {
            for (MusicElement element : measure.elements()) {
                int staff = staffOf(element);
                if (staff < 1 || staff > maxStaff) {
                    return false;
                }
            }
        }
        return true;
    }

    private static int staffOf(MusicElement element) {
        if (element instanceof Note note) {
            return note.staff();
        }
        if (element instanceof Rest rest) {
            return rest.staff();
        }
        if (element instanceof Chord chord) {
            return chord.notes().get(0).staff();
        }
        return 1;
    }
}
