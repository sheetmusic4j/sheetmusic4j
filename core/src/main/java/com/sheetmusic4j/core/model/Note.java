package com.sheetmusic4j.core.model;

/**
 * A single pitched note.
 */
public final class Note implements MusicElement {

    private final Pitch pitch;
    private final Duration duration;
    private final NoteType type;
    private final int dots;
    private final boolean tieStart;
    private final boolean tieStop;

    private Note(Builder builder) {
        this.pitch = builder.pitch;
        this.duration = builder.duration;
        this.type = builder.type;
        this.dots = builder.dots;
        this.tieStart = builder.tieStart;
        this.tieStop = builder.tieStop;
    }

    public Pitch pitch() {
        return pitch;
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

    public boolean tieStart() {
        return tieStart;
    }

    public boolean tieStop() {
        return tieStop;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private Pitch pitch;
        private Duration duration;
        private NoteType type;
        private int dots;
        private boolean tieStart;
        private boolean tieStop;

        public Builder pitch(Pitch pitch) {
            this.pitch = pitch;
            return this;
        }

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

        public Builder tieStart(boolean tieStart) {
            this.tieStart = tieStart;
            return this;
        }

        public Builder tieStop(boolean tieStop) {
            this.tieStop = tieStop;
            return this;
        }

        public Note build() {
            if (pitch == null) {
                throw new IllegalStateException("Note requires a pitch");
            }
            if (duration == null) {
                throw new IllegalStateException("Note requires a duration");
            }
            if (type == null) {
                type = NoteType.fromQuarterValue(duration.inQuarters());
            }
            return new Note(this);
        }
    }
}
