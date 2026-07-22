package com.sheetmusic4j.core.model;

/**
 * A slur endpoint attached to a {@link Note} (MusicXML
 * {@code <notations><slur number="N" type="start|stop"/>}). A {@code number}
 * disambiguates concurrent/nested slurs the same way {@link Beam#number()}
 * disambiguates beam levels.
 *
 * @param number 1-based slur id matching a start to its stop
 * @param type   whether this note begins or ends the slur
 */
public record Slur(int number, Type type) {

    public Slur {
        if (number < 1) {
            throw new IllegalArgumentException("slur number must be >= 1, got " + number);
        }
        if (type == null) {
            throw new IllegalArgumentException("type must not be null");
        }
    }

    public enum Type {
        START,
        STOP
    }
}
