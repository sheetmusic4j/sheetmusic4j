package com.sheetmusic4j.core.abc;

import java.util.Optional;
import java.util.Set;

import com.sheetmusic4j.core.io.ScoreFormat;
import com.sheetmusic4j.core.io.ScoreReader;
import com.sheetmusic4j.core.io.ScoreWriter;

/**
 * ABC as a {@link ScoreFormat}, registered as a service so
 * {@link com.sheetmusic4j.core.io.ScoreFile} finds it.
 */
public final class AbcFormat implements ScoreFormat {

    @Override
    public String name() {
        return "ABC";
    }

    @Override
    public Set<String> extensions() {
        return Set.of("abc");
    }

    @Override
    public ScoreReader reader() {
        return new AbcReader();
    }

    @Override
    public Optional<ScoreWriter> writer() {
        return Optional.of(new AbcWriter());
    }
}
