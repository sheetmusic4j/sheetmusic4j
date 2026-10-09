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
    private final int voice;
    private final boolean visible;

    private Rest(Builder builder) {
        this.duration = builder.duration;
        this.type = builder.type;
        this.dots = builder.dots;
        this.tuplets = List.copyOf(builder.tuplets);
        this.timeModification = builder.timeModification;
        this.staff = builder.staff;
        this.voice = builder.voice;
        this.visible = builder.visible;
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
     * The voice (1-based) this rest belongs to within its staff, see
     * {@link Note#voice()}. Defaults to {@code 1}.
     */
    public int voice() {
        return voice;
    }

    /**
     * Whether the rest is printed. An invisible rest (MusicXML
     * {@code print-object="no"} or {@code <forward>}, ABC {@code x},
     * LilyPond {@code s}) only advances its voice's time - it fills the gap
     * before a voice that starts mid-measure. Defaults to {@code true}.
     */
    public boolean visible() {
        return visible;
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
        private int voice = 1;
        private boolean visible = true;

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

        public Builder voice(int voice) {
            this.voice = voice;
            return this;
        }

        public Builder visible(boolean visible) {
            this.visible = visible;
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
