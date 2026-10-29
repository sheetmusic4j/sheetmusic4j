package com.sheetmusic4j.pdf;

import java.util.Optional;
import java.util.Set;

import com.sheetmusic4j.core.io.ScoreFormat;
import com.sheetmusic4j.core.io.ScoreReader;
import com.sheetmusic4j.core.io.ScoreWriter;

/**
 * Engraved PDF (load only) as a {@link ScoreFormat}, registered as a service so
 * {@link com.sheetmusic4j.core.io.ScoreFile} finds it when this module is on the class or module path.
 */
public final class PdfFormat implements ScoreFormat {
    @Override
    public String name() {
        return "Engraved PDF";
    }

    @Override
    public Set<String> extensions() {
        return Set.of("pdf");
    }

    @Override
    public ScoreReader reader() {
        return new PdfScoreReader();
    }

    @Override
    public Optional<ScoreWriter> writer() {
        return Optional.empty();
    }
}
