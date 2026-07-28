package com.sheetmusic4j.core.abc;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import com.sheetmusic4j.core.model.Accidental;
import com.sheetmusic4j.core.model.Articulation;
import com.sheetmusic4j.core.model.Attributes;
import com.sheetmusic4j.core.model.Barline;
import com.sheetmusic4j.core.model.Beam;
import com.sheetmusic4j.core.model.Chord;
import com.sheetmusic4j.core.model.Clef;
import com.sheetmusic4j.core.model.Creator;
import com.sheetmusic4j.core.model.Direction;
import com.sheetmusic4j.core.model.DirectionType;
import com.sheetmusic4j.core.model.Duration;
import com.sheetmusic4j.core.model.Harmony;
import com.sheetmusic4j.core.model.HarmonyKind;
import com.sheetmusic4j.core.model.KeySignature;
import com.sheetmusic4j.core.model.Lyric;
import com.sheetmusic4j.core.model.Measure;
import com.sheetmusic4j.core.model.MusicElement;
import com.sheetmusic4j.core.model.Note;
import com.sheetmusic4j.core.model.NoteType;
import com.sheetmusic4j.core.model.Part;
import com.sheetmusic4j.core.model.Pitch;
import com.sheetmusic4j.core.model.Rest;
import com.sheetmusic4j.core.model.Score;
import com.sheetmusic4j.core.model.Slur;
import com.sheetmusic4j.core.model.Step;
import com.sheetmusic4j.core.model.Syllabic;
import com.sheetmusic4j.core.model.TimeModification;
import com.sheetmusic4j.core.model.TimeSignature;
import com.sheetmusic4j.core.model.Tuplet;

/**
 * Reads an ABC music notation document into a {@link Score}.
 *
 * <p>The parser handles the MVP subset described in the module documentation:
 * headers (X, T, C, M, L, K, Q), pitched notes with accidentals and octave
 * marks, rests, chords, ties, slurs, tuplets, broken rhythm, bar lines,
 * inline fields, and {@code w:} lyric lines. Unsupported constructs
 * (decorations, grace notes, guitar-chord annotations, multi-voice) are
 * skipped silently so parsers survive real-world files.
 *
 * <p>Only the first tune (leading {@code X:} block) is loaded; subsequent
 * tunes in a multi-tune file are ignored for MVP.
 */
public final class AbcReader {

    /** Divisions per quarter note used by the produced {@link Score}. */
    private static final int DIVISIONS = 96;

    public Score read(Path path) {
        try (InputStream in = Files.newInputStream(path)) {
            return read(in);
        } catch (IOException e) {
            throw new AbcException("Could not read ABC file: " + path, e);
        }
    }

    public Score read(InputStream in) {
        try {
            String text = readAll(in);
            return parse(text);
        } catch (IOException e) {
            throw new AbcException("Failed to read ABC stream", e);
        }
    }

    private String readAll(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        int n;
        while ((n = in.read(chunk)) > 0) {
            buf.write(chunk, 0, n);
        }
        return buf.toString(StandardCharsets.UTF_8);
    }

    private Score parse(String text) {
        List<String> lines = splitLines(text);
        List<List<String>> tunes = splitTunes(lines);
        Score.Builder score = Score.builder();
        int index = 1;
        for (List<String> tuneLines : tunes) {
            parseTune(score, tuneLines, index);
            index++;
            // MVP: keep parsing subsequent tunes as additional parts so
            // multi-tune files are still readable. If the user only wants the
            // first, they can pick score.parts().get(0).
        }
        if (score.build().parts().isEmpty()) {
            // Defensive fallback: produce an empty score rather than throwing
            // for files that carry no music (headers only).
            return score.build();
        }
        return score.build();
    }

    private List<String> splitLines(String text) {
        List<String> out = new ArrayList<>();
        int start = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\n') {
                out.add(text.substring(start, i));
                start = i + 1;
            } else if (c == '\r') {
                out.add(text.substring(start, i));
                if (i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                    i++;
                }
                start = i + 1;
            }
        }
        if (start < text.length()) {
            out.add(text.substring(start));
        }
        return out;
    }

    private List<List<String>> splitTunes(List<String> lines) {
        List<List<String>> out = new ArrayList<>();
        List<String> current = null;
        for (String raw : lines) {
            if (raw.stripLeading().startsWith("%")) {
                // Pure comment line (full-line %/%% directive): ignored, and
                // must NOT be treated as a blank separator line below, since
                // stripping its content would otherwise make it look blank
                // and prematurely terminate the tune it appears inside of.
                continue;
            }
            String stripped = stripInlineComment(raw);
            if (isBlank(stripped)) {
                if (current != null && !current.isEmpty()) {
                    // Blank line terminates a tune only when the tune has
                    // already accumulated content; otherwise skip.
                    current = null;
                }
                continue;
            }
            if (stripped.startsWith("X:")) {
                current = new ArrayList<>();
                out.add(current);
                current.add(stripped);
            } else if (current != null) {
                current.add(stripped);
            }
            // Lines before any X: (file-level info) are ignored for MVP.
        }
        return out;
    }

    private static boolean isBlank(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (!Character.isWhitespace(s.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Strip trailing {@code %} comments. Note ABC allows {@code \%} to escape
     * a literal percent sign — for MVP we treat all {@code %} as comment
     * starters, which is safe for parsing (escapes only affect typesetting).
     */
    private static String stripInlineComment(String line) {
        int p = line.indexOf('%');
        if (p < 0) {
            return line;
        }
        // Preserve leading whitespace/BOM but drop the comment.
        return line.substring(0, p);
    }

    private void parseTune(Score.Builder score, List<String> tuneLines, int fallbackIndex) {
        TuneHeader header = new TuneHeader();
        header.referenceNumber = fallbackIndex;
        int cursor = 0;
        // Header phase: everything up to (and including) K:.
        boolean bodyStarted = false;
        while (cursor < tuneLines.size()) {
            String line = tuneLines.get(cursor);
            if (isInfoField(line)) {
                char field = line.charAt(0);
                String value = line.substring(2).trim();
                applyHeaderField(header, field, value);
                cursor++;
                if (field == 'K') {
                    bodyStarted = true;
                    break;
                }
            } else {
                // Body reached without an explicit K: — apply defaults.
                break;
            }
        }
        if (!bodyStarted && header.key == null) {
            header.key = KeySignature.cMajor();
        }

        // Body phase.
        BodyParser body = new BodyParser(header, fallbackIndex == 1);
        while (cursor < tuneLines.size()) {
            String line = tuneLines.get(cursor);
            cursor++;
            if (line == null || isBlank(line)) {
                continue;
            }
            if (isInfoField(line)) {
                char field = line.charAt(0);
                String value = line.substring(2);
                if (field == 'w') {
                    body.applyLyrics(value.trim());
                } else if (field == 'W') {
                    // Uppercase W: is the ABC "words after tune" field:
                    // free-form verse text that renders below the last
                    // system rather than aligning to individual notes.
                    // By convention a single leading space after the colon
                    // is presentational and stripped.
                    String w = value;
                    if (w.startsWith(" ")) {
                        w = w.substring(1);
                    }
                    body.addPostTuneText(w);
                } else if (field == 'K' || field == 'M' || field == 'L' || field == 'Q' || field == 'T') {
                    body.applyMidTuneField(field, value.trim());
                }
                // Other mid-tune info fields (V:, N:, ...) are ignored.
                continue;
            }
            body.parseLine(line);
        }
        body.finish();

        Part part = body.buildPart();
        if (part != null) {
            score.addPart(part);
        }
        // Score-level metadata is taken from the first tune's header. Later
        // tunes add creators/titles only when the score-level fields are
        // still unset, to avoid clobbering.
        if (score.build().workTitle().isEmpty() && header.title != null) {
            score.workTitle(header.title);
        }
        if (score.build().movementTitle().isEmpty() && header.subtitle != null) {
            score.movementTitle(header.subtitle);
        }
        if (header.composer != null && !score.hasCreatorRole("composer")) {
            Creator creator = Creator.of("composer", header.composer);
            if (creator != null) {
                score.addCreator(creator);
            }
        }
    }

    private static boolean isInfoField(String line) {
        if (line.length() < 2) {
            return false;
        }
        char c = line.charAt(0);
        if (line.charAt(1) != ':') {
            return false;
        }
        // ABC info-field letters are single ASCII letters (upper or lower case
        // for a small set: w, W, r, s, ...).
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z');
    }

    private void applyHeaderField(TuneHeader header, char field, String value) {
        switch (field) {
            case 'X' -> {
                try {
                    header.referenceNumber = Integer.parseInt(value.trim());
                } catch (NumberFormatException ignored) {
                    // Keep fallback numbering.
                }
            }
            case 'T' -> {
                if (header.title == null) {
                    header.title = value;
                } else if (header.subtitle == null) {
                    // A second T: line is the tune's subtitle (an
                    // alternative title); rendered as its own line below
                    // the main title.
                    header.subtitle = value;
                } else {
                    header.subtitle = header.subtitle + " / " + value;
                }
            }
            case 'C' -> header.composer = value;
            case 'M' -> header.timeSignature = parseMeter(value);
            case 'L' -> header.unitLength = parseUnitLength(value);
            case 'Q' -> header.tempo = parseTempo(value);
            case 'K' -> header.key = AbcKey.parse(value);
            default -> {
                // Ignore other headers (P, N, R, O, A, Z, S, B, D, F, G, H,
                // I, U, V, ...) for MVP.
            }
        }
    }

    private static TimeSignature parseMeter(String value) {
        if (value == null) {
            return null;
        }
        String v = value.trim();
        if (v.isEmpty() || v.equalsIgnoreCase("none")) {
            return null;
        }
        if (v.equals("C")) {
            return new TimeSignature(4, 4);
        }
        if (v.equals("C|")) {
            return new TimeSignature(2, 2);
        }
        int slash = v.indexOf('/');
        if (slash > 0 && slash < v.length() - 1) {
            try {
                int beats = Integer.parseInt(v.substring(0, slash).trim());
                int beatType = Integer.parseInt(v.substring(slash + 1).trim());
                return new TimeSignature(beats, beatType);
            } catch (NumberFormatException e) {
                // fall through to null
            }
        }
        return null;
    }

    private static AbcNoteLength.Fraction parseUnitLength(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        String v = value.trim();
        int slash = v.indexOf('/');
        if (slash < 0) {
            try {
                return AbcNoteLength.Fraction.of(Integer.parseInt(v), 1);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        try {
            int num = slash == 0 ? 1 : Integer.parseInt(v.substring(0, slash).trim());
            int den = Integer.parseInt(v.substring(slash + 1).trim());
            return AbcNoteLength.Fraction.of(num, den);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static DirectionType.Metronome parseTempo(String value) {
        if (value == null) {
            return null;
        }
        String v = value.trim();
        // Q:120  or  Q:1/4=120  or  Q:"Allegro" 1/4=120 (only bpm is used).
        int eq = v.indexOf('=');
        String bpmPart = eq >= 0 ? v.substring(eq + 1).trim() : v;
        // Strip trailing non-digit characters (e.g. "120 bpm" -> "120").
        int end = 0;
        while (end < bpmPart.length() && Character.isDigit(bpmPart.charAt(end))) {
            end++;
        }
        if (end == 0) {
            return null;
        }
        try {
            int bpm = Integer.parseInt(bpmPart.substring(0, end));
            return new DirectionType.Metronome(NoteType.QUARTER, false, bpm);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Shorthand chord-quality tokens (as written after the root letter in an
     * ABC guitar-chord string, e.g. {@code "Gm7"} -> {@code "m7"}) mapped to
     * the matching {@link HarmonyKind}. Mirrors common lead-sheet notation;
     * an unrecognised suffix falls back to {@link HarmonyKind#OTHER} with
     * the raw text kept as a {@link Harmony#textOverride()}.
     */
    private static final Map<String, HarmonyKind> CHORD_QUALITY_ALIASES = Map.ofEntries(
            Map.entry("", HarmonyKind.MAJOR),
            Map.entry("maj", HarmonyKind.MAJOR),
            Map.entry("m", HarmonyKind.MINOR),
            Map.entry("min", HarmonyKind.MINOR),
            Map.entry("-", HarmonyKind.MINOR),
            Map.entry("7", HarmonyKind.DOMINANT_SEVENTH),
            Map.entry("dom7", HarmonyKind.DOMINANT_SEVENTH),
            Map.entry("maj7", HarmonyKind.MAJOR_SEVENTH),
            Map.entry("m7", HarmonyKind.MINOR_SEVENTH),
            Map.entry("min7", HarmonyKind.MINOR_SEVENTH),
            Map.entry("dim", HarmonyKind.DIMINISHED),
            Map.entry("dim7", HarmonyKind.DIMINISHED_SEVENTH),
            Map.entry("aug", HarmonyKind.AUGMENTED),
            Map.entry("+", HarmonyKind.AUGMENTED),
            Map.entry("6", HarmonyKind.MAJOR_SIXTH),
            Map.entry("m6", HarmonyKind.MINOR_SIXTH),
            Map.entry("min6", HarmonyKind.MINOR_SIXTH),
            Map.entry("maj9", HarmonyKind.MAJOR_NINTH),
            Map.entry("m9", HarmonyKind.MINOR_NINTH),
            Map.entry("min9", HarmonyKind.MINOR_NINTH),
            Map.entry("9", HarmonyKind.DOMINANT_NINTH),
            Map.entry("sus4", HarmonyKind.SUSPENDED_FOURTH),
            Map.entry("sus", HarmonyKind.SUSPENDED_FOURTH),
            Map.entry("sus2", HarmonyKind.SUSPENDED_SECOND),
            Map.entry("5", HarmonyKind.POWER),
            Map.entry("m7b5", HarmonyKind.HALF_DIMINISHED),
            Map.entry("dim7b5", HarmonyKind.HALF_DIMINISHED));

    /**
     * Parse an ABC guitar-chord string (the content of a {@code "..."}
     * annotation) into a {@link Harmony}, or {@code null} if it doesn't look
     * like a chord symbol (e.g. free text like {@code "Fine"} or a
     * positioned annotation like {@code "^turn"}) - such strings are simply
     * dropped, matching pre-existing behaviour for non-chord annotations.
     */
    private static Harmony parseChordSymbol(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim();
        if (s.isEmpty()) {
            return null;
        }
        String bassText = null;
        int slash = s.indexOf('/');
        if (slash >= 0) {
            bassText = s.substring(slash + 1).trim();
            s = s.substring(0, slash).trim();
        }
        ChordRoot root = parseChordRoot(s);
        if (root == null) {
            return null;
        }
        Harmony.Bass bass = null;
        if (bassText != null && !bassText.isEmpty()) {
            ChordRoot b = parseChordRoot(bassText);
            if (b != null) {
                bass = new Harmony.Bass(b.step, b.alter);
            }
        }
        HarmonyKind kind = CHORD_QUALITY_ALIASES.get(root.suffix.toLowerCase(Locale.ROOT));
        String textOverride = null;
        if (kind == null) {
            kind = HarmonyKind.OTHER;
            textOverride = root.suffix;
        }
        return new Harmony(new Harmony.Root(root.step, root.alter), kind,
                Optional.ofNullable(bass), Optional.ofNullable(textOverride));
    }

    /** A parsed chord-symbol root or slash-bass letter: step + accidental + remaining suffix. */
    private record ChordRoot(Step step, int alter, String suffix) {
    }

    private static ChordRoot parseChordRoot(String s) {
        if (s.isEmpty()) {
            return null;
        }
        char letter = s.charAt(0);
        if (letter < 'A' || letter > 'G') {
            return null;
        }
        Step step = Step.valueOf(String.valueOf(letter));
        int i = 1;
        int alter = 0;
        if (i < s.length() && (s.charAt(i) == '#' || s.charAt(i) == 'b')) {
            alter = s.charAt(i) == '#' ? 1 : -1;
            i++;
        }
        return new ChordRoot(step, alter, s.substring(i));
    }

    /** Header fields collected before the first body line. */
    private static final class TuneHeader {
        int referenceNumber = 1;
        String title;
        String subtitle;
        String composer;
        TimeSignature timeSignature;
        AbcNoteLength.Fraction unitLength;
        DirectionType.Metronome tempo;
        KeySignature key;
    }

    /**
     * Fills in the ABC default unit-note-length when one wasn't given
     * explicitly: {@code 1/16} for compact time signatures (numerator/beat
     * ratio &lt; 0.75), {@code 1/8} otherwise.
     */
    private static AbcNoteLength.Fraction defaultUnitLength(TimeSignature ts) {
        if (ts == null) {
            return new AbcNoteLength.Fraction(1, 8);
        }
        double ratio = (double) ts.beats() / ts.beatType();
        if (ratio < 0.75) {
            return new AbcNoteLength.Fraction(1, 16);
        }
        return new AbcNoteLength.Fraction(1, 8);
    }

    /**
     * State machine that walks body characters and emits model objects into a
     * {@link Part.Builder}. One instance per tune.
     */
    private final class BodyParser {

        private final Part.Builder part;
        private AbcNoteLength.Fraction unitLength;
        private KeySignature key;
        private TimeSignature timeSignature;
        private DirectionType.Metronome pendingTempo;
        private boolean firstMeasureFlushed;

        /** Pending measure being filled by the tokenizer. */
        private PendingMeasure currentMeasure;
        /** Reference to {@code currentMeasure.elements}; refreshed on each measure start. */
        private List<MusicElement> pendingElements;
        /** Pending measures collected during the current music line. */
        private final List<PendingMeasure> lineMeasures = new ArrayList<>();
        private int measureNumber = 1;
        private final List<Note> lyricNoteAnchors = new ArrayList<>();

        // Barline / repeat / ending bookkeeping. A repeat-start ("|:") has
        // nothing to attach trailing dots to on the measure it follows (it
        // may be the very first measure of the part), so it is carried
        // forward as a leading mark on the next measure instead. An ending
        // label stays active on every subsequent measure until a new bar
        // token introduces a different one (see applyBarToken).
        private boolean pendingLeadingRepeatStart;
        private String currentEndingLabel;

        /** Grace-note pitches captured from a "{...}" run, attached to the next emitted note. */
        private final List<Pitch> pendingGraceNotes = new ArrayList<>();

        // Pending broken rhythm to apply to the next emitted note (from > or <
        // encountered immediately after a note).
        private AbcNoteLength.Fraction nextLengthMultiplier;

        // Tie: whether the next emitted note should receive tieStop=true.
        private boolean tieToNext;

        // Slur bookkeeping.
        private int slurDepth;

        // Tuplet bookkeeping.
        private int tupletCountRemaining;
        private int tupletActualNotes;
        private int tupletNormalNotes;
        private boolean tupletStartPending;
        private int tupletNumberCounter;
        private int currentTupletNumber;
        /**
         * Whether the current tuplet needs an explicit bracket line: true
         * unless a lookahead at the tuplet's start found its whole run
         * beaming cleanly together (matching the ABC/notation convention of
         * showing just the number over an already-beamed tuplet).
         */
        private boolean tupletBracket = true;

        // Chord accumulation.
        private boolean inChord;
        private final List<Note> chordNotes = new ArrayList<>();

        // Beam grouping: notes accumulated since the last beam break (a rest,
        // whitespace, bar line, or a note too long to beam). Flushed into
        // Beam entries once the group is known to be complete.
        private final List<Note> beamGroup = new ArrayList<>();

        // Within-measure explicit accidentals; keyed by pitch-letter+octave.
        private final Map<String, Integer> measureAccidentals = new HashMap<>();

        // Post-tune W: text lines collected while parsing the body.
        private final List<String> postTuneText = new ArrayList<>();

        BodyParser(TuneHeader header, boolean isPrimaryTune) {
            String id = "P" + header.referenceNumber;
            // The primary tune's title already renders as the score's work
            // title (see AbcReader#parseTune); reusing it as the part name
            // too would print it a second time as a per-system staff label.
            // Later tunes in a multi-tune file have no such heading, so their
            // titles double as the part label that tells them apart.
            this.part = Part.builder(id).name(isPrimaryTune ? null : header.title);
            this.unitLength = header.unitLength != null
                    ? header.unitLength
                    : defaultUnitLength(header.timeSignature);
            this.key = header.key != null ? header.key : KeySignature.cMajor();
            this.timeSignature = header.timeSignature != null
                    ? header.timeSignature
                    : new TimeSignature(4, 4);
            this.pendingTempo = header.tempo;
            startMeasure();
        }

        Part buildPart() {
            commitLine();
            for (String line : postTuneText) {
                part.addPostTuneText(line);
            }
            return part.build();
        }

        void finish() {
            closeChordIfOpen();
            flushBeamGroup();
            closeMeasure();
            commitLine();
        }

        /** Append a raw {@code W:} field value to the part-level post-tune text. */
        void addPostTuneText(String line) {
            postTuneText.add(line == null ? "" : line);
        }

        private void startMeasure() {
            currentMeasure = new PendingMeasure(measureNumber);
            pendingElements = currentMeasure.elements;
            currentMeasure.ending = currentEndingLabel;
            if (pendingLeadingRepeatStart) {
                currentMeasure.leadingRepeatStart = true;
                pendingLeadingRepeatStart = false;
            }
            if (!firstMeasureFlushed && lineMeasures.isEmpty()) {
                currentMeasure.attributes = Attributes.builder()
                        .divisions(DIVISIONS)
                        .keySignature(key)
                        .timeSignature(timeSignature)
                        .clef(Clef.treble())
                        .build();
                if (pendingTempo != null) {
                    currentMeasure.elements.add(new Direction(pendingTempo,
                            com.sheetmusic4j.core.model.Placement.ABOVE));
                    pendingTempo = null;
                }
            }
        }

        /** Close the current measure and add it to the line's queue. */
        private void closeMeasure() {
            if (currentMeasure == null) {
                return;
            }
            measureAccidentals.clear();
            // A barline with no preceding notes at the very start of the
            // tune (e.g. a decorative leading "[|", or a repeat-start "|:"
            // opening the whole part) is not itself an empty pickup measure
            // - drop it and let the next startMeasure() re-derive the same
            // opening attributes.
            boolean leadingDecorative = currentMeasure.elements.isEmpty()
                    && lineMeasures.isEmpty() && !firstMeasureFlushed;
            if (!leadingDecorative && (!currentMeasure.elements.isEmpty() || currentMeasure.attributes != null)) {
                lineMeasures.add(currentMeasure);
                measureNumber++;
            }
            currentMeasure = null;
        }

        /** Emit all measures buffered during the current music line. */
        private void commitLine() {
            for (PendingMeasure pm : lineMeasures) {
                Measure.Builder b = Measure.builder(pm.number);
                if (pm.attributes != null) {
                    b.attributes(pm.attributes);
                }
                for (MusicElement el : pm.elements) {
                    b.addElement(el);
                }
                if (pm.barline != null) {
                    b.barline(pm.barline);
                }
                b.leadingRepeatStart(pm.leadingRepeatStart);
                if (pm.ending != null) {
                    b.ending(pm.ending);
                }
                if (pm.sectionTitle != null) {
                    b.sectionTitle(pm.sectionTitle);
                }
                b.forceSystemBreak(pm.forceSystemBreak);
                part.addMeasure(b.build());
                firstMeasureFlushed = true;
            }
            lineMeasures.clear();
        }

        /**
         * Classify a captured bar-line token and apply its style/repeat to
         * the measure being closed. A bare "|:" has nothing to attach
         * trailing dots to (see {@link #pendingLeadingRepeatStart}), so it
         * only sets a pending flag consumed by the next {@link #startMeasure()}.
         */
        private void applyBarToken(String token) {
            switch (token) {
                case "||" -> currentMeasure.barline = new Barline(Barline.Style.DOUBLE, Barline.Repeat.NONE);
                case "|]" -> currentMeasure.barline = new Barline(Barline.Style.FINAL, Barline.Repeat.NONE);
                case ":|" -> currentMeasure.barline = new Barline(Barline.Style.FINAL, Barline.Repeat.BACKWARD);
                case "::" -> currentMeasure.barline = new Barline(Barline.Style.DOUBLE, Barline.Repeat.BOTH);
                case "|:" -> pendingLeadingRepeatStart = true;
                default -> {
                    // "|", the decorative "[|", or an unrecognized run of
                    // bar punctuation: a plain barline.
                }
            }
        }

        /** Mutable measure state accumulated during a music line. */
        private static final class PendingMeasure {
            final int number;
            Attributes attributes;
            final List<MusicElement> elements = new ArrayList<>();
            Barline barline;
            boolean leadingRepeatStart;
            String ending;
            String sectionTitle;
            boolean forceSystemBreak;

            PendingMeasure(int number) {
                this.number = number;
            }
        }

        /**
         * A mid-tune K:/M: change must show up as a fresh {@code <attributes>}
         * block, same as MusicXML represents an attribute change mid-piece -
         * otherwise the new key/time signature is tracked in parser state but
         * never reaches the model. {@code currentMeasure} is always the
         * not-yet-populated measure this change applies to: {@link #startMeasure()}
         * already ran for it (from the previous line's closing barline) before
         * this field line is seen, same as the {@code T:} mid-tune handling
         * above mutates {@code currentMeasure} directly.
         *
         * <p>Also forces a new system, same as a mid-tune {@code T:} does:
         * this engraver only ever draws a key/time signature at the start of
         * a system, so a change that landed mid-row would otherwise never be
         * drawn at all.
         */
        private void applyMidTuneAttributesChange() {
            if (currentMeasure == null) {
                return;
            }
            currentMeasure.attributes = Attributes.builder()
                    .divisions(DIVISIONS)
                    .keySignature(key)
                    .timeSignature(timeSignature)
                    .clef(Clef.treble())
                    .build();
            currentMeasure.forceSystemBreak = true;
        }

        void applyMidTuneField(char field, String value) {
            switch (field) {
                case 'K' -> {
                    KeySignature ks = AbcKey.parse(value);
                    if (ks != null) {
                        this.key = ks;
                        applyMidTuneAttributesChange();
                    }
                }
                case 'M' -> {
                    TimeSignature ts = parseMeter(value);
                    if (ts != null) {
                        this.timeSignature = ts;
                        applyMidTuneAttributesChange();
                    }
                }
                case 'L' -> {
                    AbcNoteLength.Fraction f = parseUnitLength(value);
                    if (f != null) {
                        this.unitLength = f;
                    }
                }
                case 'Q' -> {
                    DirectionType.Metronome m = parseTempo(value);
                    if (m != null) {
                        pendingElements.add(new Direction(m, com.sheetmusic4j.core.model.Placement.ABOVE));
                    }
                }
                case 'T' -> {
                    // A mid-tune T: is a section title: displayed above the
                    // system that starts with the current measure (already
                    // open and still empty - T: only ever appears at a
                    // measure boundary), forced onto a fresh row so the
                    // title reads as a heading rather than floating over
                    // mid-line content.
                    if (!value.isBlank() && currentMeasure != null) {
                        currentMeasure.sectionTitle = value;
                        currentMeasure.forceSystemBreak = true;
                    }
                }
                default -> {
                    // ignored
                }
            }
        }

        void applyLyrics(String line) {
            if (line == null || line.isEmpty() || lyricNoteAnchors.isEmpty()) {
                return;
            }
            List<String> syllables = splitLyricSyllables(line);
            int idx = 0;
            for (String syl : syllables) {
                if (idx >= lyricNoteAnchors.size()) {
                    break;
                }
                if (syl.equals("*") || syl.equals("_")) {
                    // '*' = skip note; '_' = hold syllable (treated as skip
                    // for MVP).
                    idx++;
                    continue;
                }
                if (syl.equals("|")) {
                    // Advance to next measure boundary — approximate by
                    // consuming remaining anchors until measure boundary is
                    // reached. For MVP just consume none extra.
                    continue;
                }
                Syllabic syllabic = Syllabic.SINGLE;
                String text = syl;
                boolean hasBegin = false;
                boolean hasEnd = false;
                if (text.endsWith("-")) {
                    hasBegin = true;
                    text = text.substring(0, text.length() - 1);
                }
                if (text.startsWith("-")) {
                    hasEnd = true;
                    text = text.substring(1);
                }
                if (hasBegin && hasEnd) {
                    syllabic = Syllabic.MIDDLE;
                } else if (hasBegin) {
                    syllabic = Syllabic.BEGIN;
                } else if (hasEnd) {
                    syllabic = Syllabic.END;
                }
                Note anchor = lyricNoteAnchors.get(idx);
                Note replaced = attachLyric(anchor, new Lyric(text, syllabic, 1));
                replaceAnchor(idx, replaced);
                idx++;
            }
        }

        private List<String> splitLyricSyllables(String line) {
            List<String> out = new ArrayList<>();
            int i = 0;
            int n = line.length();
            StringBuilder sb = new StringBuilder();
            while (i < n) {
                char c = line.charAt(i);
                if (c == ' ' || c == '\t') {
                    if (sb.length() > 0) {
                        out.add(sb.toString());
                        sb.setLength(0);
                    }
                    i++;
                } else if (c == '~') {
                    // '~' is a hard space within a syllable in ABC lyrics.
                    sb.append(' ');
                    i++;
                } else if (c == '\\' && i + 1 < n) {
                    sb.append(line.charAt(i + 1));
                    i += 2;
                } else {
                    sb.append(c);
                    i++;
                }
            }
            if (sb.length() > 0) {
                out.add(sb.toString());
            }
            return out;
        }

        private Note attachLyric(Note note, Lyric lyric) {
            List<Lyric> merged = new ArrayList<>(note.lyrics());
            merged.add(lyric);
            return rebuildNote(note, b -> b.lyrics(merged));
        }

        private void replaceAnchor(int idx, Note replaced) {
            Note original = lyricNoteAnchors.get(idx);
            lyricNoteAnchors.set(idx, replaced);
            // Search across all pending measures for the original note
            // reference and substitute the rebuilt one. Lyric application
            // happens after the whole music line has been parsed, so the
            // target note may live in an earlier bar of the current line.
            if (replaceIn(pendingElements, original, replaced)) {
                return;
            }
            for (PendingMeasure pm : lineMeasures) {
                if (replaceIn(pm.elements, original, replaced)) {
                    return;
                }
            }
        }

        private static boolean replaceIn(List<MusicElement> list, Note original, Note replaced) {
            for (int i = list.size() - 1; i >= 0; i--) {
                MusicElement el = list.get(i);
                if (el == original) {
                    list.set(i, replaced);
                    return true;
                }
                if (el instanceof Chord chord) {
                    List<Note> notes = chord.notes();
                    for (int j = 0; j < notes.size(); j++) {
                        if (notes.get(j) == original) {
                            List<Note> newNotes = new ArrayList<>(notes);
                            newNotes.set(j, replaced);
                            list.set(i, new Chord(newNotes));
                            return true;
                        }
                    }
                }
            }
            return false;
        }

        void parseLine(String line) {
            // Committing any measures from the previous music line now (before
            // clearing anchors) means any pending w: line has already been
            // consumed via applyLyrics.
            commitLine();
            // Drop a trailing line-continuation backslash if present.
            String work = line;
            if (work.endsWith("\\")) {
                work = work.substring(0, work.length() - 1);
            }
            // Reset the lyric anchor list at the start of each music line so
            // a following w: line attaches only to notes on the current line.
            lyricNoteAnchors.clear();
            int i = 0;
            int n = work.length();
            boolean lastWasNote = false;
            while (i < n) {
                char c = work.charAt(i);
                if (c == ' ' || c == '\t') {
                    // Whitespace is the primary ABC beam break: end whatever
                    // beam group is in progress.
                    i++;
                    lastWasNote = false;
                    flushBeamGroup();
                    continue;
                }
                if (c == '%') {
                    break;
                }
                if (c == '|' || c == ':' || (c == '[' && i + 1 < n && work.charAt(i + 1) == '|')) {
                    // Bar line: '|', '||', '|]', '|:', ':|', '::', or a
                    // decorative leading '[|' (thick-thin mark occasionally
                    // used to open a tune) - never a chord, even though it
                    // starts with '['.
                    int j = i;
                    while (j < n && (work.charAt(j) == '|' || work.charAt(j) == ':'
                            || work.charAt(j) == ']' || work.charAt(j) == '[')) {
                        // A '[' beyond the very first character could be an
                        // inline field abutting the barline with no space
                        // (e.g. "|[K:D]") - stop before swallowing it.
                        if (j > i && work.charAt(j) == '[' && isInlineField(work, j)) {
                            break;
                        }
                        j++;
                    }
                    String token = work.substring(i, j);
                    // A first/second-ending number immediately follows some
                    // bar tokens (e.g. "|1", ":|2") with no separating space.
                    int k = j;
                    while (k < n && Character.isDigit(work.charAt(k))) {
                        k++;
                    }
                    String endingLabel = k > j ? work.substring(j, k) : null;
                    closeChordIfOpen();
                    flushBeamGroup();
                    applyBarToken(token);
                    // A bar in the middle of a body closes the current
                    // measure and starts a new one.
                    closeMeasure();
                    if (endingLabel != null) {
                        currentEndingLabel = endingLabel;
                    }
                    startMeasure();
                    lastWasNote = false;
                    i = k;
                    continue;
                }
                if (c == '[' && isInlineField(work, i)) {
                    // Inline field like [K:...], [M:...], [L:...], [Q:...]
                    int close = work.indexOf(']', i);
                    if (close < 0) {
                        i = n;
                        continue;
                    }
                    String content = work.substring(i + 1, close);
                    if (content.length() >= 2 && content.charAt(1) == ':') {
                        applyMidTuneField(content.charAt(0), content.substring(2).trim());
                    }
                    i = close + 1;
                    lastWasNote = false;
                    continue;
                }
                if (c == '[') {
                    // Start of a chord.
                    closeChordIfOpen();
                    inChord = true;
                    chordNotes.clear();
                    i++;
                    continue;
                }
                if (c == ']') {
                    closeChordIfOpen();
                    i++;
                    lastWasNote = false;
                    continue;
                }
                if (c == '(') {
                    // Could be tuplet "(3", "(2:3", "(3:2:6" or a slur "("
                    if (i + 1 < n && Character.isDigit(work.charAt(i + 1))) {
                        int j = i + 1;
                        int actual = 0;
                        while (j < n && Character.isDigit(work.charAt(j))) {
                            actual = actual * 10 + (work.charAt(j) - '0');
                            j++;
                        }
                        int normal = defaultNormalNotes(actual, timeSignature);
                        int count = actual;
                        if (j < n && work.charAt(j) == ':') {
                            // (p:q:r
                            j++;
                            int val = 0;
                            boolean hasVal = false;
                            while (j < n && Character.isDigit(work.charAt(j))) {
                                val = val * 10 + (work.charAt(j) - '0');
                                hasVal = true;
                                j++;
                            }
                            if (hasVal) {
                                normal = val;
                            }
                            if (j < n && work.charAt(j) == ':') {
                                j++;
                                int val2 = 0;
                                boolean hasVal2 = false;
                                while (j < n && Character.isDigit(work.charAt(j))) {
                                    val2 = val2 * 10 + (work.charAt(j) - '0');
                                    hasVal2 = true;
                                    j++;
                                }
                                if (hasVal2) {
                                    count = val2;
                                }
                            }
                        }
                        tupletActualNotes = actual;
                        tupletNormalNotes = normal;
                        tupletCountRemaining = count;
                        tupletStartPending = true;
                        tupletBracket = !tupletRunBeamsCleanly(work, j, count);
                        tupletNumberCounter++;
                        currentTupletNumber = tupletNumberCounter;
                        i = j;
                        continue;
                    }
                    slurDepth++;
                    // Attach slur start to the next emitted note by remembering
                    // depth changes on a per-note basis.
                    pendingSlurStart = true;
                    i++;
                    continue;
                }
                if (c == ')') {
                    if (slurDepth > 0) {
                        slurDepth--;
                        // Retroactively attach the slur stop to the last
                        // emitted note (ABC convention: ')' closes the slur
                        // that ends on the note immediately preceding it).
                        setLastNoteSlurStop();
                    }
                    i++;
                    continue;
                }
                if (c == '{') {
                    // Grace notes: one or more small ornamental pitches
                    // played before the next real note. Captured as plain
                    // pitches (their own length markers are ignored - grace
                    // notes always render as small flagged/beamed eighths)
                    // and attached to whichever note is emitted next.
                    int close = work.indexOf('}', i);
                    String content = close < 0 ? work.substring(i + 1) : work.substring(i + 1, close);
                    pendingGraceNotes.clear();
                    int gi = 0;
                    int gn = content.length();
                    while (gi < gn) {
                        char gc = content.charAt(gi);
                        if (isNoteStartChar(gc)) {
                            ParsedNote parsed = parseNote(content, gi);
                            gi = parsed.consumed;
                            if (!parsed.isRest) {
                                pendingGraceNotes.add(new Pitch(parsed.step, parsed.octave, parsed.alter));
                            }
                        } else {
                            gi++;
                        }
                    }
                    i = close < 0 ? n : close + 1;
                    continue;
                }
                if (c == '"') {
                    // Guitar chord symbol (e.g. "Gm7") or a positioned
                    // annotation string (e.g. "^fine", free text) - only the
                    // former renders; annotations are otherwise dropped.
                    int close = work.indexOf('"', i + 1);
                    String content = close < 0 ? work.substring(i + 1) : work.substring(i + 1, close);
                    Harmony harmony = parseChordSymbol(content);
                    if (harmony != null) {
                        pendingElements.add(harmony);
                    }
                    i = close < 0 ? n : close + 1;
                    continue;
                }
                if (c == '!' || c == '+') {
                    // Decoration wrapped in the same delimiter.
                    int close = work.indexOf(c, i + 1);
                    i = close < 0 ? n : close + 1;
                    continue;
                }
                if (c == '.' || c == '~' || c == 'H' || c == 'L' || c == 'M'
                        || c == 'O' || c == 'P' || c == 'R' || c == 'S'
                        || c == 'T' || c == 'u' || c == 'v') {
                    // ABC shorthand decorations attached to the following
                    // note. Only consume when clearly a decoration prefix
                    // (single char followed by a note letter or accidental).
                    // Beware: capital A-G are note letters — do NOT skip those.
                    // '.', '~', 'u', 'v' map to a known Articulation; H, L, M,
                    // O, P, R, S, T are legacy decorations with no model
                    // representation yet and are just consumed. We check the
                    // next char: if it's a note letter/accidental/octave-mark,
                    // we treat as a decoration; otherwise fall through so
                    // unrelated characters aren't lost.
                    if (i + 1 < n && isNoteStartChar(work.charAt(i + 1))) {
                        Articulation articulation = switch (c) {
                            case '.' -> Articulation.STACCATO;
                            case '~' -> Articulation.ROLL;
                            case 'v' -> Articulation.DOWN_BOW;
                            case 'u' -> Articulation.UP_BOW;
                            default -> null;
                        };
                        if (articulation != null) {
                            pendingArticulations.add(articulation);
                        }
                        i++;
                        continue;
                    }
                }
                if (c == '>' || c == '<') {
                    // Broken rhythm applied to the just-emitted note and the
                    // next note. Repeating the marker (">>", "<<<", ...)
                    // double/triple-dots instead of just single-dotting, so
                    // count the run of identical markers and apply the
                    // compound ratio once rather than the single-dot ratio
                    // once per character.
                    char marker = c;
                    int j = i;
                    while (j < n && work.charAt(j) == marker) {
                        j++;
                    }
                    int count = Math.min(j - i, 20);
                    if (lastWasNote) {
                        int pow = 1 << count;
                        AbcNoteLength.Fraction longer = AbcNoteLength.Fraction.of(2 * pow - 1, pow);
                        AbcNoteLength.Fraction shorter = AbcNoteLength.Fraction.of(1, pow);
                        if (marker == '>') {
                            applyBrokenRhythmLeft(longer);
                            nextLengthMultiplier = shorter;
                        } else {
                            applyBrokenRhythmLeft(shorter);
                            nextLengthMultiplier = longer;
                        }
                    }
                    i = j;
                    continue;
                }
                if (c == '-') {
                    // Tie start on the previously emitted note.
                    if (lastWasNote) {
                        setLastNoteTieStart();
                    }
                    i++;
                    tieToNext = true;
                    continue;
                }
                if (c == '&') {
                    // Voice overlay — MVP treats as end-of-line and continues
                    // in the same measure. Consume.
                    i++;
                    continue;
                }
                if (c == '\\') {
                    i++;
                    continue;
                }
                if (isNoteStartChar(c) || c == 'z' || c == 'x' || c == 'Z' || c == 'X') {
                    ParsedNote parsed = parseNote(work, i);
                    i = parsed.consumed;
                    if (parsed.isRest) {
                        emitRest(parsed);
                        // A rest breaks the beam group it interrupts.
                        flushBeamGroup();
                    } else {
                        emitNote(parsed);
                    }
                    lastWasNote = true;
                    continue;
                }
                // Unknown character — skip conservatively.
                i++;
            }
            flushBeamGroup();
        }

        private boolean pendingSlurStart;

        private final List<Articulation> pendingArticulations = new ArrayList<>();

        private static boolean isInlineField(String line, int at) {
            // Detects [X:...] where X is a single ASCII letter and ':' follows.
            if (at + 2 >= line.length()) {
                return false;
            }
            char c = line.charAt(at + 1);
            if (line.charAt(at + 2) != ':') {
                return false;
            }
            return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z');
        }

        private static boolean isNoteStartChar(char c) {
            return (c >= 'A' && c <= 'G') || (c >= 'a' && c <= 'g')
                    || c == '^' || c == '_' || c == '=';
        }

        /**
         * Side-effect-free lookahead: whether the next {@code count} note
         * tokens starting at {@code from} form one unbroken run - no
         * whitespace, barline, or rest between them - so the beam grouping
         * logic (whitespace/rest flush the beam) will draw them as a single
         * beamed group. Matches the ABC/notation convention of showing just
         * the tuplet number over an already-beamed group, and a full
         * bracket only when the beam wouldn't otherwise make the grouping
         * clear.
         */
        private static boolean tupletRunBeamsCleanly(String line, int from, int count) {
            int i = from;
            int n = line.length();
            int found = 0;
            while (i < n && found < count) {
                char c = line.charAt(i);
                if (c == ' ' || c == '\t' || c == '|' || c == ':' || c == '[' || c == ']'
                        || c == 'z' || c == 'x' || c == 'Z' || c == 'X') {
                    return false;
                }
                if (isNoteStartChar(c)) {
                    int consumed = skipNoteToken(line, i);
                    if (consumed == i) {
                        return false;
                    }
                    i = consumed;
                    found++;
                    continue;
                }
                if (c == '{') {
                    int close = line.indexOf('}', i);
                    i = close < 0 ? n : close + 1;
                } else if (c == '"') {
                    int close = line.indexOf('"', i + 1);
                    i = close < 0 ? n : close + 1;
                } else if (c == '!' || c == '+') {
                    int close = line.indexOf(c, i + 1);
                    i = close < 0 ? n : close + 1;
                } else {
                    i++;
                }
            }
            return found >= count;
        }

        /** Side-effect-free scan over one note token's accidental/letter/octave/length span. */
        private static int skipNoteToken(String line, int start) {
            int i = start;
            int n = line.length();
            while (i < n && (line.charAt(i) == '^' || line.charAt(i) == '_' || line.charAt(i) == '=')) {
                i++;
            }
            if (i >= n || !Character.isLetter(line.charAt(i))) {
                return start;
            }
            i++;
            while (i < n && (line.charAt(i) == '\'' || line.charAt(i) == ',')) {
                i++;
            }
            AbcNoteLength.Parsed p = AbcNoteLength.parseSuffix(line, i);
            return i + p.consumed();
        }

        /** All data captured for a single ABC note or rest token. */
        private static final class ParsedNote {
            Step step;
            int octave;
            int alter;
            boolean explicitAccidental;
            AbcNoteLength.Fraction length;
            boolean isRest;
            boolean isMeasureRest;
            int consumed;
        }

        private ParsedNote parseNote(String line, int start) {
            ParsedNote out = new ParsedNote();
            int i = start;
            int n = line.length();

            // Accidental prefix: ^, ^^, =, _, __
            int alter = 0;
            boolean hasAccidental = false;
            while (i < n) {
                char c = line.charAt(i);
                if (c == '^') {
                    alter += 1;
                    hasAccidental = true;
                    i++;
                } else if (c == '_') {
                    alter -= 1;
                    hasAccidental = true;
                    i++;
                } else if (c == '=') {
                    alter = 0;
                    hasAccidental = true;
                    i++;
                } else {
                    break;
                }
            }
            if (i >= n) {
                out.consumed = i;
                return out;
            }
            char letter = line.charAt(i);
            i++;

            if (letter == 'z' || letter == 'x') {
                out.isRest = true;
                AbcNoteLength.Parsed p = AbcNoteLength.parseSuffix(line, i);
                i += p.consumed();
                out.length = p.multiplier();
                out.consumed = i;
                return out;
            }
            if (letter == 'Z' || letter == 'X') {
                // Whole-measure rest (Z), possibly repeated (Z2 = 2 measures).
                out.isRest = true;
                out.isMeasureRest = true;
                AbcNoteLength.Parsed p = AbcNoteLength.parseSuffix(line, i);
                i += p.consumed();
                // For simplicity multiply the multiplier by the number of
                // measures the rest spans in the numerator; downstream we
                // convert this to full-measure durations.
                out.length = p.multiplier();
                out.consumed = i;
                return out;
            }

            Step step;
            int octave;
            if (letter >= 'A' && letter <= 'G') {
                step = Step.valueOf(String.valueOf(letter));
                octave = 4;
            } else if (letter >= 'a' && letter <= 'g') {
                step = Step.valueOf(String.valueOf(Character.toUpperCase(letter)));
                octave = 5;
            } else {
                // Unknown letter — treat as rest to keep parser moving.
                out.isRest = true;
                out.length = new AbcNoteLength.Fraction(1, 1);
                out.consumed = i;
                return out;
            }

            // Octave marks: ' raises, , lowers (only immediately after letter).
            while (i < n) {
                char c = line.charAt(i);
                if (c == '\'') {
                    octave++;
                    i++;
                } else if (c == ',') {
                    octave--;
                    i++;
                } else {
                    break;
                }
            }

            AbcNoteLength.Parsed p = AbcNoteLength.parseSuffix(line, i);
            i += p.consumed();

            if (!hasAccidental) {
                // Resolve alteration from measure carry, then key signature.
                String carryKey = step.name() + octave;
                Integer carry = measureAccidentals.get(carryKey);
                if (carry != null) {
                    alter = carry;
                } else {
                    alter = AbcKey.alterFor(step, this.key);
                }
            } else {
                // Remember the explicit accidental for subsequent notes in
                // this measure (same letter + octave).
                measureAccidentals.put(step.name() + octave, alter);
            }

            out.step = step;
            out.octave = octave;
            out.alter = alter;
            out.explicitAccidental = hasAccidental;
            out.length = p.multiplier();
            out.consumed = i;
            return out;
            }

        private void emitRest(ParsedNote parsed) {
            AbcNoteLength.Fraction m = parsed.length;
            if (parsed.isMeasureRest) {
                // Full-measure rest — one measure per unit of m.num (denom
                // ignored). Emit m.num measures each holding one measure-long
                // rest, but at minimum one.
                int measures = Math.max(1, m.num());
                for (int k = 0; k < measures; k++) {
                    Rest rest = Rest.builder()
                            .duration(measureDuration())
                            .type(NoteType.WHOLE)
                            .build();
                    pendingElements.add(rest);
                    lyricNoteAnchors.add(null);
                    if (k < measures - 1) {
                        closeMeasure();
                        startMeasure();
                    }
                }
                return;
            }
            AbcNoteLength.Fraction effective = m;
            if (nextLengthMultiplier != null) {
                effective = effective.times(nextLengthMultiplier);
                nextLengthMultiplier = null;
            }
            if (tupletCountRemaining > 0) {
                effective = effective.times(tupletNormalNotes, tupletActualNotes);
            }
            Duration duration = toDuration(effective);
            Rest.Builder rb = Rest.builder().duration(duration);
            setTypeAndDots(rb, duration);
            pendingElements.add(rb.build());
            lyricNoteAnchors.add(null);
        }

        private void emitNote(ParsedNote parsed) {
            AbcNoteLength.Fraction m = parsed.length;
            if (nextLengthMultiplier != null) {
                m = m.times(nextLengthMultiplier);
                nextLengthMultiplier = null;
            }
            TimeModification timeMod = null;
            List<Tuplet> tuplets = new ArrayList<>();
            if (tupletCountRemaining > 0) {
                m = m.times(tupletNormalNotes, tupletActualNotes);
                timeMod = new TimeModification(tupletActualNotes, tupletNormalNotes);
                if (tupletStartPending) {
                    tuplets.add(new Tuplet(currentTupletNumber, Tuplet.Type.START, tupletBracket));
                    tupletStartPending = false;
                }
                if (tupletCountRemaining == 1) {
                    tuplets.add(new Tuplet(currentTupletNumber, Tuplet.Type.STOP, tupletBracket));
                }
                tupletCountRemaining--;
            }
            Duration duration = toDuration(m);
            Pitch pitch = new Pitch(parsed.step, parsed.octave, parsed.alter);
            Note.Builder b = Note.builder()
                    .pitch(pitch)
                    .duration(duration);
            setTypeAndDots(b, duration);
            if (parsed.explicitAccidental) {
                b.displayedAccidental(Accidental.fromAlter(parsed.alter));
            }
            if (timeMod != null) {
                b.timeModification(timeMod);
                for (Tuplet t : tuplets) {
                    b.addTuplet(t);
                }
            }
            if (tieToNext) {
                b.tieStop(true);
                tieToNext = false;
            }
            if (pendingSlurStart) {
                b.addSlur(new Slur(1, Slur.Type.START, com.sheetmusic4j.core.model.Placement.DEFAULT));
                pendingSlurStart = false;
            }
            if (!pendingArticulations.isEmpty()) {
                for (Articulation articulation : pendingArticulations) {
                    b.addArticulation(articulation);
                }
                pendingArticulations.clear();
            }
            if (!inChord && !pendingGraceNotes.isEmpty()) {
                b.graceNotes(pendingGraceNotes);
                pendingGraceNotes.clear();
            }
            Note note = b.build();
            if (inChord) {
                chordNotes.add(note);
            } else {
                pendingElements.add(note);
                lyricNoteAnchors.add(note);
                addToBeamGroup(note);
            }
        }

        private void closeChordIfOpen() {
            if (!inChord) {
                return;
            }
            inChord = false;
            if (chordNotes.isEmpty()) {
                return;
            }
            if (chordNotes.size() == 1) {
                Note only = chordNotes.get(0);
                pendingElements.add(only);
                lyricNoteAnchors.add(only);
                addToBeamGroup(only);
            } else {
                Chord chord = new Chord(chordNotes);
                pendingElements.add(chord);
                lyricNoteAnchors.add(chordNotes.get(0));
                // MusicXML tags exactly one representative note per chord
                // with <beam> data (see Engraver#placeElement); mirror that
                // here by beaming only the chord's first note.
                addToBeamGroup(chordNotes.get(0));
            }
            chordNotes.clear();
        }

        private void applyBrokenRhythmLeft(AbcNoteLength.Fraction mul) {
            // Find the last emitted note/rest and rescale its duration.
            for (int i = pendingElements.size() - 1; i >= 0; i--) {
                MusicElement el = pendingElements.get(i);
                if (el instanceof Note note) {
                    Duration scaled = scale(note.duration(), mul);
                    Note replaced = rebuildNote(note, nb -> {
                        nb.duration(scaled);
                        setTypeAndDots(nb, scaled);
                    });
                    pendingElements.set(i, replaced);
                    updateBeamGroupReference(note, replaced);
                    // Update lyric anchor too if the same note reference.
                    for (int j = 0; j < lyricNoteAnchors.size(); j++) {
                        if (lyricNoteAnchors.get(j) == note) {
                            lyricNoteAnchors.set(j, replaced);
                        }
                    }
                    return;
                }
                if (el instanceof Rest rest) {
                    Duration scaled = scale(rest.duration(), mul);
                    Rest.Builder rb = Rest.builder().duration(scaled);
                    setTypeAndDots(rb, scaled);
                    pendingElements.set(i, rb.build());
                    return;
                }
                if (el instanceof Chord chord) {
                    List<Note> newNotes = new ArrayList<>();
                    for (Note note : chord.notes()) {
                        Duration scaled = scale(note.duration(), mul);
                        newNotes.add(rebuildNote(note, nb -> {
                            nb.duration(scaled);
                            setTypeAndDots(nb, scaled);
                        }));
                    }
                    pendingElements.set(i, new Chord(newNotes));
                    if (!chord.notes().isEmpty()) {
                        updateBeamGroupReference(chord.notes().get(0), newNotes.get(0));
                    }
                    return;
                }
            }
        }

        /**
         * Add a note to the beam group in progress, or flush the group first
         * if the note is too long to beam (a quarter note or longer).
         */
        private void addToBeamGroup(Note note) {
            if (beamLevelsFor(note.type()) >= 1) {
                beamGroup.add(note);
            } else {
                flushBeamGroup();
            }
        }

        /** Keep the beam group's reference in sync after a note is rebuilt in place. */
        private void updateBeamGroupReference(Note original, Note replaced) {
            if (!beamGroup.isEmpty() && beamGroup.get(beamGroup.size() - 1) == original) {
                beamGroup.set(beamGroup.size() - 1, replaced);
            }
        }

        /**
         * Turn the accumulated beam group into {@link Beam} entries and
         * splice the rebuilt notes back into {@code pendingElements}. A
         * group of fewer than two notes is left unbeamed (a lone eligible
         * note just gets a flag).
         *
         * <p>Beam levels follow the same convention as MusicXML {@code
         * <beam number="N">}: level 1 is the primary beam spanning the whole
         * group, level 2 the secondary beam needed by sixteenths, etc. A
         * note needing a level that a neighbour doesn't gets a hook instead
         * of a shared beam segment for that level.
         */
        private void flushBeamGroup() {
            int size = beamGroup.size();
            if (size < 2) {
                beamGroup.clear();
                return;
            }
            int[] levels = new int[size];
            int maxLevel = 0;
            for (int i = 0; i < size; i++) {
                levels[i] = beamLevelsFor(beamGroup.get(i).type());
                maxLevel = Math.max(maxLevel, levels[i]);
            }
            List<List<Beam>> perNoteBeams = new ArrayList<>();
            for (int i = 0; i < size; i++) {
                perNoteBeams.add(new ArrayList<>());
            }
            for (int level = 1; level <= maxLevel; level++) {
                addBeamLevelRuns(levels, level, perNoteBeams);
            }
            for (int i = 0; i < size; i++) {
                List<Beam> beams = perNoteBeams.get(i);
                if (beams.isEmpty()) {
                    continue;
                }
                Note original = beamGroup.get(i);
                Note replaced = rebuildNote(original, nb -> nb.beams(beams));
                if (replaceIn(pendingElements, original, replaced)) {
                    for (int j = 0; j < lyricNoteAnchors.size(); j++) {
                        if (lyricNoteAnchors.get(j) == original) {
                            lyricNoteAnchors.set(j, replaced);
                        }
                    }
                }
            }
            beamGroup.clear();
        }

        /** Assign one beam level's BEGIN/CONTINUE/END runs (or hooks for isolated notes). */
        private static void addBeamLevelRuns(int[] levels, int level, List<List<Beam>> perNoteBeams) {
            int size = levels.length;
            int i = 0;
            while (i < size) {
                if (levels[i] < level) {
                    i++;
                    continue;
                }
                int start = i;
                int end = i;
                while (end + 1 < size && levels[end + 1] >= level) {
                    end++;
                }
                if (end > start) {
                    perNoteBeams.get(start).add(new Beam(level, Beam.State.BEGIN));
                    for (int k = start + 1; k < end; k++) {
                        perNoteBeams.get(k).add(new Beam(level, Beam.State.CONTINUE));
                    }
                    perNoteBeams.get(end).add(new Beam(level, Beam.State.END));
                } else {
                    boolean hasPrevInGroup = start > 0;
                    Beam.State hookState = hasPrevInGroup ? Beam.State.BACKWARD_HOOK : Beam.State.FORWARD_HOOK;
                    perNoteBeams.get(start).add(new Beam(level, hookState));
                }
                i = end + 1;
            }
        }

        private void setLastNoteSlurStop() {
            for (int i = pendingElements.size() - 1; i >= 0; i--) {
                MusicElement el = pendingElements.get(i);
                if (el instanceof Note note) {
                    Note replaced = rebuildNote(note, nb -> nb.addSlur(
                            new Slur(1, Slur.Type.STOP, com.sheetmusic4j.core.model.Placement.DEFAULT)));
                    pendingElements.set(i, replaced);
                    updateBeamGroupReference(note, replaced);
                    for (int j = 0; j < lyricNoteAnchors.size(); j++) {
                        if (lyricNoteAnchors.get(j) == note) {
                            lyricNoteAnchors.set(j, replaced);
                        }
                    }
                    return;
                }
                if (el instanceof Chord chord) {
                    List<Note> newNotes = new ArrayList<>();
                    Note first = chord.notes().get(0);
                    for (int k = 0; k < chord.notes().size(); k++) {
                        Note note = chord.notes().get(k);
                        if (k == 0) {
                            newNotes.add(rebuildNote(note, nb -> nb.addSlur(
                                    new Slur(1, Slur.Type.STOP, com.sheetmusic4j.core.model.Placement.DEFAULT))));
                        } else {
                            newNotes.add(note);
                        }
                        // reference first only
                        if (note != first) {
                            // keep other notes as-is
                        }
                    }
                    pendingElements.set(i, new Chord(newNotes));
                    return;
                }
            }
        }

        private void setLastNoteTieStart() {
            for (int i = pendingElements.size() - 1; i >= 0; i--) {
                MusicElement el = pendingElements.get(i);
                if (el instanceof Note note) {
                    Note replaced = rebuildNote(note, nb -> nb.tieStart(true));
                    pendingElements.set(i, replaced);
                    updateBeamGroupReference(note, replaced);
                    for (int j = 0; j < lyricNoteAnchors.size(); j++) {
                        if (lyricNoteAnchors.get(j) == note) {
                            lyricNoteAnchors.set(j, replaced);
                        }
                    }
                    return;
                }
                if (el instanceof Chord chord) {
                    List<Note> newNotes = new ArrayList<>();
                    for (Note note : chord.notes()) {
                        newNotes.add(rebuildNote(note, nb -> nb.tieStart(true)));
                    }
                    pendingElements.set(i, new Chord(newNotes));
                    return;
                }
            }
        }

        private Duration toDuration(AbcNoteLength.Fraction m) {
            // quarters = m * 4 * unit.num / unit.den
            // value = quarters * DIVISIONS
            long numerator = (long) m.num() * 4L * unitLength.num() * DIVISIONS;
            long denominator = (long) m.den() * unitLength.den();
            long value = numerator / denominator;
            if (value <= 0) {
                value = 1;
            }
            return new Duration((int) value, DIVISIONS);
        }

        private Duration measureDuration() {
            long value = Math.round(timeSignature.measureLengthInQuarters() * DIVISIONS);
            return new Duration((int) Math.max(1, value), DIVISIONS);
        }

        private static Duration scale(Duration d, AbcNoteLength.Fraction m) {
            long v = (long) d.value() * m.num() / m.den();
            return new Duration((int) Math.max(1, v), d.divisions());
        }

        private static int defaultNormalNotes(int actual, TimeSignature ts) {
            // ABC default (per spec): (2 in triple time, 3 in duple)
            // (2 → 3, (3 → 2, (4 → 3, (5 → n, (6 → 2, (7 → n, (8 → 3, (9 → n
            boolean compound = ts != null && (ts.beats() % 3 == 0 && ts.beats() > 3);
            return switch (actual) {
                case 2 -> 3;
                case 3 -> 2;
                case 4 -> 3;
                case 6 -> 2;
                case 8 -> 3;
                case 5, 7, 9 -> compound ? 3 : 2;
                default -> 2;
            };
        }

        /**
         * Number of beam lines a note of this type needs (1 for an eighth, 2
         * for a sixteenth, ...); {@code 0} for a quarter note or longer,
         * meaning it cannot be part of a beamed group at all.
         */
        private static int beamLevelsFor(NoteType type) {
            int diff = type.ordinal() - NoteType.EIGHTH.ordinal();
            return diff >= 0 ? diff + 1 : 0;
        }

        /** The written note type and dot count closest to a duration expressed in quarters. */
        private record TypeAndDots(NoteType type, int dots) {
        }

        /**
         * Picks the (type, dots) pair whose value is closest to {@code
         * quarters}, checking up to 3 dots per candidate type. Unlike
         * {@link NoteType#fromQuarterValue}, this accounts for dots so a
         * dotted eighth is reported as {@code (EIGHTH, 1)} rather than the
         * dot-less closest type.
         */
        private static TypeAndDots typeAndDotsFor(double quarters) {
            NoteType bestType = NoteType.QUARTER;
            int bestDots = 0;
            double bestDiff = Double.MAX_VALUE;
            for (NoteType type : NoteType.values()) {
                double value = type.quarterValue();
                for (int dots = 0; dots <= 3; dots++) {
                    double diff = Math.abs(value - quarters);
                    if (diff < bestDiff - 1e-9) {
                        bestDiff = diff;
                        bestType = type;
                        bestDots = dots;
                    }
                    value += type.quarterValue() / (1 << (dots + 1));
                }
            }
            return new TypeAndDots(bestType, bestDots);
        }

        private static void setTypeAndDots(Note.Builder b, Duration duration) {
            TypeAndDots td = typeAndDotsFor(duration.inQuarters());
            b.type(td.type()).dots(td.dots());
        }

        private static void setTypeAndDots(Rest.Builder b, Duration duration) {
            TypeAndDots td = typeAndDotsFor(duration.inQuarters());
            b.type(td.type()).dots(td.dots());
        }
    }

    /**
     * Rebuild a {@link Note} with a mutation applied to a fresh builder,
     * copying all state from the original. Used to "modify" immutable notes.
     */
    private static Note rebuildNote(Note original, java.util.function.Consumer<Note.Builder> mutate) {
        Note.Builder b = Note.builder()
                .pitch(original.pitch())
                .duration(original.duration())
                .type(original.type())
                .dots(original.dots())
                .tieStart(original.tieStart())
                .tieStop(original.tieStop())
                .beams(new ArrayList<>(original.beams()))
                .lyrics(new ArrayList<>(original.lyrics()))
                .staff(original.staff())
                .articulations(new ArrayList<>(original.articulations()))
                .slurs(new ArrayList<>(original.slurs()))
                .tuplets(new ArrayList<>(original.tuplets()))
                .graceNotes(new ArrayList<>(original.graceNotes()));
        original.displayedAccidental().ifPresent(b::displayedAccidental);
        original.timeModification().ifPresent(b::timeModification);
        original.stemUp().ifPresent(b::stemUp);
        mutate.accept(b);
        return b.build();
    }
}
