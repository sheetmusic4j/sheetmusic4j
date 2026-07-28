package com.sheetmusic4j.core.abc;

/**
 * Thrown when an ABC document cannot be read or written.
 */
public class AbcException extends RuntimeException {

    public AbcException(String message) {
        super(message);
    }

    public AbcException(String message, Throwable cause) {
        super(message, cause);
    }
}
