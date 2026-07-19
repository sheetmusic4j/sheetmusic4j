package com.sheet4j.core.musicxml;

/**
 * Thrown when a MusicXML document cannot be read or written.
 */
public class MusicXmlException extends RuntimeException {

    public MusicXmlException(String message) {
        super(message);
    }

    public MusicXmlException(String message, Throwable cause) {
        super(message, cause);
    }
}
