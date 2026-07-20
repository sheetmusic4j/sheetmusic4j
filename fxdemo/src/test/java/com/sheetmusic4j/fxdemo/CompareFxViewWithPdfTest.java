package com.sheetmusic4j.fxdemo;

import com.sheetmusic4j.core.musicxml.MusicXmlReader;
import com.sheetmusic4j.core.model.Score;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Automated visual regression test comparing the Sheet4j engraving of a score
 * against a reference rendering, in the spirit of lottie4j's
 * {@code CompareFxViewWithWebViewTest}.
 *
 * <p>The score is rendered headlessly with the production {@link com.sheetmusic4j.fxviewer.ScorePainter}
 * (the same logic the JavaFX viewer uses). If a reference image is available on the
 * test classpath it is compared with {@link ImageSimilarity}:
 * <ul>
 *   <li>{@code /reference/c-major-scale.png} - compared directly, or</li>
 *   <li>{@code /reference/c-major-scale.pdf} - rasterized (PDFBox) then compared.</li>
 * </ul>
 * When no reference is provided (or PDFBox is unavailable) the comparison is skipped,
 * while the deterministic "produces ink" check still guards the rendering pipeline.
 *
 * <p>Drop a reference exported from your notation software into
 * {@code fxdemo/src/test/resources/reference/} to activate the strict comparison.
 */
class CompareFxViewWithPdfTest {

    private static final int WIDTH = 1000;
    private static final int HEIGHT = 300;
    private static final String REFERENCE_BASE = "/reference/c-major-scale";

    private static final double THRESHOLD =
            Double.parseDouble(System.getProperty("sheetmusic4j.compare.threshold", "0.6"));

    @BeforeAll
    static void headless() {
        System.setProperty("java.awt.headless", "true");
    }

    private Score loadScore() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/c-major-scale.musicxml")) {
            assertNotNull(in, "sample score resource must exist");
            return new MusicXmlReader().read(in);
        }
    }

    @Test
    void renderingPipelineProducesInk() throws Exception {
        BufferedImage rendered = HeadlessScoreImage.render(loadScore(), WIDTH, HEIGHT);
        double ink = ImageSimilarity.inkRatio(rendered);
        assertTrue(ink > 0.001, "rendered score should not be blank, ink ratio was " + ink);
    }

    @Test
    void matchesReferenceWhenAvailable() throws Exception {
        Optional<BufferedImage> reference = loadReference();
        assumeTrue(reference.isPresent(),
                "No reference image/PDF found (or PDFBox unavailable); skipping strict comparison. "
                        + "Add one under fxdemo/src/test/resources/reference/ to enable it.");

        BufferedImage rendered = HeadlessScoreImage.render(loadScore(), WIDTH, HEIGHT);
        double similarity = ImageSimilarity.similarity(rendered, reference.get());
        System.out.printf("Sheet4j vs reference similarity: %.4f (threshold %.2f)%n", similarity, THRESHOLD);

        assertTrue(similarity >= THRESHOLD,
                "Engraving differs from reference: similarity " + similarity + " < threshold " + THRESHOLD);
    }

    private Optional<BufferedImage> loadReference() {
        URL png = getClass().getResource(REFERENCE_BASE + ".png");
        if (png != null) {
            try (InputStream in = png.openStream()) {
                return Optional.ofNullable(ImageIO.read(in));
            } catch (Exception e) {
                return Optional.empty();
            }
        }
        URL pdf = getClass().getResource(REFERENCE_BASE + ".pdf");
        if (pdf != null && "file".equals(pdf.getProtocol())) {
            try {
                return PdfRasterizer.rasterizeFirstPage(Path.of(pdf.toURI()), 150f);
            } catch (Exception e) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }
}
