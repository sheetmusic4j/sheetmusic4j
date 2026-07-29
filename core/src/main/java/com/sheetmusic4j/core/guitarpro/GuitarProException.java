package com.sheetmusic4j.core.guitarpro;

/**
 * Thrown when a GuitarPro file cannot be imported.
 */
public class GuitarProException extends RuntimeException {

    public GuitarProException(String message) {
        super(message);
    }

    public GuitarProException(String message, Throwable cause) {
        super(message, cause);
    }
}
