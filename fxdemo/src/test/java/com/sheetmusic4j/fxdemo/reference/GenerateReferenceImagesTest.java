package com.sheetmusic4j.fxdemo.reference;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
        String musicXml = readXml(xml);
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

    /**
     * MusicXML files in the wild are not always UTF-8: MuseScore emits UTF-16 for
     * some scores, others carry a UTF-8 BOM. {@link Files#readString(Path)} uses
     * UTF-8 unconditionally and throws {@link java.nio.charset.MalformedInputException}
     * on the first non-UTF-8 byte. Detect the encoding from BOM / XML declaration
     * and strip any leading BOM so OSMD receives clean text.
     */
    private static String readXml(Path path) throws IOException {
        byte[] bytes = Files.readAllBytes(path);
        Charset cs = detectCharset(bytes);
        int offset = bomLength(bytes, cs);
        return new String(bytes, offset, bytes.length - offset, cs);
    }

    private static final Pattern XML_ENCODING = Pattern.compile(
            "<\\?xml[^>]*encoding\\s*=\\s*[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);

    private static Charset detectCharset(byte[] bytes) {
        if (bytes.length >= 3
                && (bytes[0] & 0xFF) == 0xEF
                && (bytes[1] & 0xFF) == 0xBB
                && (bytes[2] & 0xFF) == 0xBF) {
            return StandardCharsets.UTF_8;
        }
        if (bytes.length >= 2 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xFE) {
            return StandardCharsets.UTF_16LE;
        }
        if (bytes.length >= 2 && (bytes[0] & 0xFF) == 0xFE && (bytes[1] & 0xFF) == 0xFF) {
            return StandardCharsets.UTF_16BE;
        }
        // No BOM: read the first ~200 bytes as ASCII to fish out the XML declaration.
        int head = Math.min(bytes.length, 200);
        String prolog = new String(bytes, 0, head, StandardCharsets.ISO_8859_1);
        Matcher m = XML_ENCODING.matcher(prolog);
        if (m.find()) {
            String name = m.group(1);
            try {
                return Charset.forName(name);
            } catch (Exception ignored) {
                // fall through to UTF-8
            }
        }
        return StandardCharsets.UTF_8;
    }

    private static int bomLength(byte[] bytes, Charset cs) {
        if (cs.equals(StandardCharsets.UTF_8)
                && bytes.length >= 3
                && (bytes[0] & 0xFF) == 0xEF
                && (bytes[1] & 0xFF) == 0xBB
                && (bytes[2] & 0xFF) == 0xBF) {
            return 3;
        }
        if ((cs.equals(StandardCharsets.UTF_16LE) || cs.equals(StandardCharsets.UTF_16BE))
                && bytes.length >= 2
                && ((bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xFE
                    || (bytes[0] & 0xFF) == 0xFE && (bytes[1] & 0xFF) == 0xFF)) {
            return 2;
        }
        return 0;
        }
        }
