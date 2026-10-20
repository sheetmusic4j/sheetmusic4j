package com.sheetmusic4j.core.musicxml;

import com.sheetmusic4j.core.io.ScoreFormatException;

/**
 * Thrown when a MusicXML document cannot be read or written.
 */
public class MusicXmlException extends ScoreFormatException {

    public MusicXmlException(String message) {
        super(message);
    }

    public MusicXmlException(String message, Throwable cause) {
        super(message, cause);
    }
}
