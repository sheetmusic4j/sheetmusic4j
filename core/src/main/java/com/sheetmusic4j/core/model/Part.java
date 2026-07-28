package com.sheetmusic4j.core.model;

import java.util.ArrayList;
import java.util.List;

/**
 * A part (instrument/voice) of a score, containing an ordered list of measures.
 *
 * <p>Carries the two label strings MusicXML source files typically expose:
 * <ul>
 *   <li>{@link #name()} — full instrument name (e.g. {@code "Bass Clarinet in B\u266D"});
 *   the engraver prints this to the left of the first system.</li>
 *   <li>{@link #abbreviation()} — short label (e.g. {@code "B. Cl."}) used on
 *   continuation systems.</li>
 * </ul>
 * Both are nullable: scores that carry no {@code <part-name>} keep the field
 * as {@code null}, and the engraver silently skips their label emission.
 */
public final class Part {

    private final String id;
    private final String name;
    private final String abbreviation;
    private final List<Measure> measures;
    private final List<String> postTuneText;

    private Part(Builder builder) {
        this.id = builder.id;
        this.name = builder.name;
        this.abbreviation = builder.abbreviation;
        this.measures = List.copyOf(builder.measures);
        this.postTuneText = List.copyOf(builder.postTuneText);
    }

    /**
     * Stable identifier used in MusicXML {@code <part id>} references.
     *
     * @return the non-null part id
     */
    public String id() {
        return id;
    }

    /**
     * Full part name from {@code <part-name>}, or {@code null} if the source
     * did not carry one.
     *
     * @return the full name, or {@code null}
     */
    public String name() {
        return name;
    }

    /**
     * Short part label from {@code <part-abbreviation>}, or {@code null} if
     * the source did not carry one. The engraver uses this on continuation
     * systems (the second and subsequent rows of a wide score) so the label
     * area stays narrow.
     *
     * @return the abbreviation, or {@code null}
     */
    public String abbreviation() {
        return abbreviation;
    }

    /**
     * Ordered list of measures belonging to this part.
     *
     * @return an immutable view of the measures
     */
    public List<Measure> measures() {
        return measures;
    }

    /**
     * Free-form text lines carried alongside this part but not tied to any
     * specific note timeline. ABC uses the uppercase {@code W:} info field to
     * attach additional verses that are typeset below the tune once the
     * musical body has ended (in contrast to the lowercase {@code w:} field,
     * whose syllables land inside {@link Note#lyrics()}).
     *
     * <p>The engraver / demo can print these lines beneath the last system as
     * a stand-alone text block. Empty for scores that carry no such text.
     *
     * @return the (possibly empty) list of post-tune text lines
     */
    public List<String> postTuneText() {
        return postTuneText;
    }

    /**
     * Start building a part with the given identifier.
     *
     * @param id MusicXML {@code <part id>}; must not be {@code null}
     * @return a fresh {@link Builder}
     */
    public static Builder builder(String id) {
        return new Builder(id);
    }

    /**
     * Fluent builder for {@link Part} instances.
     */
    public static final class Builder {
        private final String id;
        private String name;
        private String abbreviation;
        private final List<Measure> measures = new ArrayList<>();
        private final List<String> postTuneText = new ArrayList<>();

        private Builder(String id) {
            this.id = id;
        }

        /**
         * Set the full part name ({@code <part-name>}).
         *
         * @param name the full name (may be {@code null})
         * @return this builder for chaining
         */
        public Builder name(String name) {
            this.name = name;
            return this;
        }

        /**
         * Set the short part label ({@code <part-abbreviation>}).
         *
         * @param abbreviation the abbreviation (may be {@code null})
         * @return this builder for chaining
         */
        public Builder abbreviation(String abbreviation) {
            this.abbreviation = abbreviation;
            return this;
        }

        /**
         * Append a measure to the part.
         *
         * @param measure the measure to add (non-null)
         * @return this builder for chaining
         */
        public Builder addMeasure(Measure measure) {
            this.measures.add(measure);
            return this;
        }

        /**
         * Replace the part's measures with the given list.
         *
         * @param measures the measures to set (each non-null)
         * @return this builder for chaining
         */
        public Builder measures(List<Measure> measures) {
            this.measures.clear();
            this.measures.addAll(measures);
            return this;
        }

        /**
         * Append a post-tune text line (one call per {@code W:} field in ABC).
         *
         * @param line the text (nulls and blanks are ignored)
         * @return this builder for chaining
         */
        public Builder addPostTuneText(String line) {
            if (line != null) {
                this.postTuneText.add(line);
            }
            return this;
        }

        /**
         * Replace the post-tune text lines with the given list.
         *
         * @param lines the lines to set (each non-null)
         * @return this builder for chaining
         */
        public Builder postTuneText(List<String> lines) {
            this.postTuneText.clear();
            for (String s : lines) {
                if (s != null) {
                    this.postTuneText.add(s);
                }
            }
            return this;
        }

        /**
         * Build the immutable {@link Part}.
         *
         * @return a new part
         * @throws IllegalStateException when no id has been supplied
         */
        public Part build() {
            if (id == null) {
                throw new IllegalStateException("Part requires an id");
            }
            return new Part(this);
        }
    }
}
