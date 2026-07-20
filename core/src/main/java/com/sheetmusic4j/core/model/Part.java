package com.sheetmusic4j.core.model;

import java.util.ArrayList;
import java.util.List;

/**
 * A part (instrument/voice) of a score, containing an ordered list of measures.
 */
public final class Part {

    private final String id;
    private final String name;
    private final List<Measure> measures;

    private Part(Builder builder) {
        this.id = builder.id;
        this.name = builder.name;
        this.measures = List.copyOf(builder.measures);
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public List<Measure> measures() {
        return measures;
    }

    public static Builder builder(String id) {
        return new Builder(id);
    }

    public static final class Builder {
        private final String id;
        private String name;
        private final List<Measure> measures = new ArrayList<>();

        private Builder(String id) {
            this.id = id;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder addMeasure(Measure measure) {
            this.measures.add(measure);
            return this;
        }

        public Builder measures(List<Measure> measures) {
            this.measures.clear();
            this.measures.addAll(measures);
            return this;
        }

        public Part build() {
            if (id == null) {
                throw new IllegalStateException("Part requires an id");
            }
            return new Part(this);
        }
    }
}
