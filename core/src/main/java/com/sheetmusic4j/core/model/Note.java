package com.sheetmusic4j.core.model;

import java.util.List;
import java.util.Optional;

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
    private final Accidental displayedAccidental;
    private final List<Beam> beams;
    private final int staff;

    private Note(Builder builder) {
        this.pitch = builder.pitch;
        this.duration = builder.duration;
        this.type = builder.type;
        this.dots = builder.dots;
        this.tieStart = builder.tieStart;
        this.tieStop = builder.tieStop;
        this.displayedAccidental = builder.displayedAccidental;
        this.beams = List.copyOf(builder.beams);
        this.staff = builder.staff;
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

    /**
     * The explicit accidental to display for this note occurrence, when the
     * MusicXML {@code <accidental>} element is present. Falls back to a
     * derivation from {@link Pitch#alter()} at rendering time when absent.
     */
    public Optional<Accidental> displayedAccidental() {
        return Optional.ofNullable(displayedAccidental);
    }

    /**
     * Beam entries attached to this note; empty for notes that are not part
     * of any beamed group.
     */
    public List<Beam> beams() {
        return beams;
    }

    /**
     * Whether this note participates in a beam of the given level.
     */
    public boolean isBeamed() {
        return !beams.isEmpty();
    }

    /**
     * The staff index (1-based) this note is assigned to within its part.
     * Defaults to {@code 1} for single-staff parts.
     */
    public int staff() {
        return staff;
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
        private Accidental displayedAccidental;
        private java.util.List<Beam> beams = new java.util.ArrayList<>();
        private int staff = 1;

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

        public Builder displayedAccidental(Accidental displayedAccidental) {
            this.displayedAccidental = displayedAccidental;
            return this;
        }

        public Builder addBeam(Beam beam) {
            this.beams.add(beam);
            return this;
        }

        public Builder beams(java.util.List<Beam> beams) {
            this.beams = new java.util.ArrayList<>(beams);
            return this;
        }

        public Builder staff(int staff) {
            this.staff = staff;
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
