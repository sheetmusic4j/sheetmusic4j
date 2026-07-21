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

    /**
     * Returns the 1-based measure number.
     *
     * @return the measure number
     */
    public int number() {
        return number;
    }

    /**
     * Returns the attributes if set at this measure.
     *
     * @return the attributes, or empty if not set
     */
    public Optional<Attributes> attributes() {
        return Optional.ofNullable(attributes);
    }

    /**
     * Returns the list of musical elements in this measure.
     *
     * @return the elements list
     */
    public List<MusicElement> elements() {
        return elements;
    }

    /**
     * Creates a new builder for a measure with the given number.
     *
     * @param number the 1-based measure number
     * @return a new builder instance
     */
    public static Builder builder(int number) {
        return new Builder(number);
    }

    /** Builder for constructing {@link Measure} instances. */
    public static final class Builder {
        private final int number;
        private Attributes attributes;
        private final List<MusicElement> elements = new ArrayList<>();

        private Builder(int number) {
            this.number = number;
        }

        /**
         * Sets the attributes for this measure.
         *
         * @param attributes the attributes to set
         * @return this builder
         */
        public Builder attributes(Attributes attributes) {
            this.attributes = attributes;
            return this;
        }

        /**
         * Adds an element to this measure.
         *
         * @param element the element to add
         * @return this builder
         */
        public Builder addElement(MusicElement element) {
            this.elements.add(element);
            return this;
        }

        /**
         * Sets all elements for this measure.
         *
         * @param elements the elements list
         * @return this builder
         */
        public Builder elements(List<MusicElement> elements) {
            this.elements.clear();
            this.elements.addAll(elements);
            return this;
        }

        /**
         * Builds and returns the {@link Measure} instance.
         *
         * @return the constructed Measure
         */
        public Measure build() {
            return new Measure(this);
        }
    }
}
