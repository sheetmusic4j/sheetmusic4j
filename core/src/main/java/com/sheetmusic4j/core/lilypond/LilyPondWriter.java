package com.sheetmusic4j.core.lilypond;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import com.sheetmusic4j.core.model.Attributes;
import com.sheetmusic4j.core.model.Chord;
import com.sheetmusic4j.core.model.Clef;
import com.sheetmusic4j.core.model.Creator;
import com.sheetmusic4j.core.model.Direction;
import com.sheetmusic4j.core.model.DirectionType;
import com.sheetmusic4j.core.model.KeySignature;
import com.sheetmusic4j.core.model.Measure;
import com.sheetmusic4j.core.model.MusicElement;
import com.sheetmusic4j.core.model.Note;
import com.sheetmusic4j.core.model.Part;
import com.sheetmusic4j.core.model.Pitch;
import com.sheetmusic4j.core.model.Rest;
import com.sheetmusic4j.core.model.Score;
import com.sheetmusic4j.core.model.Slur;
import com.sheetmusic4j.core.model.TimeSignature;
import com.sheetmusic4j.core.model.Tuplet;

/**
 * Writes a {@link Score} as a LilyPond {@code .ly} document. Emits the same
 * subset understood by {@link LilyPondReader} so that a read/write/read
 * round-trip is structurally stable.
 *
 * <p>Each part becomes a {@code \score { \new Staff { … } }} block with the
 * music expression rendered in <em>absolute</em> pitch mode (LilyPond
 * happily consumes either absolute or relative; absolute is simpler for a
 * machine producer).
 */
public final class LilyPondWriter {

    /** LilyPond version declared at the top of emitted files. */
    private static final String LILY_VERSION = "2.24.0";

    public void write(Score score, Path path) {
        try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(path))) {
            write(score, out);
        } catch (IOException e) {
            throw new LilyPondException("Could not write LilyPond file: " + path, e);
        }
    }

    public void write(Score score, OutputStream out) {
        try (Writer w = new OutputStreamWriter(out, StandardCharsets.UTF_8)) {
            writeScore(w, score);
        } catch (IOException e) {
            throw new LilyPondException("Failed to write LilyPond stream", e);
        }
    }

    private void writeScore(Writer w, Score score) throws IOException {
        w.write("\\version \"" + LILY_VERSION + "\"\n");

        String title = score.workTitle().orElse(null);
        String subtitle = score.movementTitle().orElse(null);
        String composer = firstCreator(score, "composer");
        if (title != null || subtitle != null || composer != null) {
            w.write("\\header {\n");
            if (title != null) {
                w.write("    title = " + quote(title) + "\n");
            }
            if (subtitle != null) {
                w.write("    subtitle = " + quote(subtitle) + "\n");
            }
            if (composer != null) {
                w.write("    composer = " + quote(composer) + "\n");
            }
            w.write("}\n");
        }

        List<Part> parts = score.parts();
        if (parts.isEmpty()) {
            w.write("\\score {\n    \\new Staff {\n        \\time 4/4\n        \\key c \\major\n        \\clef treble\n    }\n}\n");
            return;
        }
        for (Part part : parts) {
            writePart(w, part);
        }
    }

    private void writePart(Writer w, Part part) throws IOException {
        // Discover initial meter, key, and clef from the first measure with attributes.
        TimeSignature meter = null;
        KeySignature key = null;
        Clef clef = null;
        for (Measure m : part.measures()) {
            Optional<Attributes> a = m.attributes();
            if (a.isPresent()) {
                if (meter == null) {
                    meter = a.get().timeSignature().orElse(null);
                }
                if (key == null) {
                    key = a.get().keySignature().orElse(null);
                }
                if (clef == null) {
                    clef = a.get().clef().orElse(null);
                }
            }
            if (meter != null && key != null && clef != null) {
                break;
            }
        }
        if (meter == null) {
            meter = new TimeSignature(4, 4);
        }
        if (key == null) {
            key = KeySignature.cMajor();
        }
        if (clef == null) {
            clef = Clef.treble();
        }

        w.write("\\score {\n");
        w.write("    \\new Staff {\n");
        w.write("        \\time " + meter.beats() + "/" + meter.beatType() + "\n");
        w.write("        \\key " + LilyPondPitch.keyString(key) + "\n");
        w.write("        \\clef " + clefName(clef) + "\n");

        writeBody(w, part, key, meter);

        w.write("    }\n");

        // Attach lyrics as \addlyrics when any note carries one.
        String lyrics = collectLyrics(part);
        if (!lyrics.isBlank()) {
            w.write("    \\addlyrics {\n        " + lyrics + "\n    }\n");
        }
        w.write("}\n");

        // Post-tune text (from ABC round-trips carrying W: lines) is written
        // as trailing block comments so the file remains valid LilyPond and
        // the text is still preserved through another round-trip.
        for (String text : part.postTuneText()) {
            w.write("%{ " + sanitize(text) + " %}\n");
        }
    }

    private void writeBody(Writer w, Part part, KeySignature initialKey, TimeSignature initialMeter)
            throws IOException {
        StringBuilder line = new StringBuilder();
        String previousDurationSuffix = null;
        KeySignature currentKey = initialKey;
        TimeSignature currentMeter = initialMeter;
        Clef currentClef = null;
        int emittedOnLine = 0;
        int measureIdx = 0;
        for (Measure measure : part.measures()) {
            Optional<Attributes> attrs = measure.attributes();
            if (attrs.isPresent() && measureIdx > 0) {
                KeySignature k = attrs.get().keySignature().orElse(null);
                if (k != null && k.fifths() != currentKey.fifths()) {
                    line.append(" \\key ").append(LilyPondPitch.keyString(k));
                    currentKey = k;
                    previousDurationSuffix = null;
                }
                TimeSignature ts = attrs.get().timeSignature().orElse(null);
                if (ts != null && (ts.beats() != currentMeter.beats() || ts.beatType() != currentMeter.beatType())) {
                    line.append(" \\time ").append(ts.beats()).append('/').append(ts.beatType());
                    currentMeter = ts;
                }
                Clef c = attrs.get().clef().orElse(null);
                if (c != null && !c.equals(currentClef)) {
                    line.append(" \\clef ").append(clefName(c));
                    currentClef = c;
                }
            }
            for (MusicElement element : measure.elements()) {
                previousDurationSuffix = writeElement(line, element, previousDurationSuffix);
            }
            // Bar line.
            if (measure.barline().isPresent()) {
                String barStyle = barStyleFor(measure.barline().get());
                if (barStyle != null) {
                    line.append(" \\bar \"").append(barStyle).append('"');
                }
            }
            line.append(" |");
            emittedOnLine++;
            if (emittedOnLine >= 4) {
                w.write("        ");
                w.write(line.toString().stripLeading());
                w.write("\n");
                line.setLength(0);
                emittedOnLine = 0;
            }
            measureIdx++;
        }
        if (line.length() > 0) {
            w.write("        ");
            w.write(line.toString().stripLeading());
            w.write("\n");
        }
    }

    private static String writeElement(StringBuilder line, MusicElement element, String previousDurationSuffix) {
        if (element instanceof Note note) {
            return writeNote(line, note, previousDurationSuffix);
        }
        if (element instanceof Rest rest) {
            return writeRest(line, rest, previousDurationSuffix);
        }
        if (element instanceof Chord chord) {
            return writeChord(line, chord, previousDurationSuffix);
        }
        if (element instanceof Direction direction && direction.type() instanceof DirectionType.Metronome m) {
            line.append(" \\tempo ")
                    .append(LilyPondDuration.typeToNumber(m.beatUnit()))
                    .append(m.dotted() ? "." : "")
                    .append(" = ")
                    .append(m.perMinute());
        }
        // Harmony / other directions: dropped for MVP.
        return previousDurationSuffix;
    }

    private static String writeNote(StringBuilder line, Note note, String previousDurationSuffix) {
        prependSpace(line);
        emitTupletStart(line, note);
        appendPitchToken(line, note.pitch());
        String newSuffix = LilyPondDuration.suffixFor(note.type(), note.dots());
        if (!newSuffix.equals(previousDurationSuffix)) {
            line.append(newSuffix);
        }
        if (note.tieStart()) {
            line.append('~');
        }
        emitSlursAndTupletClose(line, note);
        return newSuffix;
        }

    private static String writeRest(StringBuilder line, Rest rest, String previousDurationSuffix) {
        prependSpace(line);
        line.append('r');
        String newSuffix = LilyPondDuration.suffixFor(rest.type(), rest.dots());
        if (!newSuffix.equals(previousDurationSuffix)) {
            line.append(newSuffix);
        }
        return newSuffix;
    }

    private static String writeChord(StringBuilder line, Chord chord, String previousDurationSuffix) {
        List<Note> notes = chord.notes();
        Note first = notes.get(0);
        prependSpace(line);
        emitTupletStart(line, first);
        line.append('<');
        for (int i = 0; i < notes.size(); i++) {
            if (i > 0) {
                line.append(' ');
            }
            appendPitchToken(line, notes.get(i).pitch());
        }
        line.append('>');
        String newSuffix = LilyPondDuration.suffixFor(first.type(), first.dots());
        if (!newSuffix.equals(previousDurationSuffix)) {
            line.append(newSuffix);
        }
        if (first.tieStart()) {
            line.append('~');
        }
        emitSlursAndTupletClose(line, first);
        return newSuffix;
    }

    private static void appendPitchToken(StringBuilder line, Pitch pitch) {
        line.append(LilyPondPitch.nameOf(pitch.step(), pitch.alter()));
        // Middle C (C4) is written as c'; each octave above adds a ',
        // each octave below adds a ,.
        int octaveMarks = pitch.octave() - 3;
        if (octaveMarks > 0) {
            for (int i = 0; i < octaveMarks; i++) {
                line.append('\'');
            }
        } else if (octaveMarks < 0) {
            for (int i = 0; i < -octaveMarks; i++) {
                line.append(',');
            }
        }
    }

    private static void emitTupletStart(StringBuilder line, Note note) {
        for (Tuplet t : note.tuplets()) {
            if (t.type() == Tuplet.Type.START) {
                int actual = note.timeModification().map(tm -> tm.actualNotes()).orElse(t.number());
                int normal = note.timeModification().map(tm -> tm.normalNotes()).orElse(2);
                line.append("\\tuplet ").append(actual).append('/').append(normal).append(" { ");
            }
        }
    }

    private static void emitSlursAndTupletClose(StringBuilder line, Note note) {
        // LilyPond convention: '(' opens the slur *after* the first note
        // it belongs to; ')' closes *after* the last note.
        boolean hasStart = false;
        boolean hasStop = false;
        for (Slur s : note.slurs()) {
            if (s.type() == Slur.Type.START) {
                hasStart = true;
            }
            if (s.type() == Slur.Type.STOP) {
                hasStop = true;
            }
        }
        if (hasStart) {
            line.append('(');
        }
        if (hasStop) {
            line.append(')');
        }
        // Emit tuplet-close brace when this note terminates a tuplet run.
        for (Tuplet t : note.tuplets()) {
            if (t.type() == Tuplet.Type.STOP) {
                line.append(" }");
            }
        }
    }

    private static void prependSpace(StringBuilder line) {
        if (line.length() > 0 && line.charAt(line.length() - 1) != ' ') {
            line.append(' ');
        }
    }

    private static String barStyleFor(com.sheetmusic4j.core.model.Barline bl) {
        if (bl.repeat() == com.sheetmusic4j.core.model.Barline.Repeat.BACKWARD) {
            return ":|";
        }
        if (bl.repeat() == com.sheetmusic4j.core.model.Barline.Repeat.FORWARD) {
            return "|:";
        }
        return switch (bl.style()) {
            case DOUBLE -> "||";
            case FINAL -> "|.";
            default -> null;
        };
    }

    private static String collectLyrics(Part part) {
        StringBuilder sb = new StringBuilder();
        for (Measure m : part.measures()) {
            for (MusicElement el : m.elements()) {
                Note note = null;
                if (el instanceof Note n) {
                    note = n;
                } else if (el instanceof Chord c && !c.notes().isEmpty()) {
                    note = c.notes().get(0);
                }
                if (note != null && !note.lyrics().isEmpty()) {
                    String text = note.lyrics().get(0).text();
                    if (sb.length() > 0) {
                        sb.append(' ');
                    }
                    sb.append(sanitizeLyric(text));
                    if (note.lyrics().get(0).syllabic() == com.sheetmusic4j.core.model.Syllabic.BEGIN
                            || note.lyrics().get(0).syllabic() == com.sheetmusic4j.core.model.Syllabic.MIDDLE) {
                        sb.append(" --");
                    }
                }
            }
        }
        return sb.toString();
    }

    private static String sanitizeLyric(String text) {
        if (text == null || text.isEmpty()) {
            return "_";
        }
        // Quote when the syllable would otherwise clash with LilyPond tokens.
        if (needsQuoting(text)) {
            return quote(text);
        }
        return text;
    }

    private static boolean needsQuoting(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isWhitespace(c) || c == '"' || c == '\\' || c == '{' || c == '}'
                    || c == '<' || c == '>' || c == '%') {
                return true;
            }
        }
        return false;
    }

    private static String quote(String value) {
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\\' || c == '"') {
                sb.append('\\');
            }
            sb.append(c);
        }
        sb.append('"');
        return sb.toString();
    }

    private static String sanitize(String text) {
        // Block-comment safety: strip any nested %} sequences that would
        // otherwise close the enclosing comment prematurely.
        return text.replace("%}", "% }");
    }

    private static String clefName(Clef clef) {
        if (clef == null) {
            return "treble";
        }
        return switch (clef.sign()) {
            case G -> "treble";
            case F -> "bass";
            case C -> switch (clef.line()) {
                case 1 -> "soprano";
                case 2 -> "mezzosoprano";
                case 3 -> "alto";
                case 4 -> "tenor";
                case 5 -> "baritone";
                default -> "alto";
            };
            case PERCUSSION -> "percussion";
            case TAB -> "tab";
            case NONE -> "treble";
        };
    }

    private static String firstCreator(Score score, String role) {
        for (Creator c : score.creators()) {
            if (role.equalsIgnoreCase(c.role())) {
                return c.name();
            }
        }
        return null;
    }

    }
