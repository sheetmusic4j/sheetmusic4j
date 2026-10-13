package com.sheetmusic4j.core.guitarpro;

import java.util.Optional;
import java.util.Set;

import com.sheetmusic4j.core.io.ScoreFormat;
import com.sheetmusic4j.core.io.ScoreReader;
import com.sheetmusic4j.core.io.ScoreWriter;

/**
 * Guitar Pro 8 (load only) as a {@link ScoreFormat}, registered as a service so
 * {@link com.sheetmusic4j.core.io.ScoreFile} finds it.
 */
public final class GuitarProFormat implements ScoreFormat {

    @Override
    public String name() {
        return "Guitar Pro";
    }

    @Override
    public Set<String> extensions() {
        return Set.of("gp");
    }

    @Override
    public ScoreReader reader() {
        return new GuitarProImporter();
    }

    @Override
    public Optional<ScoreWriter> writer() {
        return Optional.empty();
    }
}
