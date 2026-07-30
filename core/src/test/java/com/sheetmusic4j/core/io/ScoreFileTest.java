package com.sheetmusic4j.core.io;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.sheetmusic4j.core.model.Score;

/**
 * Extension-dispatch checks for the {@link ScoreFile} facade. The individual
 * readers/writers have their own dedicated tests; here we only verify that
 * <em>every</em> supported extension is routed to a reader/writer rather than
 * rejected with "Unsupported file extension".
 */
class ScoreFileTest {

    /** A base score used to drive save/load round-trips for the writable formats. */
    private static Score baseScore() {
        Path musicXml = Path.of("src/test/resources/c-major-scale.musicxml");
        assumeTrue(Files.exists(musicXml), "base MusicXML fixture not available");
        return ScoreFile.load(musicXml);
    }

    /**
     * Every extension that has both a reader and a writer must round-trip
     * through the facade: {@code save(ext)} then {@code load(ext)} both dispatch
     * to the right format and yield a non-empty score. This covers .musicxml,
     * .xml, .mxl (MusicXML), .mid, .midi (MIDI) and .abc (ABC) in one place.
     */
    @ParameterizedTest(name = ".{0} round-trips through ScoreFile")
    @ValueSource(strings = {"musicxml", "xml", "mxl", "mid", "midi", "abc"})
    void writableExtensionRoundTripsThroughFacade(String extension, @TempDir Path dir) {
        Score original = baseScore();

        Path out = dir.resolve("round-trip." + extension);
        ScoreFile.save(original, out);
        assertTrue(Files.exists(out), "ScoreFile.save must write a ." + extension + " file");

        Score reloaded = ScoreFile.load(out);
        assertFalse(reloaded.parts().isEmpty(),
                "reloading the saved ." + extension + " file must yield at least one part");
    }

    /**
     * GuitarPro is load-only. The fixture lives in the {@code fxdemo} module, so
     * locate it relative to this module and skip when it is not present.
     */
    @Test
    void loadsGuitarProThroughFacade() {
        Path gp = Path.of("../fxdemo/src/test/resources/guitarpro/death-painkiller/death-painkiller.gp");
        assumeTrue(Files.exists(gp), "GuitarPro fixture not available");

        Score score = ScoreFile.load(gp);
        assertFalse(score.parts().isEmpty(), "GuitarPro must load into at least one part");
    }

    @Test
    void savingGuitarProIsUnsupported(@TempDir Path dir) {
        Score score = baseScore();
        assertThrows(UnsupportedOperationException.class,
                () -> ScoreFile.save(score, dir.resolve("out.gp")));
    }

    @Test
    void unknownExtensionIsRejectedOnLoad() {
        assertThrows(IllegalArgumentException.class,
                () -> ScoreFile.load(Path.of("mystery.foo")));
    }

    @Test
    void unknownExtensionIsRejectedOnSave(@TempDir Path dir) {
        Score score = baseScore();
        assertThrows(IllegalArgumentException.class,
                () -> ScoreFile.save(score, dir.resolve("mystery.foo")));
    }

    @Test
    void extensionMatchingIsCaseInsensitive(@TempDir Path dir) {
        Score original = baseScore();
        Path out = dir.resolve("UPPER.MUSICXML");
        ScoreFile.save(original, out);
        Score reloaded = ScoreFile.load(out);
        assertFalse(reloaded.parts().isEmpty(), "extension dispatch must ignore case");
    }
}
