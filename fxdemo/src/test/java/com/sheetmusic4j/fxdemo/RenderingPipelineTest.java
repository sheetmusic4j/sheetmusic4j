package com.sheetmusic4j.fxdemo;

import com.sheetmusic4j.core.model.Score;
import com.sheetmusic4j.core.musicxml.MusicXmlReader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Non-comparative smoke test for the headless engraving pipeline. Ensures the
 * production {@link com.sheetmusic4j.fxviewer.ScorePainter} produces a non-blank
 * image for the shared {@code c-major-scale.musicxml} fixture, independent of
 * whether a reference image is available.
 *
 * <p>The stricter, reference-based comparison lives in
 * {@link CompareFxViewWithReferenceTest}.
 */
class RenderingPipelineTest {

    private static final int WIDTH = 1000;
    private static final int HEIGHT = 300;

    @BeforeAll
    static void headless() {
        System.setProperty("java.awt.headless", "true");
    }

    @Test
    void producesInk() throws Exception {
        Score score;
        try (InputStream in = getClass().getResourceAsStream("/c-major-scale.musicxml")) {
            assertNotNull(in, "sample score resource must exist");
            score = new MusicXmlReader().read(in);
        }
        BufferedImage rendered = HeadlessScoreImage.render(score, WIDTH, HEIGHT);
        double ink = ImageSimilarity.inkRatio(rendered);
        assertTrue(ink > 0.001, "rendered score should not be blank, ink ratio was " + ink);
    }
}
