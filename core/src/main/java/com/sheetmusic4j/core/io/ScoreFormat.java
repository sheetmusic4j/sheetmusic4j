package com.sheetmusic4j.core.io;

import java.util.Optional;
import java.util.Set;

/**
 * A score file format: the extensions it is recognised by, how to read it,
 * and - when supported - how to write it.
 *
 * <p>{@link ScoreFile} knows the built-in formats; additional formats can be
 * contributed as {@link java.util.ServiceLoader services} of this interface.
 */
public interface ScoreFormat {

    /**
     * Human-readable format name, e.g. {@code "MusicXML"}.
     */
    String name();

    /**
     * Lower-case file extensions without the dot, e.g. {@code "musicxml"}.
     */
    Set<String> extensions();

    /**
     * A new reader for this format.
     */
    ScoreReader reader();

    /**
     * A new writer for this format, or empty when the format is read-only.
     */
    Optional<ScoreWriter> writer();
}
