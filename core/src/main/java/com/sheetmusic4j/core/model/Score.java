package com.sheetmusic4j.core.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The root of the music model: work/movement metadata and a list of parts.
 */
public final class Score {

    private final String workTitle;
    private final String movementTitle;
    private final List<Part> parts;

    private Score(Builder builder) {
        this.workTitle = builder.workTitle;
        this.movementTitle = builder.movementTitle;
        this.parts = List.copyOf(builder.parts);
    }

    public Optional<String> workTitle() {
        return Optional.ofNullable(workTitle);
    }

    public Optional<String> movementTitle() {
        return Optional.ofNullable(movementTitle);
    }

    public List<Part> parts() {
        return parts;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String workTitle;
        private String movementTitle;
        private final List<Part> parts = new ArrayList<>();

        public Builder workTitle(String workTitle) {
            this.workTitle = workTitle;
            return this;
        }

        public Builder movementTitle(String movementTitle) {
            this.movementTitle = movementTitle;
            return this;
        }

        public Builder addPart(Part part) {
            this.parts.add(part);
            return this;
        }

        public Builder parts(List<Part> parts) {
            this.parts.clear();
            this.parts.addAll(parts);
            return this;
        }

        public Score build() {
            return new Score(this);
        }
    }
}
