package com.sheetmusic4j.core.lilypond;

import com.sheetmusic4j.core.io.ScoreFormatException;

/**
 * Thrown when a LilyPond document cannot be read or written.
 */
public class LilyPondException extends ScoreFormatException {

    public LilyPondException(String message) {
        super(message);
    }

    public LilyPondException(String message, Throwable cause) {
        super(message, cause);
    }
}
