package com.sheetmusic4j.core.model;

/**
 * A time-consuming (or chord-grouping) element within a measure.
 * Sealed to the concrete kinds understood by the model.
 */
public sealed interface MusicElement permits Note, Rest, Chord {

    /**
     * The duration this element occupies on the timeline.
     */
    Duration duration();
}
