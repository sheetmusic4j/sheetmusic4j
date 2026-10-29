package com.sheetmusic4j.pdf.recognize;

import com.sheetmusic4j.core.model.Clef;
import com.sheetmusic4j.core.model.Placement;
import com.sheetmusic4j.core.model.TimeSignature;

/**
 * What is in force at a point of one part: clef, key signature (as fifths) and time signature. Systems repeat the
 * clef and key but not always the time signature, so it carries over from the previous system.
 */
public final class PartState {
    /** The clef in force at the end of the last measure read; the next measure is decoded with it. */
    Clef clef;
    /** The clef the measures read so far have announced in their attributes. */
    Clef shownClef;
    Integer fifths;
    TimeSignature time;
    boolean started;
    /** A tie that runs off the end of the previous system and ends on the first note of the next one. */
    boolean pendingTie;
    /** A slur that does the same; holds its placement. */
    Placement pendingSlur;
}
