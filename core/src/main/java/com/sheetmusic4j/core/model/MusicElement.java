package com.sheetmusic4j.core.model;

/**
 * A time-consuming (or chord-grouping) element within a measure.
 * Sealed to the concrete kinds understood by the model.
 *
 * <p><b>Identity contract:</b> {@link Note}, {@link Rest} and {@link Chord}
 * deliberately keep {@link Object#equals(Object) identity equality}. Layout
 * results reference the element each placement was produced from, and
 * viewers key their highlight maps by element, so two notes with the same
 * pitch and duration must stay distinguishable. Consequently those
 * references are only valid for the exact {@code Score} instance that was
 * laid out; reloading a file yields new elements.
 */
public sealed interface MusicElement permits Note, Rest, Chord, Direction, Harmony {

    /**
     * The duration this element occupies on the timeline.
     */
    Duration duration();
}
