package com.sheetmusic4j.core.model;

/**
 * MusicXML {@code <time-modification>}: the tuplet ratio applied to a note
 * or rest's written type. The sounding {@link Duration} on the element is
 * already tuplet-adjusted (MusicXML always encodes the actual, sounding
 * duration), so this record exists purely to drive the displayed tuplet
 * number (typically {@link #actualNotes()}, e.g. "6" for a sextuplet).
 *
 * @param actualNotes the number of notes actually played in the time of
 *                    {@link #normalNotes()}
 * @param normalNotes the number of notes normally occupying that duration
 */
public record TimeModification(int actualNotes, int normalNotes) {

    public TimeModification {
        if (actualNotes < 1) {
            throw new IllegalArgumentException("actualNotes must be >= 1, got " + actualNotes);
        }
        if (normalNotes < 1) {
            throw new IllegalArgumentException("normalNotes must be >= 1, got " + normalNotes);
        }
    }
}
