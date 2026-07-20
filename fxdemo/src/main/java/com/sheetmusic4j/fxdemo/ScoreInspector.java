package com.sheetmusic4j.fxdemo;

import com.sheetmusic4j.core.model.Attributes;
import com.sheetmusic4j.core.model.Chord;
import com.sheetmusic4j.core.model.Clef;
import com.sheetmusic4j.core.model.Measure;
import com.sheetmusic4j.core.model.MusicElement;
import com.sheetmusic4j.core.model.Note;
import com.sheetmusic4j.core.model.Part;
import com.sheetmusic4j.core.model.Rest;
import com.sheetmusic4j.core.model.Score;

/**
 * Produces a human-readable, framework-agnostic summary of a {@link Score}.
 * Kept free of JavaFX so it can be unit tested and reused for logging.
 */
public final class ScoreInspector {

    private ScoreInspector() {
    }

    /**
     * Aggregate counts for a whole score.
     *
     * @param parts    number of parts in the score
     * @param measures number of measures across all parts
     * @param notes    number of note elements
     * @param rests    number of rest elements
     * @param chords   number of chord elements
     */
    public record Stats(int parts, int measures, int notes, int rests, int chords) {

        /**
         * Returns the total number of musical elements counted in this summary.
         *
         * @return combined note, rest, and chord count
         */
        public int elements() {
            return notes + rests + chords;
        }
    }

    /**
     * Count major score structures and musical element types.
     *
     * @param score score to summarize
     * @return aggregate counts for the score contents
     */
    public static Stats stats(Score score) {
        int parts = score.parts().size();
        int measures = 0;
        int notes = 0;
        int rests = 0;
        int chords = 0;
        for (Part part : score.parts()) {
            measures += part.measures().size();
            for (Measure measure : part.measures()) {
                for (MusicElement element : measure.elements()) {
                    if (element instanceof Note) {
                        notes++;
                    } else if (element instanceof Rest) {
                        rests++;
                    } else if (element instanceof Chord) {
                        chords++;
                    }
                }
            }
        }
        return new Stats(parts, measures, notes, rests, chords);
    }

    /**
     * Build a multi-line debug description of the score.
     *
     * @param score score to describe, or {@code null}
     * @return human-readable multi-line debug text
     */
    public static String describe(Score score) {
        if (score == null) {
            return "No score loaded.";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Work title    : ").append(score.workTitle().orElse("(none)")).append('\n');
        sb.append("Movement title: ").append(score.movementTitle().orElse("(none)")).append('\n');

        Stats stats = stats(score);
        sb.append("Parts         : ").append(stats.parts()).append('\n');
        sb.append("Measures      : ").append(stats.measures()).append('\n');
        sb.append("Notes         : ").append(stats.notes()).append('\n');
        sb.append("Rests         : ").append(stats.rests()).append('\n');
        sb.append("Chords        : ").append(stats.chords()).append('\n');
        sb.append('\n');

        int partIndex = 1;
        for (Part part : score.parts()) {
            sb.append("Part ").append(partIndex++).append(": ")
                    .append(part.name() != null ? part.name() : part.id())
                    .append(" (id=").append(part.id()).append(", measures=")
                    .append(part.measures().size()).append(")\n");
            describeFirstAttributes(sb, part);
        }
        return sb.toString();
    }

    private static void describeFirstAttributes(StringBuilder sb, Part part) {
        for (Measure measure : part.measures()) {
            if (measure.attributes().isEmpty()) {
                continue;
            }
            Attributes attributes = measure.attributes().get();
            sb.append("    divisions: ").append(attributes.divisions().map(String::valueOf).orElse("-"))
                    .append(", key: ").append(attributes.keySignature().map(k -> k.fifths() + " fifths").orElse("-"))
                    .append(", time: ").append(attributes.timeSignature()
                            .map(t -> t.beats() + "/" + t.beatType()).orElse("-"))
                    .append(", clef: ").append(attributes.clef().map(ScoreInspector::describeClef).orElse("-"))
                    .append('\n');
            return;
        }
    }

    private static String describeClef(Clef clef) {
        return clef.sign() + "/" + clef.line();
    }
}
