package com.sheetmusic4j.engraving;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.sheetmusic4j.core.io.ScoreFile;
import com.sheetmusic4j.core.model.Score;
import com.sheetmusic4j.engraving.layout.LayoutMode;
import com.sheetmusic4j.engraving.layout.LayoutOptions;
import com.sheetmusic4j.engraving.layout.LayoutResult;
import org.junit.jupiter.api.Test;

/**
 * Characterization test: lays out every sample score in the repository in
 * several layout configurations and compares a SHA-256 of the full
 * {@link LayoutResult#toString()} against the committed
 * {@code layout-snapshots.txt}. Any change to engraving output - even a
 * sub-pixel one - shows up here, which makes it the safety net for
 * behaviour-preserving refactorings of {@link Engraver}.
 *
 * <p>When an engraving change is intentional, regenerate the snapshot file
 * with {@code -Dsheetmusic4j.snapshots.update=true}. On a mismatch the
 * actual layout dump is written to {@code target/layout-snapshots/} for
 * diffing; {@code -Dsheetmusic4j.snapshots.dumpAll=true} dumps every layout.
 */
class LayoutSnapshotTest {

    private static final Path SNAPSHOT_FILE = Paths.get("src/test/resources/layout-snapshots.txt");
    private static final Path DUMP_DIR = Paths.get("target/layout-snapshots");
    private static final List<String> SAMPLE_ROOTS = List.of(
            "../fxdemo/src/test/resources",
            "../core/src/test/resources");
    private static final List<String> EXTENSIONS = List.of(
            ".musicxml", ".xml", ".mxl", ".mid", ".midi", ".abc", ".ly");

    private static final Pattern IDENTITY = Pattern.compile("@[0-9a-f]{1,8}\\b");

    private static final Map<String, LayoutOptions> CONFIGURATIONS = new LinkedHashMap<>();

    static {
        CONFIGURATIONS.put("page", LayoutOptions.defaults());
        CONFIGURATIONS.put("narrow", LayoutOptions.builder().systemWidth(500).build());
        CONFIGURATIONS.put("strip", LayoutOptions.builder().layoutMode(LayoutMode.STRIP).build());
    }

    @Test
    void layoutsMatchSnapshots() throws IOException {
        List<Path> samples = findSamples();
        assumeTrue(!samples.isEmpty(), "no sample scores found");

        Map<String, String> actual = new LinkedHashMap<>();
        Map<String, String> dumps = new LinkedHashMap<>();
        for (Path sample : samples) {
            String name = Paths.get("..").toAbsolutePath().normalize()
                    .relativize(sample.toAbsolutePath().normalize()).toString().replace('\\', '/');
            Score score;
            try {
                score = ScoreFile.load(sample);
            } catch (RuntimeException e) {
                continue;
            }
            for (Map.Entry<String, LayoutOptions> config : CONFIGURATIONS.entrySet()) {
                String key = name + "|" + config.getKey();
                String dump;
                try {
                    dump = normalizeIdentities(new Engraver().layout(score, config.getValue()).toString());
                } catch (RuntimeException e) {
                    dump = "EXCEPTION " + e;
                }
                actual.put(key, sha256(dump));
                dumps.put(key, dump);
            }
        }

        if (Boolean.getBoolean("sheetmusic4j.snapshots.update") || !Files.exists(SNAPSHOT_FILE)) {
            List<String> lines = new ArrayList<>();
            actual.forEach((k, v) -> lines.add(k + "=" + v));
            Files.createDirectories(SNAPSHOT_FILE.getParent());
            Files.write(SNAPSHOT_FILE, lines, StandardCharsets.UTF_8);
            return;
        }

        Map<String, String> expected = new LinkedHashMap<>();
        for (String line : Files.readAllLines(SNAPSHOT_FILE, StandardCharsets.UTF_8)) {
            int eq = line.lastIndexOf('=');
            if (eq > 0) {
                expected.put(line.substring(0, eq), line.substring(eq + 1));
            }
        }

        boolean dumpAll = Boolean.getBoolean("sheetmusic4j.snapshots.dumpAll");
        List<String> mismatches = new ArrayList<>();
        for (Map.Entry<String, String> e : actual.entrySet()) {
            String want = expected.get(e.getKey());
            if (dumpAll || (want != null && !want.equals(e.getValue()))) {
                if (want != null && !want.equals(e.getValue())) {
                    mismatches.add(e.getKey());
                }
                Path dumpFile = DUMP_DIR.resolve(e.getKey().replaceAll("[^A-Za-z0-9._-]", "_") + ".txt");
                Files.createDirectories(DUMP_DIR);
                Files.writeString(dumpFile, dumps.get(e.getKey()), StandardCharsets.UTF_8);
            }
        }
        assertTrue(mismatches.isEmpty(), "layout changed for " + mismatches.size() + " snapshot(s), e.g. "
                + mismatches.subList(0, Math.min(5, mismatches.size())) + " (dumps in " + DUMP_DIR.toAbsolutePath() + ")");
    }

    private static List<Path> findSamples() throws IOException {
        List<Path> result = new ArrayList<>();
        for (String root : SAMPLE_ROOTS) {
            Path dir = Paths.get(root);
            if (!Files.isDirectory(dir)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(dir)) {
                files.filter(Files::isRegularFile)
                        .filter(p -> EXTENSIONS.stream()
                                .anyMatch(ext -> p.getFileName().toString().toLowerCase().endsWith(ext)))
                        .sorted()
                        .forEach(result::add);
            }
        }
        return result;
    }

    /**
     * Model classes without their own {@code toString()} print as
     * {@code Class@identityHash}, which shifts whenever allocation order
     * changes. Relabel each distinct identity by order of first appearance
     * so the dump stays stable while still recording which placements
     * reference the same element.
     */
    private static String normalizeIdentities(String dump) {
        Map<String, Integer> ids = new HashMap<>();
        Matcher m = IDENTITY.matcher(dump);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            int id = ids.computeIfAbsent(m.group(), k -> ids.size());
            m.appendReplacement(sb, "@#" + id);
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
