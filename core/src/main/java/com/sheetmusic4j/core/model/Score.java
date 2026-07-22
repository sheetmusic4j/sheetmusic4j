package com.sheetmusic4j.core.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The root of the music model: work/movement metadata and a list of parts.
 */
public final class Score {

    private final String workTitle;
    private final String movementTitle;
    private final List<Creator> creators;
    private final List<Part> parts;
    private final List<PartGroup> partGroups;

    private Score(Builder builder) {
        this.workTitle = builder.workTitle;
        this.movementTitle = builder.movementTitle;
        this.creators = List.copyOf(builder.creators);
        this.parts = List.copyOf(builder.parts);
        this.partGroups = List.copyOf(builder.partGroups);
    }

    public Optional<String> workTitle() {
        return Optional.ofNullable(workTitle);
    }

    public Optional<String> movementTitle() {
        return Optional.ofNullable(movementTitle);
    }

    public List<Creator> creators() {
        return creators;
    }

    public List<Part> parts() {
        return parts;
    }

    /**
     * Immutable, document-order list of {@link PartGroup part groups}
     * declared inside the source {@code <part-list>}. Outer groups appear
     * before nested inner groups.
     *
     * @return the (possibly empty) list of part groups
     */
    public List<PartGroup> partGroups() {
        return partGroups;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String workTitle;
        private String movementTitle;
        private final List<Creator> creators = new ArrayList<>();
        private final List<Part> parts = new ArrayList<>();
        private final List<PartGroup> partGroups = new ArrayList<>();

        public Builder workTitle(String workTitle) {
            this.workTitle = workTitle;
            return this;
        }

        public Builder movementTitle(String movementTitle) {
            this.movementTitle = movementTitle;
            return this;
        }

        public Builder addCreator(Creator creator) {
            if (creator != null) {
                this.creators.add(creator);
            }
            return this;
        }

        public Builder creators(List<Creator> creators) {
            this.creators.clear();
            for (Creator creator : creators) {
                if (creator != null) {
                    this.creators.add(creator);
                }
            }
            return this;
        }

        /**
         * Whether this builder already has a creator with the given role
         * (compared case-insensitively). Used by the reader to prefer the
         * canonical {@code <identification>} source over {@code <credit>}
         * fallbacks for the same role.
         */
        public boolean hasCreatorRole(String role) {
            if (role == null) {
                return false;
            }
            String normalized = role.trim().toLowerCase(Locale.ROOT);
            for (Creator c : creators) {
                if (c.role().equals(normalized)) {
                    return true;
                }
            }
            return false;
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

        /**
         * Append a {@link PartGroup} to the score. Groups should be added in
         * document order so that outer groups precede their nested inner
         * groups; the engraver relies on this ordering when laying out
         * multiple bracket columns.
         *
         * @param group the group to add (silently ignored when {@code null})
         * @return this builder for chaining
         */
        public Builder addPartGroup(PartGroup group) {
            if (group != null) {
                this.partGroups.add(group);
            }
            return this;
        }

        public Score build() {
            return new Score(this);
        }
    }
}
