package com.sheet4j.core.model;

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

    public int number() {
        return number;
    }

    public Optional<Attributes> attributes() {
        return Optional.ofNullable(attributes);
    }

    public List<MusicElement> elements() {
        return elements;
    }

    public static Builder builder(int number) {
        return new Builder(number);
    }

    public static final class Builder {
        private final int number;
        private Attributes attributes;
        private final List<MusicElement> elements = new ArrayList<>();

        private Builder(int number) {
            this.number = number;
        }

        public Builder attributes(Attributes attributes) {
            this.attributes = attributes;
            return this;
        }

        public Builder addElement(MusicElement element) {
            this.elements.add(element);
            return this;
        }

        public Builder elements(List<MusicElement> elements) {
            this.elements.clear();
            this.elements.addAll(elements);
            return this;
        }

        public Measure build() {
            return new Measure(this);
        }
    }
}
