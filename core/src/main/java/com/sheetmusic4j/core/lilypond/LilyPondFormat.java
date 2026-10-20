package com.sheetmusic4j.core.lilypond;

import java.util.Optional;
import java.util.Set;

import com.sheetmusic4j.core.io.ScoreFormat;
import com.sheetmusic4j.core.io.ScoreReader;
import com.sheetmusic4j.core.io.ScoreWriter;

/**
 * LilyPond as a {@link ScoreFormat}, registered as a service so
 * {@link com.sheetmusic4j.core.io.ScoreFile} finds it.
 */
public final class LilyPondFormat implements ScoreFormat {

    @Override
    public String name() {
        return "LilyPond";
    }

    @Override
    public Set<String> extensions() {
        return Set.of("ly");
    }

    @Override
    public ScoreReader reader() {
        return new LilyPondReader();
    }

    @Override
    public Optional<ScoreWriter> writer() {
        return Optional.of(new LilyPondWriter());
    }
}
