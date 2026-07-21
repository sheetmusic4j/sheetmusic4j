package com.sheetmusic4j.fxdemo;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

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
import com.sheetmusic4j.fxdemo.reference.ReferenceCache;

/**
 * Diagnostic replacement for the old whole-image {@code CompareFxViewWithPdfTest}:
 * for every MusicXML fixture that has a committed reference PNG under
 * {@code fxdemo/src/test/resources/reference/generated/}, compare the Sheet4j
 * engraving against the reference through a series of layered assertions
 * (ink sanity, staff count, staff bounding boxes, per-measure similarity).
 *
 * <p>When no reference is present (which is the case on a fresh checkout unless
 * {@code mvn -pl fxdemo -Prefresh-references test} has been run), the test is
 * skipped via {@link Assumptions}, so this class stays green in CI without any
 * network access.
 *
 * <p>On failure, a self-contained HTML report is written under
 * {@code target/sheet4j-diff/&lt;fixture&gt;/report.html} and its path is
 * printed in the assertion message.
 */
class CompareFxViewWithReferenceTest {

    private static final int WIDTH = 1000;
    private static final int HEIGHT = 300;

    private static final Path REFERENCE_DIR =
            Paths.get("src", "test", "resources", "reference", "generated");

    private static final double MIN_INK = 0.001;
    private static final double MIN_PER_MEASURE_SIMILARITY =
            Double.parseDouble(System.getProperty("sheetmusic4j.compare.measure.threshold", "0.4"));

    @BeforeAll
    static void headless() {
        System.setProperty("java.awt.headless", "true");
    }

    /**
     * Fixture ladder for the diagnostic comparator. To add a new fixture:
     * <ol>
     *   <li>drop the OSMD-generated reference PNG at
     *       {@code fxdemo/src/test/resources/reference/generated/&lt;basename&gt;.png}
     *       (typically by running {@code mvn -pl fxdemo -Prefresh-references test}),</li>
     *   <li>add an {@code Arguments.of(basename, path)} line below.</li>
     * </ol>
     * When a reference PNG is missing, the corresponding parameterised invocation
     * skips gracefully via {@link Assumptions}.
     */
    static Stream<Arguments> fixtures() {
        Path resourcesRoot = Paths.get("src", "test", "resources");
        Path samples = resourcesRoot.resolve("xmlsamples");
        return Stream.of(
                Arguments.of("c-major-scale", resourcesRoot.resolve("c-major-scale.musicxml")),
                Arguments.of("Saltarello", samples.resolve("Saltarello.musicxml")),
                Arguments.of("Telemann", samples.resolve("Telemann.musicxml")),
                Arguments.of("Echigo-Jishi", samples.resolve("Echigo-Jishi.musicxml")),
                Arguments.of("Dichterliebe01", samples.resolve("Dichterliebe01.musicxml")),
                Arguments.of("MozartPianoSonata", samples.resolve("MozartPianoSonata.musicxml")),
                Arguments.of("MozartTrio", samples.resolve("MozartTrio.musicxml"))
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("fixtures")
    void engravingMatchesReference(String name, Path xmlPath) throws Exception {
        Path reference = REFERENCE_DIR.resolve(name + ".png");
        Assumptions.assumeTrue(Files.isRegularFile(reference),
                "No reference PNG at " + reference.toAbsolutePath()
                        + " - run `mvn -pl fxdemo -Prefresh-references test` to generate one.");

        Score score = loadScore(xmlPath);
        BufferedImage rendered = HeadlessScoreImage.render(score, WIDTH, HEIGHT);
        double ink = ImageSimilarity.inkRatio(rendered);
        assertTrue(ink > MIN_INK, "rendered score should not be blank, ink ratio was " + ink);

        BufferedImage referenceImage = new ReferenceCache(REFERENCE_DIR).load(name)
                .orElseThrow(() -> new IllegalStateException("Reference PNG could not be loaded: " + reference));

        LayoutResult layout = new Engraver().layout(score, layoutOptions());
        DiagnosticComparator.Diagnostic diagnostic =
                new DiagnosticComparator().compare(rendered, referenceImage, layout);

        Path reportDir = Paths.get("target", "sheet4j-diff", name);
        Path report = DiffReportWriter.write(reportDir, name, rendered, referenceImage, diagnostic);

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
            // Heights should be within an order of magnitude of each other.
            double ratio = ref.height / (double) Math.max(1, r.height);
            assertTrue(ratio > 0.25 && ratio < 4.0,
                    "staff " + i + " height ratio out of range: rendered=" + r.height
                            + " reference=" + ref.height);
        }
    }

    private static void assertPerMeasureSimilarity(String name,
                                                   DiagnosticComparator.Diagnostic diagnostic,
                                                   Path report) {
        List<DiagnosticComparator.MeasureDiff> worst = diagnostic.worstMeasures(3);
        for (DiagnosticComparator.MeasureDiff md : diagnostic.measures()) {
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
        // Prefer classpath (works from IDE) with filesystem fallback.
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
