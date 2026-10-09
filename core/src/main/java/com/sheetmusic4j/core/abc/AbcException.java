package com.sheetmusic4j.core.abc;

import com.sheetmusic4j.core.io.ScoreFormatException;

/**
 * Thrown when an ABC document cannot be read or written.
 */
public class AbcException extends ScoreFormatException {

    public AbcException(String message) {
        super(message);
    }

    public AbcException(String message, Throwable cause) {
        super(message, cause);
    }
}
