package com.sheetmusic4j.core.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PitchTest {

    @Test
    void middleCIsMidi60() {
        assertEquals(60, new Pitch(Step.C, 4).toMidiNumber());
    }

    @Test
    void a4IsMidi69() {
        assertEquals(69, new Pitch(Step.A, 4).toMidiNumber());
    }

    @Test
    void sharpRaisesBySemitone() {
        assertEquals(61, new Pitch(Step.C, 4, 1).toMidiNumber());
    }

    @Test
    void roundTripThroughMidi() {
        for (int midi = 21; midi <= 108; midi++) {
            assertEquals(midi, Pitch.fromMidiNumber(midi).toMidiNumber());
        }
    }
}
