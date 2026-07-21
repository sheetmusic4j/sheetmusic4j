package com.sheetmusic4j.fxdemo.reference;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Regenerates the reference PNGs used by {@link com.sheetmusic4j.fxdemo.CompareFxViewWithReferenceTest}
 * from every {@code *.musicxml} sample by driving a headless JavaFX WebView loaded
 * with OpenSheetMusicDisplay.
 *
 * <p>This test is tagged {@code reference-generation} and is <b>excluded</b> from
 * the default surefire run. Run it explicitly with the {@code refresh-references}
 * Maven profile:
 *
 * <pre>
 *   mvn -pl fxdemo -Prefresh-references test
 * </pre>
 *
 * <p>If the OSMD bundle is not committed under
 * {@code fxdemo/src/test/resources/reference/osmd/opensheetmusicdisplay.min.js},
 * or if JavaFX-web cannot be booted, the test skips gracefully via
 * {@link Assumptions}.
 */
@Tag("reference-generation")
class GenerateReferenceImagesTest {

    private static final int WIDTH = 1000;
    private static final int HEIGHT = 300;

    private static final Path SAMPLES_DIR = Paths.get("src", "test", "resources", "xmlsamples");
    private static final Path SINGLE_SAMPLE = Paths.get("src", "test", "resources", "c-major-scale.musicxml");
    private static final Path REFERENCE_DIR =
            Paths.get("src", "test", "resources", "reference", "generated");

    static Stream<Path> fixtures() throws IOException {
        Stream.Builder<Path> builder = Stream.builder();
        if (Files.isRegularFile(SINGLE_SAMPLE)) {
            builder.add(SINGLE_SAMPLE);
        }
        if (Files.isDirectory(SAMPLES_DIR)) {
            try (var paths = Files.list(SAMPLES_DIR)) {
                List<Path> xmls = paths
                        .filter(p -> {
                            String n = p.getFileName().toString().toLowerCase();
                            return n.endsWith(".musicxml") || n.endsWith(".xml");
                        })
                        .sorted()
                        .toList();
                xmls.forEach(builder::add);
            }
        }
        return builder.build();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("fixtures")
    void regenerate(Path xml) throws IOException {
        String musicXml = Files.readString(xml);
        String sample = stripExtension(xml.getFileName().toString());

        ReferenceCache cache = new ReferenceCache(REFERENCE_DIR);
        WebViewReferenceRenderer.Result result = cache.getOrGenerate(sample, xml, musicXml, WIDTH, HEIGHT);

        Assumptions.assumeTrue(!result.missing(),
                "OSMD bundle or JavaFX-web unavailable; skipping. See "
                        + "fxdemo/src/test/resources/reference/osmd/README.md");
        assertTrue(result.isSuccess(),
                "Failed to render reference for " + sample + ": " + result.errorMsg());
        assertTrue(Files.isRegularFile(cache.referencePath(sample)),
                "Reference PNG was not written for " + sample);
    }

    private static String stripExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot >= 0 ? fileName.substring(0, dot) : fileName;
    }
}
