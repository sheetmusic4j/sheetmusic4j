package com.sheetmusic4j.core.model;

import java.util.List;
import java.util.Optional;

/**
 * Musical attributes that apply from a point in a measure onward:
 * divisions (resolution), key signature, time signature, and clef(s).
 * Any field may be absent (inherited from a previous measure).
 */
public final class Attributes {

    private final Integer divisions;
    private final KeySignature keySignature;
    private final TimeSignature timeSignature;
    private final Clef clef;
    private final List<Clef> clefs;
    private final Integer staves;

    private Attributes(Builder builder) {
        this.divisions = builder.divisions;
        this.keySignature = builder.keySignature;
        this.timeSignature = builder.timeSignature;
        this.clef = builder.clef;
        this.clefs = List.copyOf(builder.clefs);
        this.staves = builder.staves;
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

    /**
     * All clefs declared on this attribute change, indexed by their
     * {@code number} attribute. When a multi-staff part is being defined, one
     * entry per staff is emitted here; when the part has a single staff, the
     * list contains at most one clef (also returned by {@link #clef()}).
     */
    public List<Clef> clefs() {
        return clefs;
    }

    /**
     * Number of staves the part has from this point onward. Absent when the
     * default (one staff) applies.
     */
    public Optional<Integer> staves() {
        return Optional.ofNullable(staves);
    }

    public boolean isEmpty() {
        return divisions == null && keySignature == null && timeSignature == null
                && clef == null && clefs.isEmpty() && staves == null;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private Integer divisions;
        private KeySignature keySignature;
        private TimeSignature timeSignature;
        private Clef clef;
        private java.util.List<Clef> clefs = new java.util.ArrayList<>();
        private Integer staves;

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

        public Builder addClef(Clef clef) {
            this.clefs.add(clef);
            if (this.clef == null) {
                this.clef = clef;
            }
            return this;
        }

        public Builder clefs(java.util.List<Clef> clefs) {
            this.clefs = new java.util.ArrayList<>(clefs);
            if (!this.clefs.isEmpty() && this.clef == null) {
                this.clef = this.clefs.get(0);
            }
            return this;
        }

        public Builder staves(Integer staves) {
            this.staves = staves;
            return this;
        }

        public Attributes build() {
            if (clefs.isEmpty() && clef != null) {
                clefs.add(clef);
            }
            return new Attributes(this);
        }
    }
}
