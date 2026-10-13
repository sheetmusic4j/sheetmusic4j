package com.sheetmusic4j.core.midi;

import java.util.Optional;
import java.util.Set;

import com.sheetmusic4j.core.io.ScoreFormat;
import com.sheetmusic4j.core.io.ScoreReader;
import com.sheetmusic4j.core.io.ScoreWriter;

/**
 * MIDI as a {@link ScoreFormat}, registered as a service so
 * {@link com.sheetmusic4j.core.io.ScoreFile} finds it.
 */
public final class MidiFormat implements ScoreFormat {

    @Override
    public String name() {
        return "MIDI";
    }

    @Override
    public Set<String> extensions() {
        return Set.of("mid", "midi");
    }

    @Override
    public ScoreReader reader() {
        return new MidiImporter();
    }

    @Override
    public Optional<ScoreWriter> writer() {
        return Optional.of(new MidiExporter());
    }
}
