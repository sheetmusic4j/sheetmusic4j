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
 * @param actualNotes the count this bracket shows (e.g. 5 for a quintuplet),
 *                or 0 to use the note's {@link TimeModification}; nested
 *                tuplets need their own, as the note's modification is the
 *                product of all of them
 * @param normalNotes the notes of normal length it replaces, or 0 when unknown
 * @param placement   above or below the notes; {@link Placement#DEFAULT} puts
 *                    it on the stem side, as beams are
 */
public record Tuplet(int number, Type type, boolean bracket, int actualNotes, int normalNotes,
                     Placement placement) {

    /** A tuplet endpoint whose count comes from the note's time modification. */
    public Tuplet(int number, Type type, boolean bracket) {
        this(number, type, bracket, 0, 0, Placement.DEFAULT);
    }

    public Tuplet {
        if (number < 1) {
            throw new IllegalArgumentException("tuplet number must be >= 1, got " + number);
        }
        if (type == null) {
            throw new IllegalArgumentException("type must not be null");
        }
        if (placement == null) {
            placement = Placement.DEFAULT;
        }
    }

    public enum Type {
        START,
        STOP
    }
}
