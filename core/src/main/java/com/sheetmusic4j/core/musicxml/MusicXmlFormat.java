package com.sheetmusic4j.core.musicxml;

import java.util.Optional;
import java.util.Set;

import com.sheetmusic4j.core.io.ScoreFormat;
import com.sheetmusic4j.core.io.ScoreReader;
import com.sheetmusic4j.core.io.ScoreWriter;

/**
 * MusicXML as a {@link ScoreFormat}, registered as a service so
 * {@link com.sheetmusic4j.core.io.ScoreFile} finds it.
 */
public final class MusicXmlFormat implements ScoreFormat {

    @Override
    public String name() {
        return "MusicXML";
    }

    @Override
    public Set<String> extensions() {
        return Set.of("musicxml", "xml", "mxl");
    }

    @Override
    public ScoreReader reader() {
        return new MusicXmlReader();
    }

    @Override
    public Optional<ScoreWriter> writer() {
        return Optional.of(new MusicXmlWriter());
    }
}
