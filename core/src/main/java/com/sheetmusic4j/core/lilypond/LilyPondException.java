package com.sheetmusic4j.core.lilypond;

/**
 * Thrown when a LilyPond document cannot be read or written.
 */
public class LilyPondException extends RuntimeException {

    public LilyPondException(String message) {
        super(message);
    }

    public LilyPondException(String message, Throwable cause) {
        super(message, cause);
    }
}
