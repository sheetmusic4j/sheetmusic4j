package com.sheetmusic4j.pdf.recognize;

import com.sheetmusic4j.core.model.Clef;
import com.sheetmusic4j.core.model.TimeSignature;

/**
 * What is in force at a point of one part: clef, key signature (as fifths) and time signature. Systems repeat the
 * clef and key but not always the time signature, so it carries over from the previous system.
 */
public final class PartState {
    Clef clef;
    Integer fifths;
    TimeSignature time;
    boolean started;
}
