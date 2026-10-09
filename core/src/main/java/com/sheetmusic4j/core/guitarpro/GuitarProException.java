package com.sheetmusic4j.core.guitarpro;

import com.sheetmusic4j.core.io.ScoreFormatException;

/**
 * Thrown when a GuitarPro file cannot be imported.
 */
public class GuitarProException extends ScoreFormatException {

    public GuitarProException(String message) {
        super(message);
    }

    public GuitarProException(String message, Throwable cause) {
        super(message, cause);
    }
}
