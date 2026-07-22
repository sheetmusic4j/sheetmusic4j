package com.sheetmusic4j.core.model;

import java.util.List;

/**
 * A rest occupying a duration with no pitch.
 */
public final class Rest implements MusicElement {

    private final Duration duration;
    private final NoteType type;
    private final int dots;
    private final List<Tuplet> tuplets;
    private final TimeModification timeModification;
    private final int staff;

    private Rest(Builder builder) {
        this.duration = builder.duration;
        this.type = builder.type;
        this.dots = builder.dots;
        this.tuplets = List.copyOf(builder.tuplets);
        this.timeModification = builder.timeModification;
        this.staff = builder.staff;
    }

    @Override
    public Duration duration() {
        return duration;
    }

    public NoteType type() {
        return type;
    }

    public int dots() {
        return dots;
    }

    /**
     * The staff index (1-based) this rest is assigned to within its part.
     * Defaults to {@code 1} for single-staff parts.
     */
    public int staff() {
        return staff;
    }

    /**
     * Tuplet bracket endpoints attached to this rest; empty for rests not
     * part of any tuplet.
     */
    public List<Tuplet> tuplets() {
        return tuplets;
    }

    /**
     * The tuplet ratio (MusicXML {@code <time-modification>}) applied to
     * this rest's written type, when present.
     */
    public java.util.Optional<TimeModification> timeModification() {
        return java.util.Optional.ofNullable(timeModification);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private Duration duration;
        private NoteType type;
        private int dots;
        private java.util.List<Tuplet> tuplets = new java.util.ArrayList<>();
        private TimeModification timeModification;
        private int staff = 1;

        public Builder duration(Duration duration) {
            this.duration = duration;
            return this;
        }

        public Builder type(NoteType type) {
            this.type = type;
            return this;
        }

        public Builder dots(int dots) {
            this.dots = dots;
            return this;
        }

        public Builder addTuplet(Tuplet tuplet) {
            this.tuplets.add(tuplet);
            return this;
        }

        public Builder tuplets(java.util.List<Tuplet> tuplets) {
            this.tuplets = new java.util.ArrayList<>(tuplets);
            return this;
        }

        public Builder timeModification(TimeModification timeModification) {
            this.timeModification = timeModification;
            return this;
        }

        public Builder staff(int staff) {
            this.staff = staff;
            return this;
        }

        public Rest build() {
            if (duration == null) {
                throw new IllegalStateException("Rest requires a duration");
            }
            if (type == null) {
                type = NoteType.fromQuarterValue(duration.inQuarters());
            }
            return new Rest(this);
        }
    }
}
