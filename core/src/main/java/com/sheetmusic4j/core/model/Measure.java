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
    private final Barline barline;
    private final boolean leadingRepeatStart;
    private final String ending;
    private final String sectionTitle;
    private final boolean forceSystemBreak;

    private Measure(Builder builder) {
        this.number = builder.number;
        this.attributes = builder.attributes;
        this.elements = List.copyOf(builder.elements);
        this.barline = builder.barline;
        this.leadingRepeatStart = builder.leadingRepeatStart;
        this.ending = builder.ending;
        this.sectionTitle = builder.sectionTitle;
        this.forceSystemBreak = builder.forceSystemBreak;
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
     * The barline drawn at the end of this measure (style/repeat), or empty
     * for a plain single thin line.
     *
     * @return the barline, or empty if not set
     */
    public Optional<Barline> barline() {
        return Optional.ofNullable(barline);
    }

    /**
     * Whether a repeat-start mark ({@code |:}) should be drawn at this
     * measure's left edge. Used when the repeat opens with nothing to
     * attach trailing dots to on a preceding measure (e.g. the very first
     * measure of a part, or right after a measure with no notes of its
     * own).
     *
     * @return {@code true} if a leading repeat-start mark should be drawn
     */
    public boolean leadingRepeatStart() {
        return leadingRepeatStart;
    }

    /**
     * The first/second-ending ("volta") label active for this measure (e.g.
     * {@code "1"}, {@code "2"}), or empty if this measure is not part of an
     * ending. Consecutive measures sharing the same label are grouped into
     * one bracket by the engraver.
     *
     * @return the ending label, or empty if not set
     */
    public Optional<String> ending() {
        return Optional.ofNullable(ending);
    }

    /**
     * A section title (ABC mid-tune {@code T:} field) to display above the
     * system this measure starts, or empty if this measure starts no new
     * section.
     *
     * @return the section title, or empty if not set
     */
    public Optional<String> sectionTitle() {
        return Optional.ofNullable(sectionTitle);
    }

    /**
     * Whether the engraver must start a new system at this measure rather
     * than packing it onto the end of the previous row.
     *
     * @return {@code true} if this measure must begin a new system
     */
    public boolean forceSystemBreak() {
        return forceSystemBreak;
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
        private Barline barline;
        private boolean leadingRepeatStart;
        private String ending;
        private String sectionTitle;
        private boolean forceSystemBreak;

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
         * Sets the barline drawn at the end of this measure.
         *
         * @param barline the barline to set
         * @return this builder
         */
        public Builder barline(Barline barline) {
            this.barline = barline;
            return this;
        }

        /**
         * Marks this measure as starting with a leading repeat-start mark
         * ({@code |:}) at its left edge.
         *
         * @param leadingRepeatStart whether to draw a leading repeat-start mark
         * @return this builder
         */
        public Builder leadingRepeatStart(boolean leadingRepeatStart) {
            this.leadingRepeatStart = leadingRepeatStart;
            return this;
        }

        /**
         * Sets the first/second-ending label active for this measure.
         *
         * @param ending the ending label, or {@code null} for none
         * @return this builder
         */
        public Builder ending(String ending) {
            this.ending = ending;
            return this;
        }

        /**
         * Sets the section title to display above the system this measure starts.
         *
         * @param sectionTitle the section title, or {@code null} for none
         * @return this builder
         */
        public Builder sectionTitle(String sectionTitle) {
            this.sectionTitle = sectionTitle;
            return this;
        }

        /**
         * Marks this measure as requiring a new system (rather than being
         * packed onto the end of the previous row).
         *
         * @param forceSystemBreak whether to force a system break at this measure
         * @return this builder
         */
        public Builder forceSystemBreak(boolean forceSystemBreak) {
            this.forceSystemBreak = forceSystemBreak;
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
