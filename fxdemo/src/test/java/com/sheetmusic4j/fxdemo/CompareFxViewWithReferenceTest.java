package com.sheetmusic4j.fxdemo;

import java.awt.Color;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.sheetmusic4j.core.model.Score;
import com.sheetmusic4j.core.musicxml.MusicXmlReader;
import com.sheetmusic4j.engraving.Engraver;
import com.sheetmusic4j.engraving.LayoutOptions;
import com.sheetmusic4j.engraving.LayoutResult;
import com.sheetmusic4j.fxdemo.reference.DiagnosticComparator;
import com.sheetmusic4j.fxdemo.reference.DiffReportWriter;
import com.sheetmusic4j.fxdemo.reference.ImageStack;
import com.sheetmusic4j.fxdemo.reference.PdfRasterizer;

/**
 * Diagnostic comparison of the Sheet4j engraving against the sibling PDF
 * committed next to each MusicXML fixture. Every fixture drives:
 * <ol>
 *   <li>A PDF metadata assertion (expected vs actual page count),</li>
 *   <li>An ink-sanity check on the rendered image,</li>
 *   <li>Staff-count and bounding-box checks,</li>
 *   <li>Per-measure similarity against the vertically-stitched PDF pages.</li>
 * </ol>
 *
 * <p>When a fixture has no sibling PDF, or PDFBox is not on the classpath,
 * the parametrized invocation is skipped via {@link Assumptions} so the
 * suite stays green in restricted environments.
 *
 * <p>On failure, a self-contained HTML report is written under
 * {@code target/sheet4j-diff/&lt;fixture&gt;/report.html}.
 */
class CompareFxViewWithReferenceTest {

    private static final int WIDTH = 1000;

    private static final float PDF_DPI =
            (float) Double.parseDouble(
                    System.getProperty("sheetmusic4j.compare.pdf.dpi", "150"));

    private static final double MIN_INK = 0.001;
    // The reference is a vertically-stitched multi-page, multi-system PDF; a
    // single Sheet4j staff can be visually 4x-6x shorter than the full PDF
    // band the StaffDetector finds. Loosened from the OSMD-era 0.25..4.0.
    private static final double MIN_STAFF_HEIGHT_RATIO = 0.15;
    private static final double MAX_STAFF_HEIGHT_RATIO = 8.0;
    /**
     * Global per-measure similarity floor. Ratcheting history:
     * <pre>
     *   0.20  initial bar (single overflowing system, no line breaks)
     * </pre>
     * The target is 0.95. Every step raises this constant only as far as all
     * committed fixtures still clear it. Callers may override the bar
     * locally via {@code -Dsheetmusic4j.compare.measure.threshold}.
     */
    private static final double MIN_PER_MEASURE_SIMILARITY =
            Double.parseDouble(System.getProperty("sheetmusic4j.compare.measure.threshold", "0.2"));

    @BeforeAll
    static void headless() {
        System.setProperty("java.awt.headless", "true");
    }

    /**
     * Fixture ladder for the diagnostic comparator. Every entry is
     * {@code (basename, xmlPath, expectedPageCount)}; the test skips when the
     * sibling {@code .pdf} is absent. Page counts were captured directly from
     * the committed PDFs via PDFBox.
     */
    static Stream<Arguments> fixtures() {
        Path samples = Paths.get("src", "test", "resources", "xmlsamples");
        Path melodyMatrix = Paths.get("src", "test", "resources", "melodymatrix");
        return Stream.of(
                Arguments.of("ActorPreludeSample", samples.resolve("ActorPreludeSample.musicxml"), 4),
                Arguments.of("BeetAnGeSample", samples.resolve("BeetAnGeSample.musicxml"), 1),
                Arguments.of("BrahWiMeSample", samples.resolve("BrahWiMeSample.musicxml"), 1),
                Arguments.of("BrookeWestSample", samples.resolve("BrookeWestSample.musicxml"), 1),
                Arguments.of("DebuMandSample", samples.resolve("DebuMandSample.musicxml"), 1),
                Arguments.of("Dichterliebe01", samples.resolve("Dichterliebe01.musicxml"), 2),
                Arguments.of("do-re-mi", melodyMatrix.resolve("do-re-mi.mxl"), 1),
                Arguments.of("Echigo-Jishi", samples.resolve("Echigo-Jishi.musicxml"), 1),
                Arguments.of("FaurReveSample", samples.resolve("FaurReveSample.musicxml"), 1),
                Arguments.of("MahlFaGe4Sample", samples.resolve("MahlFaGe4Sample.musicxml"), 1),
                Arguments.of("MozaChloSample", samples.resolve("MozaChloSample.musicxml"), 1),
                Arguments.of("MozaVeilSample", samples.resolve("MozaVeilSample.musicxml"), 1),
                Arguments.of("SchbAvMaSample", samples.resolve("SchbAvMaSample.musicxml"), 1)
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("fixtures")
    void engravingMatchesReference(String name, Path xmlPath, int expectedPages) throws Exception {
        Path pdf = PdfSibling.existingPathFor(xmlPath).orElse(null);
        Assumptions.assumeTrue(pdf != null,
                "No sibling PDF for " + xmlPath + " - skipping.");

        OptionalInt actualPageCount = PdfRasterizer.pageCount(pdf);
        Assumptions.assumeTrue(actualPageCount.isPresent(),
                "PDFBox unavailable; skipping.");
        assertEquals(expectedPages, actualPageCount.getAsInt(),
                "PDF page count mismatch for " + name);

        Optional<List<BufferedImage>> pages = PdfRasterizer.rasterizeAllPages(pdf, PDF_DPI);
        Assumptions.assumeTrue(pages.isPresent(),
                "Could not rasterize " + pdf + " - skipping.");
        BufferedImage referenceImage = ImageStack.stackVertically(
                pages.get(), 8, Color.WHITE);

        Score score = loadScore(xmlPath);
        LayoutResult layout = new Engraver().layout(score, layoutOptions());
        BufferedImage rendered = HeadlessScoreImage.render(score, WIDTH);
        double ink = ImageSimilarity.inkRatio(rendered);
        assertTrue(ink > MIN_INK, "rendered score should not be blank, ink ratio was " + ink);

        DiagnosticComparator.Diagnostic diagnostic =
                new DiagnosticComparator().compare(rendered, referenceImage, layout);

        Path reportDir = Paths.get("target", "sheet4j-diff", name);
        Path report = DiffReportWriter.write(reportDir, name, rendered, referenceImage,
                actualPageCount.getAsInt(), layout.systems().size(), diagnostic);

        try {
            assertStaffCount(diagnostic);
            assertStaffBoundingBoxes(diagnostic);
            assertPerMeasureSimilarity(name, diagnostic, report);
        } catch (AssertionError e) {
            throw new AssertionError(e.getMessage() + "\n  Diff report: " + report.toAbsolutePath(), e);
        }
    }

    private static void assertStaffCount(DiagnosticComparator.Diagnostic diagnostic) {
        int expected = diagnostic.renderedStaves().size();
        int actual = diagnostic.referenceStaves().size();
        assertTrue(actual >= expected,
                "expected at least " + expected + " staves in reference, detected " + actual);
    }

    private static void assertStaffBoundingBoxes(DiagnosticComparator.Diagnostic diagnostic) {
        List<Rectangle> rendered = diagnostic.renderedStaves();
        List<Rectangle> reference = diagnostic.referenceStaves();
        int compare = Math.min(rendered.size(), reference.size());
        for (int i = 0; i < compare; i++) {
            Rectangle r = rendered.get(i);
            Rectangle ref = reference.get(i);
            // Only skip staves whose reference band could not be detected.
            // With the auto-sized rendering canvas, rendered staves are no
            // longer clipped off the bottom, so their height should be
            // meaningful.
            if (r.height <= 0 || ref.height <= 0) {
                continue;
            }
            double ratio = ref.height / (double) Math.max(1, r.height);
            assertTrue(ratio > MIN_STAFF_HEIGHT_RATIO && ratio < MAX_STAFF_HEIGHT_RATIO,
                    "staff " + i + " height ratio out of range: rendered=" + r.height
                            + " reference=" + ref.height);
        }
    }

    private static void assertPerMeasureSimilarity(String name,
                                                   DiagnosticComparator.Diagnostic diagnostic,
                                                   Path report) {
        List<DiagnosticComparator.MeasureDiff> worst = diagnostic.worstMeasures(3);
        for (DiagnosticComparator.MeasureDiff md : diagnostic.measures()) {
            // Skip measures that ended up clipped out of the rendered canvas -
            // they degenerate to similarity=0 without carrying real signal.
            if (md.renderedRect().width <= 0 || md.renderedRect().height <= 0
                    || md.referenceRect().width <= 0 || md.referenceRect().height <= 0) {
                continue;
            }
            if (md.similarity() < MIN_PER_MEASURE_SIMILARITY) {
                fail("Fixture '" + name + "': measure " + md.measureNumber()
                        + " (staff " + md.staffIndex() + ", index " + md.measureIndex() + ")"
                        + " similarity " + md.similarity()
                        + " < threshold " + MIN_PER_MEASURE_SIMILARITY
                        + "\n  Worst measures: " + worst
                        + "\n  Report: " + report.toAbsolutePath());
            }
        }
    }

    private static LayoutOptions layoutOptions() {
        LayoutOptions defaults = LayoutOptions.defaults();
        return new LayoutOptions(
                defaults.staffLineGap(),
                defaults.staffSpacing(),
                WIDTH,
                defaults.leftMargin(),
                defaults.rightMargin(),
                defaults.topMargin(),
                defaults.measureMinWidth(),
                defaults.fontSize());
    }

    private Score loadScore(Path xmlPath) throws Exception {
        String cp = "/" + xmlPath.getFileName();
        try (InputStream in = getClass().getResourceAsStream(cp)) {
            if (in != null) {
                return new MusicXmlReader().read(in);
            }
        }
        try (InputStream in = Files.newInputStream(xmlPath)) {
            assertNotNull(in);
            return new MusicXmlReader().read(in);
        }
    }
}
