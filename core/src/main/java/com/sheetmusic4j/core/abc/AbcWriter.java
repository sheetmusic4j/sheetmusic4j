package com.sheetmusic4j.core.abc;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.sheetmusic4j.core.model.Attributes;
import com.sheetmusic4j.core.model.Chord;
import com.sheetmusic4j.core.model.Creator;
import com.sheetmusic4j.core.model.Duration;
import com.sheetmusic4j.core.model.KeySignature;
import com.sheetmusic4j.core.model.Measure;
import com.sheetmusic4j.core.model.MusicElement;
import com.sheetmusic4j.core.model.Note;
import com.sheetmusic4j.core.model.Part;
import com.sheetmusic4j.core.model.Pitch;
import com.sheetmusic4j.core.model.Rest;
import com.sheetmusic4j.core.model.Score;
import com.sheetmusic4j.core.model.Slur;
import com.sheetmusic4j.core.model.TimeModification;
import com.sheetmusic4j.core.model.TimeSignature;
import com.sheetmusic4j.core.model.Tuplet;

/**
 * Writes a {@link Score} as an ABC music notation document. Emits the same
 * subset understood by {@link AbcReader} so that a read/write/read
 * round-trip is structurally stable.
 *
 * <p>One tune ({@code X:N}) is emitted per {@link Part}. The unit note length
 * is fixed at {@code L:1/8}, which is the ABC convention for most tune types
 * and is compatible with all common note values through {@code Duration}
 * arithmetic.
 */
public final class AbcWriter {

    /** Numerator of the fixed unit note length ({@code 1/8}). */
    private static final int UNIT_NUM = 1;
    /** Denominator of the fixed unit note length ({@code 1/8}). */
    private static final int UNIT_DEN = 8;

    public void write(Score score, Path path) {
        try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(path))) {
            write(score, out);
        } catch (IOException e) {
            throw new AbcException("Could not write ABC file: " + path, e);
        }
    }

    public void write(Score score, OutputStream out) {
        try (Writer w = new OutputStreamWriter(out, StandardCharsets.UTF_8)) {
            writeScore(w, score);
        } catch (IOException e) {
            throw new AbcException("Failed to write ABC stream", e);
        }
    }

    private void writeScore(Writer w, Score score) throws IOException {
        List<Part> parts = score.parts();
        String composer = firstCreator(score, "composer");
        String title = score.workTitle().orElse(null);
        int tuneIndex = 1;
        for (Part part : parts) {
            writeTune(w, tuneIndex, part, title, composer);
            if (tuneIndex < parts.size()) {
                w.write("\n");
            }
            tuneIndex++;
        }
        if (parts.isEmpty()) {
            // Emit an empty stub tune so the file is still valid ABC.
            w.write("X:1\n");
            if (title != null) {
                w.write("T:" + title + "\n");
            }
            w.write("M:4/4\nL:1/8\nK:C\n");
        }
    }

    private static String firstCreator(Score score, String role) {
        for (Creator c : score.creators()) {
            if (role.equalsIgnoreCase(c.role())) {
                return c.name();
            }
        }
        return null;
    }

    private void writeTune(Writer w, int index, Part part, String scoreTitle, String composer) throws IOException {
        w.write("X:" + index + "\n");
        String title = scoreTitle;
        if (title == null || title.isEmpty()) {
            title = part.name() != null ? part.name() : "Untitled";
        }
        w.write("T:" + sanitize(title) + "\n");
        if (composer != null && !composer.isEmpty()) {
            w.write("C:" + sanitize(composer) + "\n");
        }

        // Discover meter and key from the first measure with attributes.
        TimeSignature meter = null;
        KeySignature key = null;
        for (Measure m : part.measures()) {
            Optional<Attributes> a = m.attributes();
            if (a.isPresent()) {
                if (meter == null) {
                    meter = a.get().timeSignature().orElse(null);
                }
                if (key == null) {
                    key = a.get().keySignature().orElse(null);
                }
            }
            if (meter != null && key != null) {
                break;
            }
        }
        if (meter == null) {
            meter = new TimeSignature(4, 4);
        }
        if (key == null) {
            key = KeySignature.cMajor();
        }

        w.write("M:" + meter.beats() + "/" + meter.beatType() + "\n");
        w.write("L:" + UNIT_NUM + "/" + UNIT_DEN + "\n");
        w.write("K:" + keyString(key) + "\n");

        writeBody(w, part, key);
    }

    private static String sanitize(String s) {
        // Strip newlines and % (which start comments) to keep info fields
        // syntactically well-formed on a single line.
        return s.replace('\n', ' ').replace('\r', ' ').replace("%", "");
    }

    private void writeBody(Writer w, Part part, KeySignature key) throws IOException {
        StringBuilder line = new StringBuilder();
        int emittedMeasuresOnLine = 0;
        KeySignature currentKey = key;
        Map<String, Integer> measureAccidentals = new HashMap<>();
        for (Measure measure : part.measures()) {
            // Detect key change mid-body and emit an inline K: field.
            Optional<Attributes> attrs = measure.attributes();
            if (attrs.isPresent()) {
                KeySignature k = attrs.get().keySignature().orElse(null);
                if (k != null && (currentKey == null || k.fifths() != currentKey.fifths())) {
                    if (line.length() > 0 && line.charAt(line.length() - 1) != ' ') {
                        line.append(' ');
                    }
                    line.append("[K:").append(keyString(k)).append(']');
                    currentKey = k;
                }
                TimeSignature ts = attrs.get().timeSignature().orElse(null);
                if (ts != null && measure.number() > 1) {
                    line.append("[M:").append(ts.beats()).append('/').append(ts.beatType()).append(']');
                }
            }
            measureAccidentals.clear();
            for (MusicElement element : measure.elements()) {
                writeElement(line, element, currentKey, measureAccidentals);
            }
            line.append('|');
            emittedMeasuresOnLine++;
            if (emittedMeasuresOnLine >= 4) {
                w.write(line.toString());
                w.write("\n");
                line.setLength(0);
                emittedMeasuresOnLine = 0;
            }
        }
        if (line.length() > 0) {
            w.write(line.toString());
            w.write("\n");
        }
        // Emit any ABC "words after tune" verses collected on the part.
        for (String text : part.postTuneText()) {
            w.write("W:");
            w.write(sanitize(text));
            w.write("\n");
        }
        }

    private void writeElement(StringBuilder line, MusicElement element, KeySignature key,
            Map<String, Integer> measureAccidentals) {
        if (element instanceof Note note) {
            writeNoteish(line, note, key, measureAccidentals, false);
        } else if (element instanceof Rest rest) {
            writeRest(line, rest);
        } else if (element instanceof Chord chord) {
            writeChord(line, chord, key, measureAccidentals);
        }
        // Directions/Harmony: dropped in MVP output.
    }

    private void writeChord(StringBuilder line, Chord chord, KeySignature key,
            Map<String, Integer> measureAccidentals) {
        List<Note> notes = chord.notes();
        // Track tuplet & slur decorations off the first note only.
        Note first = notes.get(0);
        emitTupletAndSlurStarts(line, first);
        line.append('[');
        for (Note n : notes) {
            writeNoteish(line, n, key, measureAccidentals, true);
        }
        line.append(']');
        appendLengthSuffix(line, first.duration());
        if (first.tieStart()) {
            line.append('-');
        }
        emitSlurStops(line, first);
        emitTupletStops();
        }

    private void writeNoteish(StringBuilder line, Note note, KeySignature key,
            Map<String, Integer> measureAccidentals, boolean insideChord) {
        Pitch pitch = note.pitch();
        int expectedAlter;
        String carryKey = pitch.step().name() + pitch.octave();
        Integer carry = measureAccidentals.get(carryKey);
        if (carry != null) {
            expectedAlter = carry;
        } else {
            expectedAlter = AbcKey.alterFor(pitch.step(), key);
        }
        boolean writeAccidental = note.displayedAccidental().isPresent()
                || pitch.alter() != expectedAlter;

        if (!insideChord) {
            emitTupletAndSlurStarts(line, note);
        }
        if (writeAccidental) {
            line.append(alterString(pitch.alter()));
            measureAccidentals.put(carryKey, pitch.alter());
        }
        line.append(pitchLetter(pitch));
        line.append(octaveMarks(pitch));
        if (!insideChord) {
            appendLengthSuffix(line, note.duration());
            if (note.tieStart()) {
                line.append('-');
            }
            emitSlurStops(line, note);
            emitTupletStops();
            }
            }

    private void writeRest(StringBuilder line, Rest rest) {
        line.append('z');
        appendLengthSuffix(line, rest.duration());
    }

    private static void emitTupletAndSlurStarts(StringBuilder line, Note note) {
        for (Tuplet t : note.tuplets()) {
            if (t.type() == Tuplet.Type.START) {
                TimeModification tm = note.timeModification().orElse(null);
                int actual = tm != null ? tm.actualNotes() : t.number();
                line.append('(').append(actual);
            }
        }
        for (Slur s : note.slurs()) {
            if (s.type() == Slur.Type.START) {
                line.append('(');
            }
        }
    }

    private static void emitSlurStops(StringBuilder line, Note note) {
        for (Slur s : note.slurs()) {
            if (s.type() == Slur.Type.STOP) {
                line.append(')');
            }
        }
    }

    private static void emitTupletStops() {
        // No explicit stop marker in ABC — implicit at count end. Nothing to
        // emit, but the hook is here so the write pattern matches slurs.
    }

    private static String alterString(int alter) {
        return switch (alter) {
            case -2 -> "__";
            case -1 -> "_";
            case 0 -> "=";
            case 1 -> "^";
            case 2 -> "^^";
            default -> "";
        };
    }

    private static String pitchLetter(Pitch p) {
        String letter = p.step().name();
        if (p.octave() >= 5) {
            letter = letter.toLowerCase();
        }
        return letter;
    }

    private static String octaveMarks(Pitch p) {
        int octave = p.octave();
        if (octave >= 6) {
            return "'".repeat(octave - 5);
        }
        if (octave <= 3) {
            return ",".repeat(4 - octave);
        }
        return "";
    }

    private static void appendLengthSuffix(StringBuilder line, Duration duration) {
        // multiplier = quarters / (4/UNIT_DEN * UNIT_NUM)
        //            = (value / divisions) / (4*UNIT_NUM/UNIT_DEN)
        //            = value * UNIT_DEN / (divisions * 4 * UNIT_NUM)
        long num = (long) duration.value() * UNIT_DEN;
        long den = (long) duration.divisions() * 4L * UNIT_NUM;
        long g = gcd(Math.abs(num), Math.abs(den));
        if (g == 0) {
            g = 1;
        }
        num /= g;
        den /= g;
        if (num == 1 && den == 1) {
            return;
        }
        if (den == 1) {
            line.append(num);
            return;
        }
        if (num == 1 && den == 2) {
            line.append('/');
            return;
        }
        if (num == 1) {
            line.append('/').append(den);
            return;
        }
        line.append(num).append('/').append(den);
    }

    private static long gcd(long a, long b) {
        while (b != 0) {
            long t = b;
            b = a % b;
            a = t;
        }
        return a == 0 ? 1 : a;
    }

    /** Render the key signature as an ABC {@code K:} value (e.g. "G", "Bb", "F#"). */
    private static String keyString(KeySignature key) {
        return switch (key.fifths()) {
            case -7 -> "Cb";
            case -6 -> "Gb";
            case -5 -> "Db";
            case -4 -> "Ab";
            case -3 -> "Eb";
            case -2 -> "Bb";
            case -1 -> "F";
            case 0 -> "C";
            case 1 -> "G";
            case 2 -> "D";
            case 3 -> "A";
            case 4 -> "E";
            case 5 -> "B";
            case 6 -> "F#";
            case 7 -> "C#";
            default -> "C";
        };
    }

    }
