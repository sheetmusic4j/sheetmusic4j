package com.sheetmusic4j.core.abc;

import com.sheetmusic4j.core.model.Part;
import com.sheetmusic4j.core.model.Score;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Smoke-tests {@link AbcReader} against every {@code .abc} file discovered
 * under {@code abc/examples/} on the classpath. The parser must survive each
 * file (no exceptions) and produce a {@link Score} with at least one part
 * containing at least one measure.
 */
class AbcExamplesTest {

    static Stream<Path> examples() throws IOException {
        URL dir = AbcExamplesTest.class.getResource("/abc/examples");
        assertNotNull(dir, "abc/examples/ directory not present on the classpath");
        Path root = Paths.get(dir.getPath());
        List<Path> out = new ArrayList<>();
        try (var stream = Files.walk(root)) {
            stream.filter(p -> p.toString().endsWith(".abc") && !p.toString().contains("unsupported"))
                    .forEach(out::add);
        }
        return out.stream();
    }

    @ParameterizedTest(name = "parses {0}")
    @MethodSource("examples")
    void parsesExample(Path path) throws IOException {
        String abc = Files.readString(path);
        Score score = new AbcReader().read(new java.io.ByteArrayInputStream(
                abc.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        assertFalse(score.parts().isEmpty(), "no parts parsed from " + path);
        Part part = score.parts().get(0);
        assertFalse(part.measures().isEmpty(), "no measures parsed from " + path);
    }
}
