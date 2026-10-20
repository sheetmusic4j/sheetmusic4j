package com.sheetmusic4j.core.model;

import java.util.List;

/**
 * A chord: several notes sounding simultaneously. All notes share the chord's
 * duration (the duration of its first note).
 */
public final class Chord implements MusicElement {

    private final List<Note> notes;

    public Chord(List<Note> notes) {
        if (notes == null || notes.isEmpty()) {
            throw new IllegalArgumentException("Chord requires at least one note");
        }
        this.notes = List.copyOf(notes);
    }

    public List<Note> notes() {
        return notes;
    }

    /**
     * The voice of the chord - that of its first note.
     */
    public int voice() {
        return notes.get(0).voice();
    }

    /**
     * The staff of the chord - that of its first note.
     */
    public int staff() {
        return notes.get(0).staff();
    }

    @Override
    public Duration duration() {
        return notes.get(0).duration();
    }
}
