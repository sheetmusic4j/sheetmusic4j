package com.sheetmusic4j.core.io;

/**
 * Base class for every failure to read or write a score file, whatever the
 * format. Format-specific subclasses ({@code MusicXmlException},
 * {@code MidiException}, ...) add nothing but their type, so callers that
 * don't care about the format can catch this one.
 */
public class ScoreFormatException extends RuntimeException {

    public ScoreFormatException(String message) {
        super(message);
    }

    public ScoreFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}
