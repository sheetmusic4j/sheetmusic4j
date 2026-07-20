package com.sheetmusic4j.core.midi;

/**
 * Thrown when a MIDI file cannot be imported or exported.
 */
public class MidiException extends RuntimeException {

    public MidiException(String message) {
        super(message);
    }

    public MidiException(String message, Throwable cause) {
        super(message, cause);
    }
}
