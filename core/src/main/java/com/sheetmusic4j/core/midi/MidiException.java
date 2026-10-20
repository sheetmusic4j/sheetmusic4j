package com.sheetmusic4j.core.midi;

import com.sheetmusic4j.core.io.ScoreFormatException;

/**
 * Thrown when a MIDI file cannot be imported or exported.
 */
public class MidiException extends ScoreFormatException {

    public MidiException(String message) {
        super(message);
    }

    public MidiException(String message, Throwable cause) {
        super(message, cause);
    }
}
