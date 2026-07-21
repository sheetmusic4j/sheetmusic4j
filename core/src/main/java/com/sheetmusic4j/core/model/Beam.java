package com.sheetmusic4j.core.model;

/**
 * A beam entry attached to a {@link Note}. Corresponds to a single MusicXML
 * {@code <beam number="N">STATE</beam>} element.
 *
 * <p>Notes participate in a beamed group via one or more {@code Beam} entries.
 * Number {@code 1} is the primary beam (eighth), {@code 2} the second beam
 * (sixteenth), etc. The {@link State} tells the engraver whether this note
 * begins, continues, or ends a beam of that level.
 *
 * @param number 1-based beam level (1 = primary, 2 = sixteenth, ...)
 * @param state  where this note sits in the beamed group
 */
public record Beam(int number, State state) {

    public Beam {
        if (number < 1) {
            throw new IllegalArgumentException("beam number must be >= 1, got " + number);
        }
        if (state == null) {
            throw new IllegalArgumentException("state must not be null");
        }
    }

    /**
     * MusicXML beam states.
     */
    public enum State {
        BEGIN("begin"),
        CONTINUE("continue"),
        END("end"),
        FORWARD_HOOK("forward hook"),
        BACKWARD_HOOK("backward hook");

        private final String xmlValue;

        State(String xmlValue) {
            this.xmlValue = xmlValue;
        }

        public String xmlValue() {
            return xmlValue;
        }

        /**
         * Parse a MusicXML beam state string. Accepts both hyphenated and
         * space-separated hook variants for robustness.
         *
         * @param value the raw text content of a MusicXML {@code <beam>} element
         * @return the matching state, or {@link #CONTINUE} as a lenient default
         */
        public static State fromXml(String value) {
            if (value == null) {
                return CONTINUE;
            }
            String v = value.trim().toLowerCase().replace('-', ' ');
            for (State s : values()) {
                if (s.xmlValue.equals(v)) {
                    return s;
                }
            }
            return CONTINUE;
        }
    }
}
