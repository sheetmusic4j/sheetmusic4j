package com.sheetmusic4j.core.lilypond;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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
import com.sheetmusic4j.core.model.ClefSign;
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
 * Reads a LilyPond {@code .ly} document into a {@link Score}.
 *
 * <p>The parser handles the MVP subset described in the module documentation:
 * headers ({@code \header { title composer subtitle }}), {@code \version} /
 * {@code \language} directives, {@code \score} / {@code \book} / {@code \new
 * Staff} / {@code \new Voice} wrappers, pitched notes in Dutch pitch-name
 * notation, rests ({@code r}, {@code R}, {@code s}), numeric durations with
 * dots and duration carry-over, chords ({@code <c e g>4}), ties ({@code ~}),
 * slurs ({@code (...)}), tuplets ({@code \tuplet 3/2 { ... }} and legacy
 * {@code \times 2/3 { ... }}), bar checks ({@code |}) and {@code \bar}
 * commands, per-staff attributes ({@code \time}, {@code \key}, {@code \clef}),
 * tempo, chord symbols ({@code \chordmode}, {@code \chords}) and lyrics
 * ({@code \addlyrics}, {@code \lyricmode}).
 *
 * <p>Unsupported constructs (polyphony {@code << ... >>}, {@code \repeat},
 * ossia, {@code \override}/{@code \set}, most ornaments) are skipped
 * silently so real-world files survive round-tripping structurally.
 */
public final class LilyPondReader {

    /** Divisions per quarter note used by the produced {@link Score}. */
    private static final int DIVISIONS = 96;

    public Score read(Path path) {
        try (InputStream in = Files.newInputStream(path)) {
            return read(in);
        } catch (IOException e) {
            throw new LilyPondException("Could not read LilyPond file: " + path, e);
        }
    }

    public Score read(InputStream in) {
        try {
            String text = readAll(in);
            return parse(text);
        } catch (IOException e) {
            throw new LilyPondException("Failed to read LilyPond stream", e);
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

    Score parse(String rawText) {
        String text = stripComments(rawText);
        Header header = extractHeader(text);

        Score.Builder score = Score.builder();
        if (header.title != null) {
            score.workTitle(header.title);
        }
        if (header.subtitle != null) {
            score.movementTitle(header.subtitle);
        }
        if (header.composer != null) {
            Creator creator = Creator.of("composer", header.composer);
            if (creator != null) {
                score.addCreator(creator);
            }
        }

        String musicExpression = extractMusicExpression(text);
        List<String> addLyricsBlocks = extractAddLyricsBlocks(text);
        List<String> chordBlocks = extractChordModeBlocks(text);

        Part.Builder partBuilder = Part.builder("P1");
        BodyParser body = new BodyParser(partBuilder, DIVISIONS);
        body.parse(musicExpression);
        for (String lyrics : addLyricsBlocks) {
            body.applyLyrics(lyrics);
        }
        List<Harmony> chords = new ArrayList<>();
        for (String chordBody : chordBlocks) {
            chords.addAll(parseChordMode(chordBody));
        }
        body.applyChordSymbols(chords);
        body.finish();
        Part part = partBuilder.build();
        score.addPart(part);
        return score.build();
    }

    // ----- top-level text preprocessing ------------------------------------

    /** Strip LilyPond line comments ({@code % ...}) and block comments ({@code %{ ... %}}). */
    static String stripComments(String text) {
        StringBuilder sb = new StringBuilder(text.length());
        int i = 0;
        int n = text.length();
        while (i < n) {
            char c = text.charAt(i);
            if (c == '%' && i + 1 < n && text.charAt(i + 1) == '{') {
                int j = text.indexOf("%}", i + 2);
                if (j < 0) {
                    return sb.toString();
                }
                i = j + 2;
                continue;
            }
            if (c == '%') {
                while (i < n && text.charAt(i) != '\n') {
                    i++;
                }
                continue;
            }
            if (c == '"') {
                sb.append(c);
                i++;
                while (i < n && text.charAt(i) != '"') {
                    if (text.charAt(i) == '\\' && i + 1 < n) {
                        sb.append(text.charAt(i)).append(text.charAt(i + 1));
                        i += 2;
                    } else {
                        sb.append(text.charAt(i));
                        i++;
                    }
                }
                if (i < n) {
                    sb.append(text.charAt(i));
                    i++;
                }
                continue;
            }
            sb.append(c);
            i++;
        }
        return sb.toString();
    }

    /** Find the {@code '}'} matching the {@code '{'} at {@code openPos}. */
    static int findMatchingBrace(String text, int openPos) {
        int depth = 0;
        int i = openPos;
        int n = text.length();
        while (i < n) {
            char c = text.charAt(i);
            if (c == '"') {
                i++;
                while (i < n && text.charAt(i) != '"') {
                    if (text.charAt(i) == '\\' && i + 1 < n) {
                        i += 2;
                    } else {
                        i++;
                    }
                }
                i++;
                continue;
            }
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
            i++;
        }
        return -1;
    }

    private static Header extractHeader(String text) {
        Header h = new Header();
        int p = indexOfTopLevelDirective(text, "\\header");
        if (p < 0) {
            return h;
        }
        int brace = text.indexOf('{', p);
        if (brace < 0) {
            return h;
        }
        int close = findMatchingBrace(text, brace);
        if (close < 0) {
            return h;
        }
        String body = text.substring(brace + 1, close);
        parseHeaderAssignments(body, h);
        return h;
    }

    private static void parseHeaderAssignments(String body, Header h) {
        int i = 0;
        int n = body.length();
        while (i < n) {
            while (i < n && Character.isWhitespace(body.charAt(i))) {
                i++;
            }
            if (i >= n) {
                break;
            }
            int keyStart = i;
            while (i < n && (Character.isLetterOrDigit(body.charAt(i)) || body.charAt(i) == '_')) {
                i++;
            }
            String key = body.substring(keyStart, i);
            while (i < n && Character.isWhitespace(body.charAt(i))) {
                i++;
            }
            if (i >= n || body.charAt(i) != '=') {
                // Not an assignment — skip to next line.
                while (i < n && body.charAt(i) != '\n') {
                    i++;
                }
                continue;
            }
            i++; // consume '='
            while (i < n && Character.isWhitespace(body.charAt(i))) {
                i++;
            }
            if (i < n && body.charAt(i) == '"') {
                int strStart = i + 1;
                i = strStart;
                StringBuilder sb = new StringBuilder();
                while (i < n && body.charAt(i) != '"') {
                    if (body.charAt(i) == '\\' && i + 1 < n) {
                        sb.append(body.charAt(i + 1));
                        i += 2;
                    } else {
                        sb.append(body.charAt(i));
                        i++;
                    }
                }
                if (i < n) {
                    i++; // consume closing '"'
                }
                String value = sb.toString();
                switch (key.toLowerCase(Locale.ROOT)) {
                    case "title" -> h.title = value;
                    case "subtitle" -> h.subtitle = value;
                    case "composer" -> h.composer = value;
                    default -> {
                        // ignore unknown header keys
                    }
                }
            } else {
                // Non-string value — skip to newline.
                while (i < n && body.charAt(i) != '\n') {
                    i++;
                }
            }
        }
    }

    /**
     * The index of a directive occurrence outside of any braces (i.e. at the
     * file's top level), or {@code -1} if none.
     */
    private static int indexOfTopLevelDirective(String text, String directive) {
        int depth = 0;
        int i = 0;
        int n = text.length();
        while (i < n) {
            char c = text.charAt(i);
            if (c == '"') {
                i++;
                while (i < n && text.charAt(i) != '"') {
                    if (text.charAt(i) == '\\' && i + 1 < n) {
                        i += 2;
                    } else {
                        i++;
                    }
                }
                i++;
                continue;
            }
            if (c == '{') {
                depth++;
                i++;
                continue;
            }
            if (c == '}') {
                if (depth > 0) {
                    depth--;
                }
                i++;
                continue;
            }
            if (depth == 0 && c == '\\' && text.regionMatches(i, directive, 0, directive.length())) {
                int after = i + directive.length();
                if (after >= n || !Character.isLetterOrDigit(text.charAt(after))) {
                    return i;
                }
            }
            i++;
        }
        return -1;
    }

    /**
     * Extract the primary music expression: the contents of the first
     * {@code \new Staff} / {@code \new Voice} block found (searching first
     * inside {@code \score {…}}, then falling back to the whole file if no
     * {@code \score} is present).
     */
    private static String extractMusicExpression(String text) {
        int scorePos = text.indexOf("\\score");
        String searchScope = text;
        if (scorePos >= 0) {
            int brace = text.indexOf('{', scorePos);
            if (brace >= 0) {
                int close = findMatchingBrace(text, brace);
                if (close > 0) {
                    searchScope = text.substring(brace + 1, close);
                }
            }
        }
        // Prefer \new Staff, then \new Voice, then \relative, then the
        // outer brace of the search scope.
        for (String needle : new String[]{"\\new Staff", "\\new Voice"}) {
            int p = searchScope.indexOf(needle);
            if (p >= 0) {
                int brace = searchScope.indexOf('{', p);
                if (brace >= 0) {
                    int close = findMatchingBrace(searchScope, brace);
                    if (close > 0) {
                        return searchScope.substring(brace + 1, close);
                    }
                }
            }
        }
        return searchScope;
    }

    /** All top-level {@code \addlyrics { … }} / {@code \lyricmode { … }} blocks. */
    private static List<String> extractAddLyricsBlocks(String text) {
        List<String> out = new ArrayList<>();
        for (String directive : new String[]{"\\addlyrics", "\\lyricmode", "\\lyrics"}) {
            int from = 0;
            while (from < text.length()) {
                int p = text.indexOf(directive, from);
                if (p < 0) {
                    break;
                }
                int after = p + directive.length();
                // Ensure we didn't just match a longer identifier prefix.
                if (after < text.length() && Character.isLetterOrDigit(text.charAt(after))) {
                    from = after;
                    continue;
                }
                int brace = text.indexOf('{', p);
                if (brace < 0) {
                    break;
                }
                int close = findMatchingBrace(text, brace);
                if (close < 0) {
                    break;
                }
                out.add(text.substring(brace + 1, close));
                from = close + 1;
            }
        }
        return out;
    }

    private static List<String> extractChordModeBlocks(String text) {
        List<String> out = new ArrayList<>();
        for (String directive : new String[]{"\\chordmode", "\\chords"}) {
            int from = 0;
            while (from < text.length()) {
                int p = text.indexOf(directive, from);
                if (p < 0) {
                    break;
                }
                int after = p + directive.length();
                if (after < text.length() && Character.isLetterOrDigit(text.charAt(after))) {
                    from = after;
                    continue;
                }
                int brace = text.indexOf('{', p);
                if (brace < 0) {
                    break;
                }
                int close = findMatchingBrace(text, brace);
                if (close < 0) {
                    break;
                }
                out.add(text.substring(brace + 1, close));
                from = close + 1;
            }
        }
        return out;
    }

    // ----- chord-mode -------------------------------------------------------

    /**
     * Shorthand chord-quality tokens (as written after the root letter in a
     * LilyPond chord-mode expression, e.g. {@code c1:m7}) mapped to the
     * matching {@link HarmonyKind}. Mirrors {@code AbcReader#CHORD_QUALITY_ALIASES}
     * for consistency.
     */
    private static final Map<String, HarmonyKind> CHORD_QUALITY_ALIASES = Map.ofEntries(
            Map.entry("", HarmonyKind.MAJOR),
            Map.entry("maj", HarmonyKind.MAJOR),
            Map.entry("m", HarmonyKind.MINOR),
            Map.entry("min", HarmonyKind.MINOR),
            Map.entry("7", HarmonyKind.DOMINANT_SEVENTH),
            Map.entry("maj7", HarmonyKind.MAJOR_SEVENTH),
            Map.entry("m7", HarmonyKind.MINOR_SEVENTH),
            Map.entry("min7", HarmonyKind.MINOR_SEVENTH),
            Map.entry("dim", HarmonyKind.DIMINISHED),
            Map.entry("dim7", HarmonyKind.DIMINISHED_SEVENTH),
            Map.entry("aug", HarmonyKind.AUGMENTED),
            Map.entry("6", HarmonyKind.MAJOR_SIXTH),
            Map.entry("m6", HarmonyKind.MINOR_SIXTH),
            Map.entry("maj9", HarmonyKind.MAJOR_NINTH),
            Map.entry("m9", HarmonyKind.MINOR_NINTH),
            Map.entry("9", HarmonyKind.DOMINANT_NINTH),
            Map.entry("sus4", HarmonyKind.SUSPENDED_FOURTH),
            Map.entry("sus", HarmonyKind.SUSPENDED_FOURTH),
            Map.entry("sus2", HarmonyKind.SUSPENDED_SECOND),
            Map.entry("5", HarmonyKind.POWER));

    private static List<Harmony> parseChordMode(String body) {
        List<Harmony> out = new ArrayList<>();
        int i = 0;
        int n = body.length();
        while (i < n) {
            while (i < n && Character.isWhitespace(body.charAt(i))) {
                i++;
            }
            if (i >= n) {
                break;
            }
            char c = body.charAt(i);
            if (c == '\\') {
                // Skip \command tokens (e.g. \major, \minor).
                i++;
                while (i < n && Character.isLetter(body.charAt(i))) {
                    i++;
                }
                continue;
            }
            if (c < 'a' || c > 'g') {
                i++;
                continue;
            }
            LilyPondPitch.Parsed pp = LilyPondPitch.parse(body, i);
            if (pp == null) {
                i++;
                continue;
            }
            int p = i + pp.consumed();
            // Skip octave marks (chord roots don't care about octaves).
            while (p < n && (body.charAt(p) == '\'' || body.charAt(p) == ',')) {
                p++;
            }
            // Skip duration.
            while (p < n && (Character.isDigit(body.charAt(p)) || body.charAt(p) == '.')) {
                p++;
            }
            String qualitySuffix = "";
            Harmony.Bass bass = null;
            if (p < n && body.charAt(p) == ':') {
                p++;
                int qStart = p;
                while (p < n && !Character.isWhitespace(body.charAt(p)) && body.charAt(p) != '/') {
                    p++;
                }
                qualitySuffix = body.substring(qStart, p);
            }
            if (p < n && body.charAt(p) == '/') {
                p++;
                LilyPondPitch.Parsed bp = LilyPondPitch.parse(body, p);
                if (bp != null) {
                    bass = new Harmony.Bass(bp.step(), bp.alter());
                    p += bp.consumed();
                    while (p < n && (body.charAt(p) == '\'' || body.charAt(p) == ',')) {
                        p++;
                    }
                }
            }
            HarmonyKind kind = CHORD_QUALITY_ALIASES.get(qualitySuffix.toLowerCase(Locale.ROOT));
            String textOverride = null;
            if (kind == null) {
                kind = HarmonyKind.OTHER;
                textOverride = qualitySuffix;
            }
            out.add(new Harmony(new Harmony.Root(pp.step(), pp.alter()), kind,
                    Optional.ofNullable(bass), Optional.ofNullable(textOverride)));
            i = p;
        }
        return out;
    }

    // ----- header value bag -------------------------------------------------

    private static final class Header {
        String title;
        String subtitle;
        String composer;
    }

    // ----- body parser ------------------------------------------------------

    /**
     * State machine that walks the music expression's characters and emits
     * model objects into a {@link Part.Builder}.
     */
    static final class BodyParser {

        private final Part.Builder part;
        private final int divisions;

        private KeySignature key = KeySignature.cMajor();
        private TimeSignature timeSignature = TimeSignature.fourFour();
        private Clef clef = Clef.treble();
        private NoteType currentDurationType = NoteType.QUARTER;
        private int currentDurationDots;
        private boolean firstMeasureFlushed;

        // Relative mode state.
        private boolean relativeMode;
        private Pitch previousPitch = new Pitch(Step.C, 4);

        // Current measure state.
        private PendingMeasure currentMeasure;
        private final List<PendingMeasure> emittedMeasures = new ArrayList<>();
        private int measureNumber = 1;
        private boolean pendingLeadingRepeatStart;

        // Lyric anchors collected during music parsing.
        private final List<Note> lyricAnchors = new ArrayList<>();

        // Tie state.
        private boolean tieToNext;

        // Articulations pending for the next note.
        private final List<Articulation> pendingArticulations = new ArrayList<>();

        // Grace-note pitches pending for the next note.
        private final List<Pitch> pendingGraceNotes = new ArrayList<>();

        // Tuplet state.
        private int tupletActualNotes;
        private int tupletNormalNotes;
        private int tupletRemainingNotes;
        private boolean tupletStartPending;
        private int tupletNumberCounter;
        private int currentTupletNumber;

        // Beam group.
        private final List<Note> beamGroup = new ArrayList<>();

        // Chord accumulation.
        private boolean inChord;
        private final List<Note> chordNotes = new ArrayList<>();
        private NoteType chordDurationType;
        private int chordDurationDots;

        // Pending tempo direction to attach to the next measure.
        private Direction pendingTempo;

        BodyParser(Part.Builder part, int divisions) {
            this.part = part;
            this.divisions = divisions;
            startMeasure();
        }

        void parse(String music) {
            int i = 0;
            int n = music.length();
            while (i < n) {
                char c = music.charAt(i);
                if (Character.isWhitespace(c)) {
                    i++;
                    continue;
                }
                if (c == '\\') {
                    i = handleCommand(music, i);
                    continue;
                }
                if (c == '{') {
                    i++;
                    continue;
                }
                if (c == '}') {
                    i++;
                    if (tupletRemainingNotes > 0) {
                        // Fail-safe: force-close an open tuplet at the block end.
                        closeTuplet();
                    }
                    continue;
                }
                if (c == '|') {
                    closeChordIfOpen();
                    flushBeamGroup();
                    closeMeasure();
                    startMeasure();
                    i++;
                    continue;
                }
                if (c == '<' && i + 1 < n && music.charAt(i + 1) == '<') {
                    // Polyphony start — skip to matching >>. MVP drops it.
                    i = skipPolyphony(music, i);
                    continue;
                }
                if (c == '<') {
                    // Chord: <c e g>4
                    inChord = true;
                    chordNotes.clear();
                    chordDurationType = currentDurationType;
                    chordDurationDots = currentDurationDots;
                    i++;
                    continue;
                }
                if (c == '>') {
                    // Chord end. A trailing duration suffix belongs to the chord.
                    i++;
                    LilyPondDuration.Parsed dp = LilyPondDuration.parseSuffix(music, i);
                    if (dp != null) {
                        chordDurationType = dp.type();
                        chordDurationDots = dp.dots();
                        currentDurationType = dp.type();
                        currentDurationDots = dp.dots();
                        i += dp.consumed();
                    }
                    // Also consume any trailing '~' as a tie on the chord.
                    while (i < n && (music.charAt(i) == '~' || music.charAt(i) == '(' || music.charAt(i) == ')')) {
                        char t = music.charAt(i);
                        if (t == '~') {
                            setLastEmittedTieStart();
                            tieToNext = true;
                        } else if (t == '(') {
                            setLastNoteSlurStart();
                        } else {
                            setLastNoteSlurStop();
                        }
                        i++;
                    }
                    closeChordIfOpen();
                    continue;
                }
                if (c == '~') {
                    setLastEmittedTieStart();
                    tieToNext = true;
                    i++;
                    continue;
                }
                if (c == '(') {
                    // LilyPond convention: '(' opens the slur on the note
                    // preceding it (e.g. `d'4( e'4)`).
                    setLastNoteSlurStart();
                    i++;
                    continue;
                }
                if (c == ')') {
                    setLastNoteSlurStop();
                    i++;
                    continue;
                }
                if (c == '[' || c == ']') {
                    // Explicit beam markers — MVP relies on auto-beaming.
                    i++;
                    continue;
                }
                if (c == '-') {
                    if (i + 1 < n) {
                        char next = music.charAt(i + 1);
                        // Only staccato and accent map to the current
                        // Articulation enum values; the tenuto shorthand
                        // ("--") is consumed but has no model mapping yet.
                        Articulation art = switch (next) {
                            case '.' -> Articulation.STACCATO;
                            case '>' -> Articulation.ACCENT;
                            default -> null;
                        };
                        if (art != null) {
                            pendingArticulations.add(art);
                            i += 2;
                            continue;
                        }
                        if (next == '-') {
                            // Tenuto — consumed silently for MVP.
                            i += 2;
                            continue;
                        }
                    }
                    // Fingering (-1, -2, ...) or a stray dash — consume 1 char.
                    i++;
                    continue;
                }
                if (c == '_' || c == '^') {
                    // Text scripts ("_" below, "^" above). Skip an optional
                    // "\command" or "text" argument.
                    i++;
                    while (i < n && Character.isWhitespace(music.charAt(i))) {
                        i++;
                    }
                    if (i < n && music.charAt(i) == '"') {
                        int close = music.indexOf('"', i + 1);
                        i = close < 0 ? n : close + 1;
                    } else if (i < n && music.charAt(i) == '\\') {
                        i++;
                        while (i < n && Character.isLetter(music.charAt(i))) {
                            i++;
                        }
                    } else {
                        // Consume one identifier or char.
                        if (i < n && Character.isLetterOrDigit(music.charAt(i))) {
                            while (i < n && Character.isLetterOrDigit(music.charAt(i))) {
                                i++;
                            }
                        }
                    }
                    continue;
                }
                if (c == '!' || c == '?') {
                    // Reminder / cautionary accidental marker — consumed by parseNote inline.
                    i++;
                    continue;
                }
                if (c == 'r' || c == 'R') {
                    boolean fullMeasure = c == 'R';
                    i++;
                    LilyPondDuration.Parsed dp = LilyPondDuration.parseSuffix(music, i);
                    NoteType type = currentDurationType;
                    int dots = currentDurationDots;
                    if (dp != null) {
                        type = dp.type();
                        dots = dp.dots();
                        currentDurationType = type;
                        currentDurationDots = dots;
                        i += dp.consumed();
                    }
                    emitRest(type, dots, fullMeasure);
                    continue;
                }
                if (c == 's') {
                    // Spacer: skipped, but consume its duration.
                    i++;
                    LilyPondDuration.Parsed dp = LilyPondDuration.parseSuffix(music, i);
                    if (dp != null) {
                        currentDurationType = dp.type();
                        currentDurationDots = dp.dots();
                        i += dp.consumed();
                    }
                    continue;
                }
                if (c >= 'a' && c <= 'g') {
                    ParsedNote pn = parseNote(music, i);
                    if (pn == null) {
                        i++;
                        continue;
                    }
                    i = pn.consumed;
                    emitNote(pn);
                    continue;
                }
                if (Character.isDigit(c)) {
                    // Stray digit — ignore.
                    i++;
                    continue;
                }
                i++;
            }
        }

        private int handleCommand(String s, int start) {
            int i = start + 1; // skip '\'
            int n = s.length();
            int nameStart = i;
            while (i < n && (Character.isLetter(s.charAt(i)) || s.charAt(i) == '-')) {
                i++;
            }
            String name = s.substring(nameStart, i);
            switch (name) {
                case "relative" -> {
                    // \relative [c'] { ... }
                    int p = i;
                    while (p < n && Character.isWhitespace(s.charAt(p))) {
                        p++;
                    }
                    Pitch reference = new Pitch(Step.C, 4);
                    if (p < n && s.charAt(p) >= 'a' && s.charAt(p) <= 'g') {
                        LilyPondPitch.Parsed pp = LilyPondPitch.parse(s, p);
                        if (pp != null) {
                            int q = p + pp.consumed();
                            int octave = 3;
                            while (q < n) {
                                char oc = s.charAt(q);
                                if (oc == '\'') {
                                    octave++;
                                    q++;
                                } else if (oc == ',') {
                                    octave--;
                                    q++;
                                } else {
                                    break;
                                }
                            }
                            reference = new Pitch(pp.step(), octave, pp.alter());
                            p = q;
                        }
                    }
                    while (p < n && Character.isWhitespace(s.charAt(p))) {
                        p++;
                    }
                    if (p < n && s.charAt(p) == '{') {
                        int close = findMatchingBrace(s, p);
                        if (close > 0) {
                            boolean prevRelative = relativeMode;
                            Pitch prevRef = previousPitch;
                            relativeMode = true;
                            previousPitch = reference;
                            parse(s.substring(p + 1, close));
                            relativeMode = prevRelative;
                            previousPitch = prevRef;
                            return close + 1;
                        }
                    }
                    return p;
                }
                case "time" -> {
                    int p = skipWhitespace(s, i);
                    // Read fraction like 4/4
                    int numStart = p;
                    while (p < n && Character.isDigit(s.charAt(p))) {
                        p++;
                    }
                    if (p < n && s.charAt(p) == '/') {
                        int num = Integer.parseInt(s.substring(numStart, p));
                        p++;
                        int denStart = p;
                        while (p < n && Character.isDigit(s.charAt(p))) {
                            p++;
                        }
                        int den = Integer.parseInt(s.substring(denStart, p));
                        this.timeSignature = new TimeSignature(num, den);
                        applyAttributeChange();
                    }
                    return p;
                }
                case "key" -> {
                    int p = skipWhitespace(s, i);
                    LilyPondPitch.Parsed pp = LilyPondPitch.parse(s, p);
                    if (pp == null) {
                        return p;
                    }
                    int q = p + pp.consumed();
                    while (q < n && Character.isWhitespace(s.charAt(q))) {
                        q++;
                    }
                    String mode = "\\major";
                    if (q < n && s.charAt(q) == '\\') {
                        int mstart = q + 1;
                        int mend = mstart;
                        while (mend < n && Character.isLetter(s.charAt(mend))) {
                            mend++;
                        }
                        mode = "\\" + s.substring(mstart, mend);
                        q = mend;
                    }
                    this.key = keyFromLilyPond(pp.step(), pp.alter(), mode);
                    applyAttributeChange();
                    return q;
                }
                case "clef" -> {
                    int p = skipWhitespace(s, i);
                    String clefName;
                    if (p < n && s.charAt(p) == '"') {
                        int close = s.indexOf('"', p + 1);
                        clefName = close < 0 ? "" : s.substring(p + 1, close);
                        p = close < 0 ? n : close + 1;
                    } else {
                        int cstart = p;
                        while (p < n && Character.isLetter(s.charAt(p))) {
                            p++;
                        }
                        clefName = s.substring(cstart, p);
                    }
                    this.clef = clefFromName(clefName);
                    applyAttributeChange();
                    return p;
                }
                case "tempo" -> {
                    int p = skipWhitespace(s, i);
                    // Skip optional "text" preceding the numeric spec.
                    if (p < n && s.charAt(p) == '"') {
                        int close = s.indexOf('"', p + 1);
                        p = close < 0 ? n : close + 1;
                        p = skipWhitespace(s, p);
                    }
                    // Parse `4 = 120` or `4.=120`.
                    int numStart = p;
                    while (p < n && Character.isDigit(s.charAt(p))) {
                        p++;
                    }
                    if (numStart == p) {
                        return p;
                    }
                    int noteNumber = Integer.parseInt(s.substring(numStart, p));
                    NoteType noteType = LilyPondDuration.numberToType(noteNumber);
                    boolean dotted = false;
                    if (p < n && s.charAt(p) == '.') {
                        dotted = true;
                        p++;
                    }
                    p = skipWhitespace(s, p);
                    if (p < n && s.charAt(p) == '=') {
                        p++;
                        p = skipWhitespace(s, p);
                        int bpmStart = p;
                        while (p < n && Character.isDigit(s.charAt(p))) {
                            p++;
                        }
                        if (bpmStart < p) {
                            int bpm = Integer.parseInt(s.substring(bpmStart, p));
                            DirectionType.Metronome m = new DirectionType.Metronome(noteType, dotted, bpm);
                            Direction dir = new Direction(m, com.sheetmusic4j.core.model.Placement.ABOVE);
                            if (currentMeasure != null && currentMeasure.elements.isEmpty()) {
                                currentMeasure.elements.add(dir);
                            } else {
                                pendingTempo = dir;
                            }
                        }
                    }
                    return p;
                }
                case "bar" -> {
                    int p = skipWhitespace(s, i);
                    String style = "";
                    if (p < n && s.charAt(p) == '"') {
                        int close = s.indexOf('"', p + 1);
                        if (close > 0) {
                            style = s.substring(p + 1, close);
                            p = close + 1;
                        } else {
                            p = n;
                        }
                    }
                    applyBarStyle(style);
                    return p;
                }
                case "tuplet" -> {
                    int p = skipWhitespace(s, i);
                    int numStart = p;
                    while (p < n && Character.isDigit(s.charAt(p))) {
                        p++;
                    }
                    if (p >= n || s.charAt(p) != '/') {
                        return p;
                    }
                    int actual = Integer.parseInt(s.substring(numStart, p));
                    p++;
                    int denStart = p;
                    while (p < n && Character.isDigit(s.charAt(p))) {
                        p++;
                    }
                    int normal = Integer.parseInt(s.substring(denStart, p));
                    p = skipWhitespace(s, p);
                    if (p < n && s.charAt(p) == '{') {
                        int close = findMatchingBrace(s, p);
                        if (close > 0) {
                            int count = countTupletNotes(s, p + 1, close);
                            openTuplet(actual, normal, count);
                            parse(s.substring(p + 1, close));
                            if (tupletRemainingNotes > 0) {
                                closeTuplet();
                            }
                            return close + 1;
                        }
                    }
                    return p;
                }
                case "times" -> {
                    // Legacy \times N/D { ... } — inverse ratio: actual/normal.
                    int p = skipWhitespace(s, i);
                    int numStart = p;
                    while (p < n && Character.isDigit(s.charAt(p))) {
                        p++;
                    }
                    if (p >= n || s.charAt(p) != '/') {
                        return p;
                    }
                    int normal = Integer.parseInt(s.substring(numStart, p));
                    p++;
                    int denStart = p;
                    while (p < n && Character.isDigit(s.charAt(p))) {
                        p++;
                    }
                    int actual = Integer.parseInt(s.substring(denStart, p));
                    p = skipWhitespace(s, p);
                    if (p < n && s.charAt(p) == '{') {
                        int close = findMatchingBrace(s, p);
                        if (close > 0) {
                            int count = countTupletNotes(s, p + 1, close);
                            openTuplet(actual, normal, count);
                            parse(s.substring(p + 1, close));
                            if (tupletRemainingNotes > 0) {
                                closeTuplet();
                            }
                            return close + 1;
                        }
                    }
                    return p;
                }
                case "grace", "acciaccatura", "appoggiatura" -> {
                    int p = skipWhitespace(s, i);
                    if (p < n && s.charAt(p) == '{') {
                        int close = findMatchingBrace(s, p);
                        if (close > 0) {
                            String content = s.substring(p + 1, close);
                            capturePendingGraceNotes(content);
                            return close + 1;
                        }
                    }
                    // Single note grace form: \grace c8
                    LilyPondPitch.Parsed pp = LilyPondPitch.parse(s, p);
                    if (pp != null) {
                        pendingGraceNotes.add(new Pitch(pp.step(), 4, pp.alter()));
                        p += pp.consumed();
                        while (p < n && (s.charAt(p) == '\'' || s.charAt(p) == ',')) {
                            p++;
                        }
                        LilyPondDuration.Parsed dp = LilyPondDuration.parseSuffix(s, p);
                        if (dp != null) {
                            p += dp.consumed();
                        }
                    }
                    return p;
                }
                case "version", "language", "score", "book", "header", "paper",
                        "layout", "midi", "new", "with", "addlyrics", "lyricmode",
                        "lyrics", "chordmode", "chords", "context" -> {
                    // Consume any trailing arg-block(s): "..." string, then { ... } if present.
                    int p = skipWhitespace(s, i);
                    while (p < n) {
                        char c = s.charAt(p);
                        if (c == '"') {
                            int close = s.indexOf('"', p + 1);
                            p = close < 0 ? n : close + 1;
                            p = skipWhitespace(s, p);
                            continue;
                        }
                        if (c == '{') {
                            int close = findMatchingBrace(s, p);
                            // Only consume the block, don't recurse (top-level
                            // extractors already handled these).
                            p = close < 0 ? n : close + 1;
                            break;
                        }
                        if (c == '\\' || Character.isLetter(c)) {
                            // Consume one more command / identifier and keep looking.
                            if (c == '\\') {
                                p++;
                            }
                            while (p < n && (Character.isLetter(s.charAt(p)) || s.charAt(p) == '-')) {
                                p++;
                            }
                            p = skipWhitespace(s, p);
                            continue;
                        }
                        break;
                    }
                    return p;
                }
                default -> {
                    // Unknown \command — skip identifier only; args are left
                    // for the outer scanner to consume.
                    return i;
                }
            }
        }

        private void capturePendingGraceNotes(String content) {
            int i = 0;
            int n = content.length();
            while (i < n) {
                char c = content.charAt(i);
                if (c >= 'a' && c <= 'g') {
                    LilyPondPitch.Parsed pp = LilyPondPitch.parse(content, i);
                    if (pp != null) {
                        int p = i + pp.consumed();
                        int octave = relativeMode ? computeRelativeOctave(pp.step(), previousPitch) : 3;
                        while (p < n) {
                            char oc = content.charAt(p);
                            if (oc == '\'') {
                                octave++;
                                p++;
                            } else if (oc == ',') {
                                octave--;
                                p++;
                            } else {
                                break;
                            }
                        }
                        // Skip !/? reminder markers.
                        while (p < n && (content.charAt(p) == '!' || content.charAt(p) == '?')) {
                            p++;
                        }
                        // Skip duration suffix.
                        LilyPondDuration.Parsed dp = LilyPondDuration.parseSuffix(content, p);
                        if (dp != null) {
                            p += dp.consumed();
                        }
                        pendingGraceNotes.add(new Pitch(pp.step(), octave, pp.alter()));
                        if (relativeMode) {
                            previousPitch = new Pitch(pp.step(), octave, pp.alter());
                        }
                        i = p;
                        continue;
                    }
                }
                i++;
            }
        }

        private int skipPolyphony(String s, int start) {
            int depth = 0;
            int i = start;
            int n = s.length();
            while (i < n) {
                if (i + 1 < n && s.charAt(i) == '<' && s.charAt(i + 1) == '<') {
                    depth++;
                    i += 2;
                    continue;
                }
                if (i + 1 < n && s.charAt(i) == '>' && s.charAt(i + 1) == '>') {
                    depth--;
                    i += 2;
                    if (depth == 0) {
                        return i;
                    }
                    continue;
                }
                i++;
            }
            return i;
        }

        private static int skipWhitespace(String s, int i) {
            int n = s.length();
            while (i < n && Character.isWhitespace(s.charAt(i))) {
                i++;
            }
            return i;
        }

        private static int countTupletNotes(String s, int from, int to) {
            int count = 0;
            int i = from;
            while (i < to) {
                char c = s.charAt(i);
                if (c == '\\') {
                    // Skip a command token wholesale.
                    i++;
                    while (i < to && Character.isLetter(s.charAt(i))) {
                        i++;
                    }
                    continue;
                }
                if (c == '<' && i + 1 < to && s.charAt(i + 1) == '<') {
                    // polyphony — count as zero for MVP
                    i += 2;
                    continue;
                }
                if (c == '<') {
                    // chord counts as one note
                    int close = s.indexOf('>', i);
                    if (close < 0 || close >= to) {
                        return count;
                    }
                    count++;
                    i = close + 1;
                    continue;
                }
                if ((c >= 'a' && c <= 'g') || c == 'r' || c == 'R') {
                    count++;
                    // Skip the pitch/rest token.
                    i++;
                    while (i < to && Character.isLetter(s.charAt(i))) {
                        i++;
                    }
                    while (i < to && (s.charAt(i) == '\'' || s.charAt(i) == ',' || s.charAt(i) == '!'
                            || s.charAt(i) == '?')) {
                        i++;
                    }
                    LilyPondDuration.Parsed dp = LilyPondDuration.parseSuffix(s, i);
                    if (dp != null) {
                        i += dp.consumed();
                    }
                    continue;
                }
                i++;
            }
            return count;
        }

        private void openTuplet(int actual, int normal, int count) {
            tupletActualNotes = actual;
            tupletNormalNotes = normal;
            tupletRemainingNotes = count;
            tupletStartPending = true;
            tupletNumberCounter++;
            currentTupletNumber = tupletNumberCounter;
        }

        private void closeTuplet() {
            tupletRemainingNotes = 0;
            tupletStartPending = false;
        }

        // ----- parseNote ----------------------------------------------------

        private static final class ParsedNote {
            Step step;
            int octaveShift;
            int alter;
            boolean explicitAccidental;
            boolean reminder;
            NoteType type;
            int dots;
            boolean hasExplicitDuration;
            int consumed;
        }

        private ParsedNote parseNote(String s, int start) {
            int n = s.length();
            LilyPondPitch.Parsed pp = LilyPondPitch.parse(s, start);
            if (pp == null) {
                return null;
            }
            ParsedNote out = new ParsedNote();
            out.step = pp.step();
            out.alter = pp.alter();
            out.explicitAccidental = pp.alter() != 0 || pp.consumed() > 1;
            int i = start + pp.consumed();
            int shift = 0;
            while (i < n) {
                char c = s.charAt(i);
                if (c == '\'') {
                    shift++;
                    i++;
                } else if (c == ',') {
                    shift--;
                    i++;
                } else {
                    break;
                }
            }
            out.octaveShift = shift;
            // Reminder / cautionary accidental marker.
            while (i < n && (s.charAt(i) == '!' || s.charAt(i) == '?')) {
                if (s.charAt(i) == '!') {
                    out.reminder = true;
                }
                i++;
            }
            LilyPondDuration.Parsed dp = LilyPondDuration.parseSuffix(s, i);
            if (dp != null) {
                out.type = dp.type();
                out.dots = dp.dots();
                out.hasExplicitDuration = true;
                i += dp.consumed();
            } else {
                out.type = currentDurationType;
                out.dots = currentDurationDots;
                out.hasExplicitDuration = false;
            }
            out.consumed = i;
            return out;
        }

        // ----- emit ---------------------------------------------------------

        private void emitRest(NoteType type, int dots, boolean fullMeasure) {
            closeChordIfOpen();
            flushBeamGroup();
            Duration duration;
            if (fullMeasure) {
                duration = measureDuration();
            } else {
                duration = LilyPondDuration.toDuration(type, dots, divisions);
            }
            NoteType displayType = fullMeasure ? NoteType.WHOLE : type;
            int displayDots = fullMeasure ? 0 : dots;
            Rest rest = Rest.builder()
                    .duration(duration)
                    .type(displayType)
                    .dots(displayDots)
                    .build();
            currentMeasure.elements.add(rest);
            lyricAnchors.add(null);
        }

        private void emitNote(ParsedNote pn) {
            int octave;
            if (relativeMode) {
                octave = computeRelativeOctave(pn.step, previousPitch) + pn.octaveShift;
            } else {
                octave = 3 + pn.octaveShift; // absolute: bare `c` = C3
            }
            int alter = pn.alter;
            if (!pn.explicitAccidental) {
                alter = LilyPondPitch.alterFor(pn.step, key);
            }
            Pitch pitch = new Pitch(pn.step, octave, alter);
            if (relativeMode) {
                previousPitch = new Pitch(pn.step, octave, alter);
            }
            if (pn.hasExplicitDuration) {
                currentDurationType = pn.type;
                currentDurationDots = pn.dots;
            }
            NoteType type = pn.type;
            int dots = pn.dots;

            Duration writtenDuration = LilyPondDuration.toDuration(type, dots, divisions);
            Duration duration = writtenDuration;
            TimeModification timeMod = null;
            List<Tuplet> tuplets = new ArrayList<>();
            if (tupletRemainingNotes > 0 && !inChord) {
                long value = (long) writtenDuration.value() * tupletNormalNotes / tupletActualNotes;
                duration = new Duration((int) Math.max(1, value), divisions);
                timeMod = new TimeModification(tupletActualNotes, tupletNormalNotes);
                if (tupletStartPending) {
                    tuplets.add(new Tuplet(currentTupletNumber, Tuplet.Type.START, true));
                    tupletStartPending = false;
                }
                if (tupletRemainingNotes == 1) {
                    tuplets.add(new Tuplet(currentTupletNumber, Tuplet.Type.STOP, true));
                }
                tupletRemainingNotes--;
            }

            Note.Builder b = Note.builder()
                    .pitch(pitch)
                    .duration(duration)
                    .type(type)
                    .dots(dots);
            if (pn.explicitAccidental || pn.reminder) {
                b.displayedAccidental(Accidental.fromAlter(alter));
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
            if (!pendingArticulations.isEmpty()) {
                for (Articulation a : pendingArticulations) {
                    b.addArticulation(a);
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
                currentMeasure.elements.add(note);
                lyricAnchors.add(note);
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
            NoteType type = chordDurationType;
            int dots = chordDurationDots;
            Duration writtenDuration = LilyPondDuration.toDuration(type, dots, divisions);
            Duration duration = writtenDuration;
            List<Tuplet> tuplets = new ArrayList<>();
            TimeModification timeMod = null;
            if (tupletRemainingNotes > 0) {
                long value = (long) writtenDuration.value() * tupletNormalNotes / tupletActualNotes;
                duration = new Duration((int) Math.max(1, value), divisions);
                timeMod = new TimeModification(tupletActualNotes, tupletNormalNotes);
                if (tupletStartPending) {
                    tuplets.add(new Tuplet(currentTupletNumber, Tuplet.Type.START, true));
                    tupletStartPending = false;
                }
                if (tupletRemainingNotes == 1) {
                    tuplets.add(new Tuplet(currentTupletNumber, Tuplet.Type.STOP, true));
                }
                tupletRemainingNotes--;
            }
            // Rebuild notes with the chord's duration/type/dots.
            List<Note> notes = new ArrayList<>();
            for (int idx = 0; idx < chordNotes.size(); idx++) {
                Note n = chordNotes.get(idx);
                Note.Builder b = Note.builder()
                        .pitch(n.pitch())
                        .duration(duration)
                        .type(type)
                        .dots(dots);
                if (n.displayedAccidental().isPresent()) {
                    b.displayedAccidental(n.displayedAccidental().get());
                }
                if (idx == 0 && timeMod != null) {
                    b.timeModification(timeMod);
                    for (Tuplet t : tuplets) {
                        b.addTuplet(t);
                    }
                }
                if (idx == 0 && tieToNext) {
                    b.tieStop(true);
                }
                notes.add(b.build());
            }
            tieToNext = false;
            chordNotes.clear();
            if (notes.size() == 1) {
                Note only = notes.get(0);
                currentMeasure.elements.add(only);
                lyricAnchors.add(only);
                addToBeamGroup(only);
            } else {
                Chord chord = new Chord(notes);
                currentMeasure.elements.add(chord);
                lyricAnchors.add(notes.get(0));
                addToBeamGroup(notes.get(0));
            }
        }

        private int computeRelativeOctave(Step newStep, Pitch prev) {
            int prevDiatonic = prev.octave() * 7 + prev.step().ordinal();
            int bestOctave = prev.octave();
            int bestDiff = Integer.MAX_VALUE;
            for (int oct = prev.octave() - 1; oct <= prev.octave() + 1; oct++) {
                int candidate = oct * 7 + newStep.ordinal();
                int diff = Math.abs(candidate - prevDiatonic);
                if (diff < bestDiff || (diff == bestDiff && candidate < prevDiatonic)) {
                    bestDiff = diff;
                    bestOctave = oct;
                }
            }
            return bestOctave;
        }

        private void addToBeamGroup(Note note) {
            if (beamLevelsFor(note.type()) >= 1) {
                beamGroup.add(note);
            } else {
                flushBeamGroup();
            }
        }

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
            List<List<Beam>> perNote = new ArrayList<>();
            for (int i = 0; i < size; i++) {
                perNote.add(new ArrayList<>());
            }
            for (int level = 1; level <= maxLevel; level++) {
                addBeamRuns(levels, level, perNote);
            }
            for (int i = 0; i < size; i++) {
                List<Beam> beams = perNote.get(i);
                if (beams.isEmpty()) {
                    continue;
                }
                Note original = beamGroup.get(i);
                Note replaced = rebuildNote(original, nb -> nb.beams(beams));
                replaceInMeasures(original, replaced);
            }
            beamGroup.clear();
        }

        private static void addBeamRuns(int[] levels, int level, List<List<Beam>> perNote) {
            int size = levels.length;
            int i = 0;
            while (i < size) {
                if (levels[i] < level) {
                    i++;
                    continue;
                }
                int startPos = i;
                int endPos = i;
                while (endPos + 1 < size && levels[endPos + 1] >= level) {
                    endPos++;
                }
                if (endPos > startPos) {
                    perNote.get(startPos).add(new Beam(level, Beam.State.BEGIN));
                    for (int k = startPos + 1; k < endPos; k++) {
                        perNote.get(k).add(new Beam(level, Beam.State.CONTINUE));
                    }
                    perNote.get(endPos).add(new Beam(level, Beam.State.END));
                } else {
                    boolean hasPrev = startPos > 0;
                    Beam.State hook = hasPrev ? Beam.State.BACKWARD_HOOK : Beam.State.FORWARD_HOOK;
                    perNote.get(startPos).add(new Beam(level, hook));
                }
                i = endPos + 1;
            }
        }

        private static int beamLevelsFor(NoteType type) {
            int diff = type.ordinal() - NoteType.EIGHTH.ordinal();
            return diff >= 0 ? diff + 1 : 0;
        }

        private void setLastEmittedTieStart() {
            List<MusicElement> elements = currentMeasure.elements;
            for (int i = elements.size() - 1; i >= 0; i--) {
                MusicElement el = elements.get(i);
                if (el instanceof Note note) {
                    Note replaced = rebuildNote(note, nb -> nb.tieStart(true));
                    elements.set(i, replaced);
                    replaceBeamAndAnchor(note, replaced);
                    return;
                }
                if (el instanceof Chord chord) {
                    List<Note> newNotes = new ArrayList<>();
                    for (Note n : chord.notes()) {
                        newNotes.add(rebuildNote(n, nb -> nb.tieStart(true)));
                    }
                    elements.set(i, new Chord(newNotes));
                    return;
                }
            }
        }

        private void setLastNoteSlurStart() {
            List<MusicElement> elements = currentMeasure.elements;
            for (int i = elements.size() - 1; i >= 0; i--) {
                MusicElement el = elements.get(i);
                if (el instanceof Note note) {
                    Note replaced = rebuildNote(note, nb -> nb.addSlur(
                            new Slur(1, Slur.Type.START, com.sheetmusic4j.core.model.Placement.DEFAULT)));
                    elements.set(i, replaced);
                    replaceBeamAndAnchor(note, replaced);
                    return;
                }
                if (el instanceof Chord chord) {
                    List<Note> newNotes = new ArrayList<>(chord.notes());
                    if (!newNotes.isEmpty()) {
                        newNotes.set(0, rebuildNote(newNotes.get(0), nb -> nb.addSlur(
                                new Slur(1, Slur.Type.START, com.sheetmusic4j.core.model.Placement.DEFAULT))));
                        elements.set(i, new Chord(newNotes));
                    }
                    return;
                }
            }
        }

        private void setLastNoteSlurStop() {
            List<MusicElement> elements = currentMeasure.elements;
            for (int i = elements.size() - 1; i >= 0; i--) {
                MusicElement el = elements.get(i);
                if (el instanceof Note note) {
                    Note replaced = rebuildNote(note, nb -> nb.addSlur(
                            new Slur(1, Slur.Type.STOP, com.sheetmusic4j.core.model.Placement.DEFAULT)));
                    elements.set(i, replaced);
                    replaceBeamAndAnchor(note, replaced);
                    return;
                }
                if (el instanceof Chord chord) {
                    List<Note> newNotes = new ArrayList<>(chord.notes());
                    if (!newNotes.isEmpty()) {
                        newNotes.set(0, rebuildNote(newNotes.get(0), nb -> nb.addSlur(
                                new Slur(1, Slur.Type.STOP, com.sheetmusic4j.core.model.Placement.DEFAULT))));
                        elements.set(i, new Chord(newNotes));
                    }
                    return;
                }
            }
        }

        private void replaceBeamAndAnchor(Note original, Note replaced) {
            for (int j = 0; j < beamGroup.size(); j++) {
                if (beamGroup.get(j) == original) {
                    beamGroup.set(j, replaced);
                    break;
                }
            }
            for (int j = 0; j < lyricAnchors.size(); j++) {
                if (lyricAnchors.get(j) == original) {
                    lyricAnchors.set(j, replaced);
                    break;
                }
            }
        }

        private void replaceInMeasures(Note original, Note replaced) {
            for (int idx = currentMeasure.elements.size() - 1; idx >= 0; idx--) {
                if (currentMeasure.elements.get(idx) == original) {
                    currentMeasure.elements.set(idx, replaced);
                    replaceBeamAndAnchor(original, replaced);
                    return;
                }
            }
            for (PendingMeasure pm : emittedMeasures) {
                for (int idx = pm.elements.size() - 1; idx >= 0; idx--) {
                    if (pm.elements.get(idx) == original) {
                        pm.elements.set(idx, replaced);
                        replaceBeamAndAnchor(original, replaced);
                        return;
                    }
                }
            }
        }

        // ----- measures -----------------------------------------------------

        private void startMeasure() {
            currentMeasure = new PendingMeasure(measureNumber);
            if (pendingLeadingRepeatStart) {
                currentMeasure.leadingRepeatStart = true;
                pendingLeadingRepeatStart = false;
            }
            if (!firstMeasureFlushed && emittedMeasures.isEmpty()) {
                currentMeasure.attributes = Attributes.builder()
                        .divisions(divisions)
                        .keySignature(key)
                        .timeSignature(timeSignature)
                        .clef(clef)
                        .build();
                if (pendingTempo != null) {
                    currentMeasure.elements.add(pendingTempo);
                    pendingTempo = null;
                }
            }
        }

        private void closeMeasure() {
            if (currentMeasure == null) {
                return;
            }
            boolean leadingDecorative = currentMeasure.elements.isEmpty()
                    && emittedMeasures.isEmpty() && !firstMeasureFlushed
                    && currentMeasure.attributes == null;
            if (!leadingDecorative && (!currentMeasure.elements.isEmpty() || currentMeasure.attributes != null)) {
                emittedMeasures.add(currentMeasure);
                measureNumber++;
                firstMeasureFlushed = true;
            }
            currentMeasure = null;
        }

        private void applyAttributeChange() {
            if (currentMeasure == null) {
                return;
            }
            if (!firstMeasureFlushed && emittedMeasures.isEmpty() && currentMeasure.elements.isEmpty()) {
                currentMeasure.attributes = Attributes.builder()
                        .divisions(divisions)
                        .keySignature(key)
                        .timeSignature(timeSignature)
                        .clef(clef)
                        .build();
                return;
            }
            currentMeasure.attributes = Attributes.builder()
                    .divisions(divisions)
                    .keySignature(key)
                    .timeSignature(timeSignature)
                    .clef(clef)
                    .build();
            currentMeasure.forceSystemBreak = true;
        }

        private void applyBarStyle(String style) {
            if (currentMeasure == null) {
                return;
            }
            switch (style) {
                case "|:" -> pendingLeadingRepeatStart = true;
                case ":|" -> currentMeasure.barline = new Barline(Barline.Style.FINAL, Barline.Repeat.BACKWARD);
                case "||" -> currentMeasure.barline = new Barline(Barline.Style.DOUBLE, Barline.Repeat.NONE);
                case "|." -> currentMeasure.barline = new Barline(Barline.Style.FINAL, Barline.Repeat.NONE);
                default -> {
                    // Unknown/unsupported bar style — treated as a plain bar.
                }
            }
        }

        Duration measureDuration() {
            double quarters = timeSignature.measureLengthInQuarters();
            int value = Math.max(1, (int) Math.round(quarters * divisions));
            return new Duration(value, divisions);
        }

        void finish() {
            closeChordIfOpen();
            flushBeamGroup();
            closeMeasure();
            for (PendingMeasure pm : emittedMeasures) {
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
                b.forceSystemBreak(pm.forceSystemBreak);
                part.addMeasure(b.build());
            }
            emittedMeasures.clear();
        }

        void applyLyrics(String block) {
            if (block == null || block.isBlank()) {
                return;
            }
            List<String> syllables = splitLyricSyllables(block);
            int anchorIdx = 0;
            int i = 0;
            while (i < syllables.size() && anchorIdx < lyricAnchors.size()) {
                while (anchorIdx < lyricAnchors.size() && lyricAnchors.get(anchorIdx) == null) {
                    anchorIdx++;
                }
                if (anchorIdx >= lyricAnchors.size()) {
                    break;
                }
                String syl = syllables.get(i);
                if (syl.equals("_")) {
                    anchorIdx++;
                    i++;
                    continue;
                }
                Syllabic syllabic = Syllabic.SINGLE;
                if (i + 1 < syllables.size() && syllables.get(i + 1).equals("--")) {
                    // This syllable begins/continues a hyphenated word.
                    // Whether it's BEGIN or MIDDLE depends on the previous
                    // syllable's ending; keep it simple with BEGIN for the
                    // MVP (engraver treats BEGIN/MIDDLE identically for
                    // hyphenation rendering).
                    syllabic = Syllabic.BEGIN;
                    i++; // consume the '--'
                }
                Note anchor = lyricAnchors.get(anchorIdx);
                Note replaced = attachLyric(anchor, new Lyric(syl, syllabic, 1));
                replaceInMeasures(anchor, replaced);
                lyricAnchors.set(anchorIdx, replaced);
                anchorIdx++;
                i++;
                // Extension "__" — carry the syllable onto the next skip.
                if (i < syllables.size() && syllables.get(i).equals("__")) {
                    i++;
                }
            }
        }

        private Note attachLyric(Note note, Lyric lyric) {
            List<Lyric> merged = new ArrayList<>(note.lyrics());
            merged.add(lyric);
            return rebuildNote(note, b -> b.lyrics(merged));
        }

        private static List<String> splitLyricSyllables(String block) {
            List<String> out = new ArrayList<>();
            int i = 0;
            int n = block.length();
            StringBuilder sb = new StringBuilder();
            while (i < n) {
                char c = block.charAt(i);
                if (Character.isWhitespace(c)) {
                    if (sb.length() > 0) {
                        out.add(sb.toString());
                        sb.setLength(0);
                    }
                    i++;
                    continue;
                }
                if (c == '"') {
                    // Consume until the closing quote as one syllable.
                    i++;
                    StringBuilder q = new StringBuilder();
                    while (i < n && block.charAt(i) != '"') {
                        if (block.charAt(i) == '\\' && i + 1 < n) {
                            q.append(block.charAt(i + 1));
                            i += 2;
                        } else {
                            q.append(block.charAt(i));
                            i++;
                        }
                    }
                    if (i < n) {
                        i++;
                    }
                    if (q.length() > 0) {
                        out.add(q.toString());
                    }
                    continue;
                }
                if (c == '-' && i + 1 < n && block.charAt(i + 1) == '-') {
                    if (sb.length() > 0) {
                        out.add(sb.toString());
                        sb.setLength(0);
                    }
                    out.add("--");
                    i += 2;
                    continue;
                }
                if (c == '_' && i + 1 < n && block.charAt(i + 1) == '_') {
                    if (sb.length() > 0) {
                        out.add(sb.toString());
                        sb.setLength(0);
                    }
                    out.add("__");
                    i += 2;
                    continue;
                }
                sb.append(c);
                i++;
            }
            if (sb.length() > 0) {
                out.add(sb.toString());
            }
            return out;
        }

        void applyChordSymbols(List<Harmony> chords) {
            if (chords.isEmpty()) {
                return;
            }
            // Attach each chord to the corresponding lyric anchor's containing
            // measure, one chord per note anchor.
            int idx = 0;
            for (PendingMeasure pm : emittedMeasures) {
                for (int j = 0; j < pm.elements.size(); j++) {
                    MusicElement el = pm.elements.get(j);
                    if ((el instanceof Note) || (el instanceof Chord)) {
                        if (idx < chords.size()) {
                            pm.elements.add(j, chords.get(idx));
                            j++; // step over the just-inserted harmony
                            idx++;
                        } else {
                            return;
                        }
                    }
                }
            }
        }

        private static final class PendingMeasure {
            final int number;
            Attributes attributes;
            final List<MusicElement> elements = new ArrayList<>();
            Barline barline;
            boolean leadingRepeatStart;
            boolean forceSystemBreak;

            PendingMeasure(int number) {
                this.number = number;
            }
        }
    }

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

    // ----- helpers ----------------------------------------------------------

    /**
     * Build a {@link KeySignature} from a LilyPond {@code \key} argument:
     * a tonic pitch (step + accidental) and a mode command (e.g. {@code \major},
     * {@code \minor}).
     */
    static KeySignature keyFromLilyPond(Step step, int alter, String mode) {
        int majorFifths = majorFifthsFor(step, alter);
        int modeOffset = switch (mode) {
            case "\\major", "\\ionian", "" -> 0;
            case "\\minor", "\\aeolian" -> -3;
            case "\\dorian" -> -2;
            case "\\phrygian" -> -4;
            case "\\lydian" -> 1;
            case "\\mixolydian" -> -1;
            case "\\locrian" -> -5;
            default -> 0;
        };
        int fifths = majorFifths + modeOffset;
        while (fifths > 7) {
            fifths -= 12;
        }
        while (fifths < -7) {
            fifths += 12;
        }
        return new KeySignature(fifths);
    }

    private static int majorFifthsFor(Step step, int alter) {
        int natural = switch (step) {
            case C -> 0;
            case D -> 2;
            case E -> 4;
            case F -> -1;
            case G -> 1;
            case A -> 3;
            case B -> 5;
        };
        return natural + alter * 7;
    }

    /** Map a LilyPond clef name (e.g. {@code "treble"}, {@code "bass"}) to a {@link Clef}. */
    static Clef clefFromName(String name) {
        if (name == null) {
            return Clef.treble();
        }
        String lc = name.trim().toLowerCase(Locale.ROOT);
        return switch (lc) {
            case "treble", "violin", "g" -> Clef.treble();
            case "bass", "f" -> Clef.bass();
            case "alto", "c" -> new Clef(ClefSign.C, 3);
            case "tenor" -> new Clef(ClefSign.C, 4);
            case "soprano" -> new Clef(ClefSign.C, 1);
            case "mezzosoprano" -> new Clef(ClefSign.C, 2);
            case "baritone" -> new Clef(ClefSign.C, 5);
            case "percussion" -> new Clef(ClefSign.PERCUSSION, 3);
            case "tab" -> new Clef(ClefSign.TAB, 3);
            default -> Clef.treble();
        };
    }

    }
