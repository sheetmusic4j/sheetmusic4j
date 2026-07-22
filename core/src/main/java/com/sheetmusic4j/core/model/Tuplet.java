package com.sheetmusic4j.core.model;

/**
 * A tuplet bracket endpoint attached to a {@link Note} or {@link Rest}
 * (MusicXML {@code <notations><tuplet number="N" type="start|stop"
 * bracket="yes|no"/>}).
 *
 * @param number  1-based tuplet id matching a start to its stop
 * @param type    whether this element begins or ends the tuplet
 * @param bracket whether a bracket line should be drawn (MusicXML defaults
 *                this to {@code true} when omitted on an unbeamed group;
 *                beamed tuplets commonly set it to {@code false} and show
 *                only the number)
 */
public record Tuplet(int number, Type type, boolean bracket) {

    public Tuplet {
        if (number < 1) {
            throw new IllegalArgumentException("tuplet number must be >= 1, got " + number);
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
