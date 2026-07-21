package com.sheetmusic4j.core.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A measure (bar) within a part. It may carry {@link Attributes} that take effect
 * at its start and holds an ordered list of {@link MusicElement}s.
 */
public final class Measure {

    private final int number;
    private final Attributes attributes;
    private final List<MusicElement> elements;

    private Measure(Builder builder) {
        this.number = builder.number;
        this.attributes = builder.attributes;
        this.elements = List.copyOf(builder.elements);
    }

    /** Returns the 1-based measure number. */
    public int number() {
        return number;
    }

    /** Returns the attributes if set at this measure. */
    public Optional<Attributes> attributes() {
        return Optional.ofNullable(attributes);
    }

    /** Returns the list of musical elements in this measure. */
    public List<MusicElement> elements() {
        return elements;
    }

    /** Creates a new builder for a measure with the given number. */
    public static Builder builder(int number) {
        return new Builder(number);
    }

    /** Builder for constructing {@link Measure} instances. */
    /** Builder for constructing {@link Measure} instances. */
    public static final class Builder {
        private final int number;
        private Attributes attributes;
        private final List<MusicElement> elements = new ArrayList<>();

        private Builder(int number) {
            this.number = number;
        }

        /** Sets the attributes for this measure. */
        public Builder attributes(Attributes attributes) {
            this.attributes = attributes;
            return this;
        }

        /** Adds an element to this measure. */
        public Builder addElement(MusicElement element) {
            this.elements.add(element);
            return this;
        }

        /** Sets all elements for this measure. */
        public Builder elements(List<MusicElement> elements) {
            this.elements.clear();
            this.elements.addAll(elements);
            return this;
        }

        /** Builds and returns the {@link Measure} instance. */
        public Measure build() {
            return new Measure(this);
        }
    }
}
