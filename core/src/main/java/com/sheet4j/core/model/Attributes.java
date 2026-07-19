package com.sheet4j.core.model;

import java.util.Optional;

/**
 * Musical attributes that apply from a point in a measure onward:
 * divisions (resolution), key signature, time signature, and clef.
 * Any field may be absent (inherited from a previous measure).
 */
public final class Attributes {

    private final Integer divisions;
    private final KeySignature keySignature;
    private final TimeSignature timeSignature;
    private final Clef clef;

    private Attributes(Builder builder) {
        this.divisions = builder.divisions;
        this.keySignature = builder.keySignature;
        this.timeSignature = builder.timeSignature;
        this.clef = builder.clef;
    }

    public Optional<Integer> divisions() {
        return Optional.ofNullable(divisions);
    }

    public Optional<KeySignature> keySignature() {
        return Optional.ofNullable(keySignature);
    }

    public Optional<TimeSignature> timeSignature() {
        return Optional.ofNullable(timeSignature);
    }

    public Optional<Clef> clef() {
        return Optional.ofNullable(clef);
    }

    public boolean isEmpty() {
        return divisions == null && keySignature == null && timeSignature == null && clef == null;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private Integer divisions;
        private KeySignature keySignature;
        private TimeSignature timeSignature;
        private Clef clef;

        public Builder divisions(Integer divisions) {
            this.divisions = divisions;
            return this;
        }

        public Builder keySignature(KeySignature keySignature) {
            this.keySignature = keySignature;
            return this;
        }

        public Builder timeSignature(TimeSignature timeSignature) {
            this.timeSignature = timeSignature;
            return this;
        }

        public Builder clef(Clef clef) {
            this.clef = clef;
            return this;
        }

        public Attributes build() {
            return new Attributes(this);
        }
    }
}
