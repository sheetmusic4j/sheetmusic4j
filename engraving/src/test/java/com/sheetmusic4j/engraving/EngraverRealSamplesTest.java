package com.sheetmusic4j.engraving;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import org.junit.jupiter.api.Test;

import com.sheetmusic4j.core.model.Score;
import com.sheetmusic4j.core.musicxml.MusicXmlReader;

/**
 * Smoke tests for the multi-part sample scores that motivated Task 6.
 * These fixtures live in the {@code fxdemo} test resources, so the tests
 * are guarded with {@link org.junit.jupiter.api.Assumptions#assumeTrue}:
 * builds that only ship the engraving module still run cleanly.
 */
class EngraverRealSamplesTest {

    private static Path findSample(String fileName) {
        String[] candidates = {
                "../fxdemo/src/test/resources/xmlsamples/" + fileName,
                "fxdemo/src/test/resources/xmlsamples/" + fileName,
                "sheetmusic4j/fxdemo/src/test/resources/xmlsamples/" + fileName
        };
        for (String candidate : candidates) {
            Path p = Paths.get(candidate).toAbsolutePath().normalize();
            if (Files.exists(p)) {
                return p;
            }
        }
        return null;
    }

    private static LayoutResult layoutOf(Score score) {
        return new Engraver().layout(score, LayoutOptions.defaults());
    }

    private static long partLabelCount(LayoutResult layout) {
        return layout.texts().stream()
                .filter(t -> t.category() == MarkingCategory.PART_LABEL)
                .count();
    }

    @Test
    void mozaVeilSampleHasVoicePlusPianoGrandStaff() {
        Path p = findSample("MozaVeilSample.musicxml");
        assumeTrue(p != null, "MozaVeilSample.musicxml not on the test classpath");

        Score score = new MusicXmlReader().read(p);
        assertEquals(2, score.parts().size(), "expected Voice + Piano");
        LayoutResult layout = layoutOf(score);
        assertNotNull(layout);
        // Voice = 1 staff, Piano grand-staff = 2 staves.
        assertTrue(layout.systems().get(0).staves().size() >= 3,
                "expected at least 3 staves (Voice + Piano grand staff)");
        // Piano brace present.
        assertTrue(layout.systems().get(0).brackets().size() >= 1,
                "expected at least one brace for the Piano grand staff");
        // One system-wide barline.
        assertEquals(1, layout.systems().get(0).barlines().size());
        // At least 2 PART_LABEL placements per row (Voice + Piano).
        assertTrue(partLabelCount(layout) >= 2 * layout.systems().size(),
                "expected at least 2 part labels per row, got " + partLabelCount(layout));
    }

    @Test
    void schbAvMaSampleHasVoicePlusPianoGrandStaff() {
        Path p = findSample("SchbAvMaSample.musicxml");
        assumeTrue(p != null, "SchbAvMaSample.musicxml not on the test classpath");

        Score score = new MusicXmlReader().read(p);
        assertEquals(2, score.parts().size(), "expected Voice + Piano");
        LayoutResult layout = layoutOf(score);
        assertNotNull(layout);
        assertTrue(layout.systems().get(0).staves().size() >= 3);
        assertTrue(layout.systems().get(0).brackets().size() >= 1,
                "expected at least one brace for the Piano grand staff");
        assertEquals(1, layout.systems().get(0).barlines().size());
        assertTrue(partLabelCount(layout) >= 2 * layout.systems().size());
    }
}
