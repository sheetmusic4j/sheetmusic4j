package com.sheetmusic4j.core.model;

/**
 * A rest occupying a duration with no pitch.
 */
public final class Rest implements MusicElement {

    private final Duration duration;
    private final NoteType type;
    private final int dots;

    private Rest(Builder builder) {
        this.duration = builder.duration;
        this.type = builder.type;
        this.dots = builder.dots;
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

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private Duration duration;
        private NoteType type;
        private int dots;

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
