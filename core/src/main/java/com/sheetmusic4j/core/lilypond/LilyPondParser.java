package com.sheetmusic4j.core.lilypond;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.sheetmusic4j.core.model.Articulation;
import com.sheetmusic4j.core.model.DirectionType;
import com.sheetmusic4j.core.model.Fraction;
import com.sheetmusic4j.core.model.Harmony;
import com.sheetmusic4j.core.model.HarmonyKind;
import com.sheetmusic4j.core.model.NoteType;
import com.sheetmusic4j.core.model.Placement;
import com.sheetmusic4j.core.model.Step;

/**
 * Recursive-descent parser for the LilyPond input language, producing a
 * {@link LyMusic} tree per score.
 *
 * <p>Everything LilyPond resolves while reading the file is resolved here:
 * the note-name language, default durations (a note without a duration takes
 * the previous one in the file), variables (parsed once, where they are
 * defined), markup (reduced to plain text) and context types declared in
 * {@code \layout} blocks. Engraving-only syntax ({@code \override},
 * {@code \tweak}, Scheme expressions, ...) is skipped with its arguments.
 */
final class LilyPondParser {

    /** A parsed file: its header and the scores it contains. */
    record Document(Map<String, String> header, List<ScoreBlock> scores, Map<String, ContextType> contextTypes,
                    boolean timeSignaturesHidden) {
    }

    /** One {@code \score} (or a top-level music expression). */
    record ScoreBlock(LyMusic music, Map<String, String> header) {
    }

    /**
     * A context type declared in a {@code \layout} block with {@code \name},
     * derived from {@code base}; {@code delimiter} is its system-start
     * delimiter when it sets or removes one (e.g. {@code "square"}, {@code "none"}).
     */
    record ContextType(String base, String delimiter) {
    }

    /** Markup reduced to text, with the font flags we can show. */
    record Markup(String text, boolean bold, boolean italic) {
    }

    private enum Mode {
        NOTES,
        DRUMS,
        CHORDS
    }

    private static final Set<String> DYNAMICS = Set.of(
            "ppppp", "pppp", "ppp", "pp", "p", "mp", "mf", "f", "ff", "fff", "ffff", "fffff",
            "fp", "sf", "sff", "sfz", "sfp", "fz", "rfz", "rf", "sp", "spp", "n");

    /** Post-event commands with no visible counterpart in the model. */
    private static final Set<String> IGNORED_POST_EVENTS = Set.of(
            "tenuto", "portato", "fermata", "shortfermata", "longfermata", "verylongfermata",
            "henzeshortfermata", "henzelongfermata", "veryshortfermata",
            "prall", "mordent", "turn", "reverseturn", "upprall", "downprall", "prallprall",
            "prallmordent", "upmordent", "downmordent", "lineprall", "pralldown", "prallup",
            "glissando", "laissezVibrer", "repeatTie", "startTrillSpan", "stopTrillSpan",
            "startTextSpan", "stopTextSpan", "sustainOn", "sustainOff", "sostenutoOn", "sostenutoOff",
            "unaCorda", "treCorde", "harmonic", "flageolet", "open", "stopped", "snappizzicato",
            "thumb", "espressivo", "coda", "segno", "varcoda", "signumcongruentiae", "lheel", "rheel",
            "ltoe", "rtoe", "halfopen", "startGroup", "stopGroup", "bendAfter", "noBeam",
            "staccatissimo_", "ictus", "accentus", "circulus", "semicirculus", "augmentum");

    /** Commands that only switch a layout setting on or off: no arguments, no effect. */
    private static final Set<String> NO_ARGUMENT_COMMANDS = Set.of(
            "numericTimeSignature", "defaultTimeSignature", "slurUp", "slurDown", "slurNeutral",
            "slurDashed", "slurDotted", "slurSolid", "slurHalfDashed", "slurHalfSolid",
            "tieUp", "tieDown", "tieNeutral", "tieDashed", "tieDotted", "tieSolid",
            "phrasingSlurUp", "phrasingSlurDown", "phrasingSlurNeutral", "phrasingSlurDashed",
            "phrasingSlurDotted", "phrasingSlurSolid", "dynamicUp", "dynamicDown", "dynamicNeutral",
            "textLengthOn", "textLengthOff", "tupletUp", "tupletDown", "tupletNeutral",
            "mergeDifferentlyHeadedOn", "mergeDifferentlyHeadedOff", "mergeDifferentlyDottedOn",
            "mergeDifferentlyDottedOff", "shiftOn", "shiftOnn", "shiftOnnn", "shiftOff",
            "showStaffSwitch", "hideStaffSwitch", "noBreak", "noPageBreak", "pageTurn", "noPageTurn",
            "allowPageTurn", "breathe", "improvisationOn", "improvisationOff", "xNotesOn", "xNotesOff",
            "harmonicsOn", "harmonicsOff", "easyHeadsOn", "easyHeadsOff", "aikenHeads", "aikenHeadsMinor",
            "sacredHarpHeads", "sacredHarpHeadsMinor", "southernHarmonyHeads", "funkHeads", "walkerHeads",
            "arpeggioArrowUp", "arpeggioArrowDown", "arpeggioNormal", "arpeggioBracket",
            "arpeggioParenthesis", "arpeggioParenthesisDashed", "predefinedFretboardsOff",
            "predefinedFretboardsOn", "deadNotesOn", "deadNotesOff", "germanChords", "semiGermanChords",
            "italianChords", "frenchChords", "powerChords", "bassFigureExtendersOn",
            "bassFigureExtendersOff", "bassFigureStaffAlignmentDown", "bassFigureStaffAlignmentUp",
            "bassFigureStaffAlignmentNeutral", "pointAndClickOff", "pointAndClickOn", "markLengthOn",
            "markLengthOff", "hideSplitTiedTabNotes", "showSplitTiedTabNotes", "tabFullNotation",
            "voiceNeutralStyle", "voiceOneStyle", "voiceTwoStyle", "voiceThreeStyle", "voiceFourStyle",
            "startStaff", "stopStaff", "teeny", "tiny", "small", "normalsize", "large", "huge",
            "balloonLengthOn", "balloonLengthOff", "sostenutoOn", "sostenutoOff", "ligature",
            "expandEmptyMeasures", "compressFullBarRests", "expandFullBarRests", "major", "minor",
            "ionian", "dorian", "phrygian", "lydian", "mixolydian", "aeolian", "locrian", "default",
            "rightHandFinger", "fine", "dalSegno", "caesura", "section", "codaMark", "segnoMark",
            "inStaffSegno", "virgula", "breakDynamicSpan", "autoLineBreaksOff", "autoLineBreaksOn",
            "autoPageBreaksOff", "autoPageBreaksOn", "unHideNotes_", "[", "]",
            "pes", "flexa", "virga", "inclinatum", "deminutum", "quilisma", "oriscus", "stropha",
            "auctum", "descendens", "ascendens", "cavum", "linea", "episemInitium", "episemFinis");

    private final String src;
    private int pos;
    private String language = LilyPondPitch.DEFAULT_LANGUAGE;
    private final Map<String, Object> variables = new HashMap<>();
    private final Map<String, ContextType> contextTypes = new LinkedHashMap<>();
    private final Map<String, String> header = new LinkedHashMap<>();
    private final List<ScoreBlock> scores = new ArrayList<>();
    /** A {@code \layout} block omits time signatures or removes their engraver. */
    private boolean timeSignaturesHidden;

    // Default duration: a note written without one takes the previous note's.
    private NoteType defaultType = NoteType.QUARTER;
    private int defaultDots;

    private LilyPondParser(String src) {
        this.src = src;
    }

    /** Parse a file whose comments and includes have already been dealt with. */
    static Document parse(String text) {
        LilyPondParser parser = new LilyPondParser(text);
        parser.parseTopLevel();
        return new Document(parser.header, parser.scores, parser.contextTypes, parser.timeSignaturesHidden);
    }

    // ----- top level -----------------------------------------------------------------------------

    private void parseTopLevel() {
        while (true) {
            skipWhitespace();
            if (atEnd()) {
                return;
            }
            int before = pos;
            parseTopLevelStatement(scores, header);
            if (pos == before) {
                pos++;
            }
        }
    }

    /**
     * One statement at file, {@code \book} or {@code \bookpart} level: an
     * assignment, a block, or a music expression standing for a score.
     */
    private void parseTopLevelStatement(List<ScoreBlock> into, Map<String, String> headerInto) {
        char c = peek();
        if (c == '#' || c == '$') {
            skipScheme();
            return;
        }
        if (c == '"') {
            readString();
            return;
        }
        if (Character.isLetter(c)) {
            int save = pos;
            String name = readIdentifier();
            skipWhitespace();
            if (peek() == '=' && peek(1) != '=') {
                pos++;
                parseAssignment(name);
                return;
            }
            if (peek() == '.') {
                // `name.sub = value`: a nested assignment we don't need.
                skipToLineEnd();
                return;
            }
            pos = save;
        }
        if (c == '\\') {
            String command = peekCommandName();
            switch (command) {
                case "version", "include", "sourcefilename", "sourcefileline" -> {
                    readCommandName();
                    skipWhitespace();
                    if (peek() == '"') {
                        readString();
                    }
                    return;
                }
                case "language" -> {
                    readCommandName();
                    skipWhitespace();
                    if (peek() == '"') {
                        setLanguage(readString());
                    }
                    return;
                }
                case "header" -> {
                    readCommandName();
                    parseHeaderBlock(headerInto);
                    return;
                }
                case "paper", "layout", "midi" -> {
                    readCommandName();
                    parseLayoutBlock();
                    return;
                }
                case "score" -> {
                    readCommandName();
                    ScoreBlock block = parseScoreBlock();
                    if (block != null) {
                        into.add(block);
                    }
                    return;
                }
                case "book", "bookpart" -> {
                    readCommandName();
                    skipWhitespace();
                    if (peek() != '{') {
                        return;
                    }
                    pos++;
                    while (true) {
                        skipWhitespace();
                        if (atEnd()) {
                            return;
                        }
                        if (peek() == '}') {
                            pos++;
                            return;
                        }
                        int before = pos;
                        parseTopLevelStatement(into, headerInto);
                        if (pos == before) {
                            pos++;
                        }
                    }
                }
                case "markup", "markuplist" -> {
                    readCommandName();
                    parseMarkupArgument();
                    return;
                }
                default -> {
                    // A bare music expression is an implicit \score.
                }
            }
        }
        LyMusic music = parseMusicWithLyrics(Mode.NOTES);
        if (music != null && !(music instanceof LyMusic.Empty)) {
            into.add(new ScoreBlock(music, Map.of()));
        }
    }

    private void parseAssignment(String name) {
        skipWhitespace();
        char c = peek();
        if (c == '#' || c == '$') {
            String scheme = skipSchemeAndReturn();
            MusicFunction function = MusicFunction.of(scheme);
            if (function != null) {
                variables.put(name, function);
            }
            return;
        }
        if (c == '"') {
            variables.put(name, new Markup(readString(), false, false));
            return;
        }
        if (Character.isDigit(c) || c == '-') {
            readNumberToken();
            return;
        }
        if (c == '\\') {
            String command = peekCommandName();
            switch (command) {
                case "markup", "markuplist" -> {
                    readCommandName();
                    variables.put(name, parseMarkupArgument());
                    return;
                }
                case "layout", "midi", "paper", "header" -> {
                    readCommandName();
                    skipBalancedBlock();
                    return;
                }
                case "with" -> {
                    readCommandName();
                    variables.put(name, parseWithBlock());
                    return;
                }
                default -> {
                }
            }
        }
        LyMusic music = parseMusicWithLyrics(Mode.NOTES);
        if (music != null) {
            variables.put(name, music);
        }
    }

    private ScoreBlock parseScoreBlock() {
        skipWhitespace();
        if (peek() != '{') {
            return null;
        }
        pos++;
        List<LyMusic> music = new ArrayList<>();
        Map<String, String> scoreHeader = new LinkedHashMap<>();
        while (true) {
            skipWhitespace();
            if (atEnd()) {
                break;
            }
            if (peek() == '}') {
                pos++;
                break;
            }
            if (peek() == '\\') {
                String command = peekCommandName();
                if (command.equals("header")) {
                    readCommandName();
                    parseHeaderBlock(scoreHeader);
                    continue;
                }
                if (command.equals("layout") || command.equals("midi") || command.equals("paper")) {
                    readCommandName();
                    parseLayoutBlock();
                    continue;
                }
            }
            int before = pos;
            LyMusic m = parseMusicWithLyrics(Mode.NOTES);
            if (m != null && !(m instanceof LyMusic.Empty)) {
                music.add(m);
            }
            if (pos == before) {
                pos++;
            }
        }
        if (music.isEmpty()) {
            return null;
        }
        // A score holds one music expression; be lenient and play several together.
        return new ScoreBlock(music.size() == 1 ? music.get(0) : new LyMusic.Sim(music, false), scoreHeader);
    }

    private void parseHeaderBlock(Map<String, String> into) {
        skipWhitespace();
        if (peek() != '{') {
            return;
        }
        pos++;
        while (true) {
            skipWhitespace();
            if (atEnd()) {
                return;
            }
            char c = peek();
            if (c == '}') {
                pos++;
                return;
            }
            if (Character.isLetter(c)) {
                String key = readIdentifier();
                skipWhitespace();
                if (peek() != '=') {
                    continue;
                }
                pos++;
                skipWhitespace();
                String value = parseValueText();
                if (value != null && !value.isBlank() && !value.startsWith("#")) {
                    into.put(key, value.strip());
                }
                continue;
            }
            if (c == '#' || c == '$') {
                skipScheme();
                continue;
            }
            if (c == '\\') {
                readCommandName();
                continue;
            }
            pos++;
        }
    }

    /**
     * Read a {@code \paper}/{@code \layout}/{@code \midi} block, remembering
     * the context types it declares with {@code \context { \Base \name New }}.
     */
    private void parseLayoutBlock() {
        skipWhitespace();
        if (peek() != '{') {
            return;
        }
        pos++;
        while (true) {
            skipWhitespace();
            if (atEnd()) {
                return;
            }
            char c = peek();
            if (c == '}') {
                pos++;
                return;
            }
            if (c == '\\' && peekCommandName().equals("context")) {
                readCommandName();
                skipWhitespace();
                if (peek() == '{') {
                    parseContextDefinition();
                }
                continue;
            }
            skipToken();
        }
    }

    private void parseContextDefinition() {
        pos++; // '{'
        String base = null;
        String name = null;
        String delimiter = null;
        while (true) {
            skipWhitespace();
            if (atEnd()) {
                break;
            }
            char c = peek();
            if (c == '}') {
                pos++;
                break;
            }
            if (c == '\\') {
                String command = readCommandName();
                switch (command) {
                    case "name" -> {
                        skipWhitespace();
                        name = peek() == '"' ? readString() : readIdentifier();
                    }
                    case "remove", "consists", "accepts", "denies", "alias", "defaultchild", "type",
                         "description" -> {
                        skipWhitespace();
                        String arg = peek() == '"' ? readString()
                                : peek() == '#' ? skipSchemeAndReturn() : readIdentifier();
                        if (command.equals("remove") && arg.contains("System_start_delimiter")) {
                            delimiter = "none";
                        }
                        if (command.equals("remove") && arg.contains("Time_signature_engraver")) {
                            timeSignaturesHidden = true;
                        }
                    }
                    case "override", "set", "revert", "unset" -> skipPropertySetting(command);
                    case "omit" -> {
                        skipWhitespace();
                        if (readPropertyPath().endsWith("TimeSignature")) {
                            timeSignaturesHidden = true;
                        }
                    }
                    default -> {
                        if (base == null && !command.isEmpty() && Character.isUpperCase(command.charAt(0))) {
                            base = command;
                        }
                    }
                }
                continue;
            }
            if (Character.isLetter(c)) {
                String key = readIdentifier();
                skipWhitespace();
                if (peek() == '=') {
                    pos++;
                    skipWhitespace();
                    String value = peek() == '#' ? skipSchemeAndReturn() : parseValueText();
                    if (key.equals("systemStartDelimiter") && value != null) {
                        String v = value.toLowerCase(Locale.ROOT);
                        delimiter = v.contains("square") ? "square"
                                : v.contains("brace") ? "brace"
                                : v.contains("bracket") ? "bracket"
                                : v.contains("bar") ? "line" : delimiter;
                    }
                }
                continue;
            }
            skipToken();
        }
        if (name != null && base != null) {
            contextTypes.put(name, new ContextType(base, delimiter));
        }
    }

    /** {@code \with { key = value ... }}: the plain-text values it assigns. */
    private Map<String, String> parseWithBlock() {
        skipWhitespace();
        Map<String, String> out = new LinkedHashMap<>();
        if (peek() == '\\') {
            String name = readCommandName();
            if (variables.get(name) instanceof WithBlock block) {
                out.putAll(block.values());
            }
            return out;
        }
        if (peek() != '{') {
            return out;
        }
        pos++;
        while (true) {
            skipWhitespace();
            if (atEnd()) {
                return out;
            }
            char c = peek();
            if (c == '}') {
                pos++;
                return out;
            }
            if (c == '\\') {
                String command = readCommandName();
                switch (command) {
                    case "override" -> {
                        String[] setting = readOverride();
                        if (setting[0].endsWith("Stem.direction") && setting[1] != null) {
                            out.put("Stem.direction", setting[1]);
                        }
                    }
                    case "set", "revert", "unset" -> skipPropertySetting(command);
                    case "hide", "omit" -> {
                        skipWhitespace();
                        String path = readPropertyPath();
                        out.put("hide." + path.substring(path.lastIndexOf('.') + 1), "##t");
                    }
                    case "consists", "remove", "accepts", "denies", "name", "alias" -> {
                        skipWhitespace();
                        if (peek() == '"') {
                            readString();
                        } else if (peek() == '#') {
                            skipScheme();
                        } else {
                            readIdentifier();
                        }
                    }
                    default -> {
                    }
                }
                continue;
            }
            if (Character.isLetter(c)) {
                String key = readPropertyPath();
                skipWhitespace();
                if (peek() == '=') {
                    pos++;
                    skipWhitespace();
                    String value = parseValueText();
                    if (value != null) {
                        out.put(key, value);
                    }
                }
                continue;
            }
            skipToken();
        }
    }

    /**
     * A function defined in Scheme ({@code define-music-function} and
     * friends): the type predicates of its arguments, so calls can be skipped.
     */
    private record MusicFunction(List<String> argumentTypes, boolean event) {

        static MusicFunction of(String scheme) {
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("define-(music|event|scheme|void)-function\\s*\\([^)]*\\)\\s*\\(([^)]*)\\)")
                    .matcher(scheme);
            if (!m.find()) {
                return null;
            }
            List<String> types = new ArrayList<>();
            for (String t : m.group(2).trim().split("\\s+")) {
                if (!t.isEmpty()) {
                    types.add(t);
                }
            }
            return new MusicFunction(types, m.group(1).equals("event"));
        }
    }

    /** Skip the arguments of a Scheme-defined function up to its first music argument. */
    private void skipFunctionArguments(MusicFunction function) {
        for (String type : function.argumentTypes()) {
            if (type.contains("music") || type.contains("event") || type.contains("pitch")
                    || type.contains("duration")) {
                return;
            }
            skipWhitespace();
            char c = peek();
            if (c == '#' || c == '$') {
                skipScheme();
            } else if (c == '"') {
                readString();
            } else if (c == '\\' && (peekCommandName().equals("markup"))) {
                readCommandName();
                parseMarkupArgument();
            } else if (c == '\\' && variables.get(peekCommandName()) instanceof Markup) {
                readCommandName();
            } else if (c == '{' && type.contains("markup")) {
                parseMarkupArgument();
            } else if (Character.isDigit(c) || c == '-' || c == '+' || c == '.') {
                pos++;
                while (!atEnd() && (Character.isDigit(peek()) || peek() == '.' || peek() == '/')) {
                    pos++;
                }
            } else if (Character.isLetter(c)) {
                readPropertyPath();
            } else {
                return;
            }
        }
    }

    /** A {@code \with} block stored in a variable. */
    private record WithBlock(Map<String, String> values) {
    }

    /**
     * The value of an assignment or property as plain text: a string, markup,
     * markup variable, number or word; Scheme values give {@code null}.
     */
    private String parseValueText() {
        skipWhitespace();
        char c = peek();
        if (c == '"') {
            return readString();
        }
        if (c == '#' || c == '$') {
            int start = pos;
            skipScheme();
            String scheme = src.substring(start, pos).trim();
            if (scheme.startsWith("#\"") && scheme.endsWith("\"")) {
                return scheme.substring(2, scheme.length() - 1);
            }
            // Other Scheme values come back raw (e.g. "##f", "#11").
            return scheme;
        }
        if (c == '\\') {
            String command = peekCommandName();
            if (command.equals("markup") || command.equals("markuplist")) {
                readCommandName();
                return parseMarkupArgument().text();
            }
            readCommandName();
            Object value = variables.get(command);
            if (value instanceof Markup m) {
                return m.text();
            }
            return null;
        }
        if (Character.isLetterOrDigit(c) || c == '-') {
            int start = pos;
            while (!atEnd() && !Character.isWhitespace(peek()) && "{}\\\"#".indexOf(peek()) < 0) {
                pos++;
            }
            return src.substring(start, pos);
        }
        return null;
    }

    // ----- music ---------------------------------------------------------------------------------

    /** A music expression, plus any {@code \addlyrics} blocks following it. */
    private LyMusic parseMusicWithLyrics(Mode mode) {
        LyMusic music = parseMusic(mode);
        if (music == null) {
            return null;
        }
        List<LyMusic.Lyrics> lyrics = new ArrayList<>();
        while (true) {
            int save = pos;
            skipWhitespace();
            if (peek() == '\\' && peekCommandName().equals("addlyrics")) {
                readCommandName();
                lyrics.add(parseLyricsArgument());
            } else {
                pos = save;
                break;
            }
        }
        return lyrics.isEmpty() ? music : new LyMusic.AddLyrics(music, lyrics);
    }

    /**
     * One music expression, or {@code null} at a closing delimiter or the end
     * of input.
     */
    private LyMusic parseMusic(Mode mode) {
        skipWhitespace();
        if (atEnd()) {
            return null;
        }
        char c = peek();
        if (c == '}' || (c == '>' && peek(1) == '>')) {
            return null;
        }
        if (c == '{') {
            return parseSequential(mode);
        }
        if (c == '<' && peek(1) == '<') {
            return parseSimultaneous(mode);
        }
        if (c == '<' && mode != Mode.CHORDS) {
            return parseChord();
        }
        if (c == '\\') {
            if (peek(1) == '\\') {
                // Voice separator outside << >>: meaningless here.
                pos += 2;
                return new LyMusic.Empty();
            }
            return parseCommand(mode);
        }
        if (c == '#' || c == '$') {
            skipScheme();
            return new LyMusic.Empty();
        }
        if (c == '"') {
            readString();
            return new LyMusic.Empty();
        }
        if (c == '|') {
            pos++;
            return new LyMusic.Empty();
        }
        if ((c == '-' || c == '+') && (Character.isDigit(peek(1)) || peek(1) == '.')) {
            // A number argument of a function we don't know: not music.
            pos++;
            while (!atEnd() && (Character.isDigit(peek()) || peek() == '.')) {
                pos++;
            }
            return new LyMusic.Empty();
        }
        if (Character.isDigit(c)) {
            if (mode == Mode.CHORDS) {
                Duration d = parseDuration();
                return d == null ? skipChar() : new LyMusic.ChordSymbol(null, d.length());
            }
            return parseEvent(LyMusic.Event.Kind.REPEAT_NOTE, List.of());
        }
        if (Character.isLetter(c)) {
            if (mode == Mode.CHORDS) {
                return parseChordModeEvent();
            }
            return parseNoteOrRest(mode);
        }
        return skipChar();
    }

    private LyMusic skipChar() {
        pos++;
        return new LyMusic.Empty();
    }

    private LyMusic parseSequential(Mode mode) {
        pos++; // '{'
        List<LyMusic> items = new ArrayList<>();
        while (true) {
            skipWhitespace();
            if (atEnd()) {
                break;
            }
            if (peek() == '}') {
                pos++;
                break;
            }
            if (peek() == '>' && peek(1) == '>') {
                // Stray '>>' inside braces: consume and keep going.
                pos += 2;
                continue;
            }
            int before = pos;
            LyMusic m = parseMusicWithLyrics(mode);
            if (m != null && !(m instanceof LyMusic.Empty)) {
                items.add(m);
            }
            if (pos == before) {
                pos++;
            }
        }
        return new LyMusic.Seq(items);
    }

    private LyMusic parseSimultaneous(Mode mode) {
        pos += 2; // '<<'
        List<List<LyMusic>> groups = new ArrayList<>();
        List<LyMusic> current = new ArrayList<>();
        boolean separated = false;
        while (true) {
            skipWhitespace();
            if (atEnd()) {
                break;
            }
            if (peek() == '>' && peek(1) == '>') {
                pos += 2;
                break;
            }
            if (peek() == '\\' && peek(1) == '\\') {
                pos += 2;
                separated = true;
                groups.add(current);
                current = new ArrayList<>();
                continue;
            }
            if (peek() == '}') {
                // Unbalanced '}' inside << >>: leave it to the caller.
                break;
            }
            int before = pos;
            LyMusic m = parseMusicWithLyrics(mode);
            if (m != null && !(m instanceof LyMusic.Empty)) {
                current.add(m);
            }
            if (pos == before) {
                pos++;
            }
        }
        groups.add(current);
        if (!separated) {
            return new LyMusic.Sim(current, false);
        }
        List<LyMusic> voices = new ArrayList<>();
        for (List<LyMusic> group : groups) {
            voices.add(group.size() == 1 ? group.get(0) : new LyMusic.Seq(group));
        }
        return new LyMusic.Sim(voices, true);
    }

    // ----- events --------------------------------------------------------------------------------

    private LyMusic parseNoteOrRest(Mode mode) {
        int save = pos;
        String word = readNoteWord();
        if (word.isEmpty()) {
            return skipChar();
        }
        // `name = value` inside music is an assignment (rare, but legal).
        int afterWord = pos;
        skipWhitespace();
        if (peek() == '=' && peek(1) != '=' && !word.equals("r") && !word.equals("s")) {
            LilyPondPitch.StepAlter asPitch = LilyPondPitch.lookup(word, language);
            if (asPitch == null || peekAssignmentValue()) {
                pos++;
                parseAssignment(word);
                return new LyMusic.Empty();
            }
        }
        pos = afterWord;
        switch (word) {
            case "r" -> {
                return parseEvent(LyMusic.Event.Kind.REST, List.of());
            }
            case "R" -> {
                return parseEvent(LyMusic.Event.Kind.MEASURE_REST, List.of());
            }
            case "s" -> {
                return parseEvent(LyMusic.Event.Kind.SKIP, List.of());
            }
            case "q" -> {
                return parseEvent(LyMusic.Event.Kind.REPEAT_CHORD, List.of());
            }
            default -> {
            }
        }
        LilyPondPitch.StepAlter sa = mode == Mode.DRUMS ? drumPitch(word) : LilyPondPitch.lookup(word, language);
        if (sa == null) {
            // Not a note name: a stray word (e.g. `\tempo Largo` arguments).
            pos = save + word.length();
            return new LyMusic.Empty();
        }
        LyMusic.EventPitch pitch = parsePitchSuffix(sa, mode == Mode.DRUMS);
        return parseEvent(LyMusic.Event.Kind.NOTE, List.of(pitch));
    }

    private boolean peekAssignmentValue() {
        int save = pos;
        pos++;
        skipWhitespace();
        char c = peek();
        pos = save;
        return c == '{' || c == '\\' || c == '"' || c == '#';
    }

    /** Octave marks, accidental marks and octave check after a note name. */
    private LyMusic.EventPitch parsePitchSuffix(LilyPondPitch.StepAlter sa, boolean absolute) {
        int marks = absolute ? 1 : 0;
        while (!atEnd() && (peek() == '\'' || peek() == ',')) {
            marks += peek() == '\'' ? 1 : -1;
            pos++;
        }
        boolean forced = false;
        while (!atEnd() && (peek() == '!' || peek() == '?')) {
            forced = true;
            pos++;
        }
        Integer check = null;
        if (peek() == '=' && peek(1) != '=') {
            int save = pos;
            pos++;
            int checkMarks = 0;
            boolean any = false;
            while (!atEnd() && (peek() == '\'' || peek() == ',')) {
                checkMarks += peek() == '\'' ? 1 : -1;
                any = true;
                pos++;
            }
            if (any || Character.isDigit(peek()) || Character.isWhitespace(peek())) {
                check = checkMarks;
            } else {
                pos = save;
            }
        }
        return new LyMusic.EventPitch(new LyMusic.RawPitch(sa.step(), sa.alter(), marks), check, forced, false);
    }

    /** Duration, tremolo and post-events following an event's pitch(es). */
    private LyMusic parseEvent(LyMusic.Event.Kind kind, List<LyMusic.EventPitch> pitches) {
        Duration duration = parseDuration();
        if (duration == null) {
            duration = new Duration(defaultType, defaultDots, lengthOf(defaultType, defaultDots));
        }
        skipTremolo();
        List<LyMusic.Post> post = parsePostEvents();
        if (post.stream().anyMatch(p -> p instanceof LyMusic.PitchedRest)) {
            kind = LyMusic.Event.Kind.REST;
        }
        return new LyMusic.Event(kind, pitches, duration.type(), duration.dots(), duration.length(), post);
    }

    private LyMusic parseChord() {
        pos++; // '<'
        List<LyMusic.EventPitch> pitches = new ArrayList<>();
        while (true) {
            skipWhitespace();
            if (atEnd()) {
                break;
            }
            char c = peek();
            if (c == '>') {
                pos++;
                break;
            }
            if (Character.isLetter(c)) {
                String word = readNoteWord();
                LilyPondPitch.StepAlter sa = LilyPondPitch.lookup(word, language);
                if (sa == null) {
                    continue;
                }
                LyMusic.EventPitch pitch = parsePitchSuffix(sa, false);
                boolean tie = false;
                for (LyMusic.Post p : parsePostEvents()) {
                    if (p instanceof LyMusic.Tie) {
                        tie = true;
                    }
                }
                pitches.add(tie ? new LyMusic.EventPitch(pitch.pitch(), pitch.octaveCheck(),
                        pitch.forcedAccidental(), true) : pitch);
                continue;
            }
            if (c == '\\') {
                String command = readCommandName();
                skipCommandArguments(command);
                continue;
            }
            if (c == '#' || c == '$') {
                skipScheme();
                continue;
            }
            if (c == '"') {
                readString();
                continue;
            }
            pos++;
        }
        if (pitches.isEmpty()) {
            Duration d = parseDuration();
            skipTremolo();
            parsePostEvents();
            return d == null ? new LyMusic.Empty() : new LyMusic.Event(LyMusic.Event.Kind.SKIP, List.of(),
                    d.type(), d.dots(), d.length(), List.of());
        }
        return parseEvent(LyMusic.Event.Kind.NOTE, pitches);
    }

    private void skipTremolo() {
        if (peek() == ':') {
            pos++;
            while (!atEnd() && Character.isDigit(peek())) {
                pos++;
            }
        }
    }

    /** A written duration: its display type and dots, and its length in quarters. */
    private record Duration(NoteType type, int dots, Fraction length) {
    }

    /**
     * Parse a duration ({@code 4.}, {@code \breve}, {@code 1*3/4}) if one
     * starts here, making it the new default duration.
     */
    private Duration parseDuration() {
        NoteType type = null;
        if (Character.isDigit(peek())) {
            int start = pos;
            while (!atEnd() && Character.isDigit(peek())) {
                pos++;
            }
            type = typeForNumber(Integer.parseInt(src.substring(start, Math.min(pos, start + 4))));
        } else if (peek() == '\\') {
            String name = peekCommandName();
            type = switch (name) {
                case "breve" -> NoteType.BREVE;
                case "longa" -> NoteType.LONG;
                case "maxima" -> NoteType.MAXIMA;
                default -> null;
            };
            if (type != null) {
                readCommandName();
            }
        }
        if (type == null) {
            return null;
        }
        int dots = 0;
        while (peek() == '.') {
            dots++;
            pos++;
        }
        defaultType = type;
        defaultDots = dots;
        Fraction length = lengthOf(type, dots);
        while (true) {
            // Multipliers may be spaced out: `16 * 1/8`.
            int save = pos;
            skipWhitespace();
            if (peek() != '*') {
                pos = save;
                break;
            }
            pos++;
            skipWhitespace();
            long num = readInt(1);
            long den = 1;
            if (peek() == '/') {
                pos++;
                den = Math.max(1, readInt(1));
            }
            length = multiply(length, Fraction.of(num, den));
        }
        return new Duration(type, dots, length);
    }

    /** Post-events after an event: ties, slurs, beams, articulations, dynamics, texts. */
    private List<LyMusic.Post> parsePostEvents() {
        List<LyMusic.Post> out = new ArrayList<>();
        while (true) {
            int save = pos;
            skipWhitespace();
            if (atEnd()) {
                break;
            }
            char c = peek();
            if (c == '~') {
                pos++;
                out.add(new LyMusic.Tie());
                continue;
            }
            if (c == '(' || c == ')') {
                pos++;
                out.add(new LyMusic.Slur(c == '(', false));
                continue;
            }
            if (c == '[' || c == ']') {
                pos++;
                out.add(new LyMusic.Beam(c == '['));
                continue;
            }
            if (c == ':' ) {
                skipTremolo();
                continue;
            }
            if (c == '-' || c == '^' || c == '_') {
                Placement placement = c == '^' ? Placement.ABOVE : c == '_' ? Placement.BELOW : Placement.DEFAULT;
                pos++;
                LyMusic.Post p = parseDirectionalPost(placement);
                if (p != null) {
                    out.add(p);
                }
                continue;
            }
            if (c == '\\') {
                char next = peek(1);
                if (next == '<' || next == '>' || next == '!') {
                    pos += 2;
                    out.add(new LyMusic.Hairpin(next == '<' ? DirectionType.WedgeType.CRESCENDO
                            : next == '>' ? DirectionType.WedgeType.DIMINUENDO : DirectionType.WedgeType.STOP));
                    continue;
                }
                if (next == '(' || next == ')') {
                    pos += 2;
                    out.add(new LyMusic.Slur(next == '(', true));
                    continue;
                }
                String name = peekCommandName();
                if (name.equals("tweak") || name.equals("offset")) {
                    readCommandName();
                    skipCommandArguments(name);
                    continue;
                }
                if (variables.get(name) instanceof MusicFunction function && function.event()) {
                    readCommandName();
                    skipFunctionArguments(function);
                    continue;
                }
                if (name.equals("rest")) {
                    readCommandName();
                    out.add(new LyMusic.PitchedRest());
                    continue;
                }
                if (isPostEventCommand(name)) {
                    readCommandName();
                    LyMusic.Post p = postEventFor(name, Placement.DEFAULT);
                    if (p != null) {
                        out.add(p);
                    }
                    continue;
                }
            }
            pos = save;
            break;
        }
        return out;
    }

    /** After {@code -}, {@code ^} or {@code _}: the post-event it introduces. */
    private LyMusic.Post parseDirectionalPost(Placement placement) {
        char c = peek();
        switch (c) {
            case '.' -> {
                pos++;
                return new LyMusic.Artic(Articulation.STACCATO);
            }
            case '!' -> {
                pos++;
                return new LyMusic.Artic(Articulation.STACCATO);
            }
            case '>', '^' -> {
                pos++;
                return new LyMusic.Artic(Articulation.ACCENT);
            }
            case '-', '_', '+' -> {
                pos++;
                return null;
            }
            case '~' -> {
                pos++;
                return new LyMusic.Tie();
            }
            case '(' , ')' -> {
                pos++;
                return new LyMusic.Slur(c == '(', false);
            }
            case '"' -> {
                return new LyMusic.Text(readString(), placement, false, false);
            }
            case '#', '$' -> {
                int start = pos;
                skipScheme();
                String scheme = src.substring(start, pos);
                if (scheme.startsWith("#\"") && scheme.endsWith("\"")) {
                    return new LyMusic.Text(scheme.substring(2, scheme.length() - 1), placement, false, false);
                }
                return null;
            }
            default -> {
            }
        }
        if (Character.isDigit(c)) {
            while (!atEnd() && Character.isDigit(peek())) {
                pos++;
            }
            return null;
        }
        if (c == '\\') {
            char next = peek(1);
            if (next == '<' || next == '>' || next == '!') {
                pos += 2;
                return new LyMusic.Hairpin(next == '<' ? DirectionType.WedgeType.CRESCENDO
                        : next == '>' ? DirectionType.WedgeType.DIMINUENDO : DirectionType.WedgeType.STOP);
            }
            if (next == '(' || next == ')') {
                pos += 2;
                return new LyMusic.Slur(next == '(', true);
            }
            String name = readCommandName();
            if (name.equals("markup")) {
                Markup m = parseMarkupArgument();
                return new LyMusic.Text(m.text(), placement, m.bold(), m.italic());
            }
            if (name.equals("tweak") || name.equals("offset")) {
                skipCommandArguments(name);
                skipWhitespace();
                if (peek() == '-' || peek() == '^' || peek() == '_') {
                    pos++;
                }
                return parseDirectionalPost(placement);
            }
            Object value = variables.get(name);
            if (value instanceof Markup m) {
                return new LyMusic.Text(m.text(), placement, m.bold(), m.italic());
            }
            return postEventFor(name, placement);
        }
        return null;
    }

    private static boolean isPostEventCommand(String name) {
        return DYNAMICS.contains(name) || IGNORED_POST_EVENTS.contains(name) || switch (name) {
            case "staccato", "staccatissimo", "accent", "marcato", "upbow", "downbow", "arpeggio", "trill",
                 "cresc", "crescTextCresc", "decresc", "dim", "cr", "decr", "enddecr", "endcresc", "crescHairpin",
                 "dimHairpin", "fermataMarkup" -> true;
            default -> false;
        };
    }

    private static LyMusic.Post postEventFor(String name, Placement placement) {
        if (DYNAMICS.contains(name)) {
            return new LyMusic.Dynamic(name);
        }
        return switch (name) {
            case "staccato", "staccatissimo" -> new LyMusic.Artic(Articulation.STACCATO);
            case "accent", "marcato" -> new LyMusic.Artic(Articulation.ACCENT);
            case "upbow" -> new LyMusic.Artic(Articulation.UP_BOW);
            case "downbow" -> new LyMusic.Artic(Articulation.DOWN_BOW);
            case "arpeggio" -> new LyMusic.Artic(Articulation.ROLL);
            case "trill" -> new LyMusic.Text("tr", placement == Placement.DEFAULT ? Placement.ABOVE : placement,
                    true, true);
            case "cresc", "cr", "crescHairpin", "crescTextCresc" ->
                    new LyMusic.Hairpin(DirectionType.WedgeType.CRESCENDO);
            case "decresc", "dim", "decr", "dimHairpin" -> new LyMusic.Hairpin(DirectionType.WedgeType.DIMINUENDO);
            case "enddecr", "endcresc" -> new LyMusic.Hairpin(DirectionType.WedgeType.STOP);
            default -> null;
        };
    }

    private LyMusic parseChordModeEvent() {
        String word = readNoteWord();
        if (word.equals("r") || word.equals("s") || word.equals("R")) {
            Duration d = parseDuration();
            Fraction length = d == null ? lengthOf(defaultType, defaultDots) : d.length();
            parsePostEvents();
            return new LyMusic.ChordSymbol(null, length);
        }
        LilyPondPitch.StepAlter root = LilyPondPitch.lookup(word, language);
        if (root == null) {
            return new LyMusic.Empty();
        }
        while (peek() == '\'' || peek() == ',') {
            pos++;
        }
        Duration d = parseDuration();
        Fraction length = d == null ? lengthOf(defaultType, defaultDots) : d.length();
        String quality = "";
        if (peek() == ':') {
            pos++;
            int start = pos;
            while (!atEnd() && !Character.isWhitespace(peek()) && "/}\\~|".indexOf(peek()) < 0) {
                pos++;
            }
            quality = src.substring(start, pos);
        }
        Harmony.Bass bass = null;
        while (peek() == '/') {
            pos++;
            if (peek() == '+') {
                pos++;
            }
            String bassWord = readNoteWord();
            LilyPondPitch.StepAlter b = LilyPondPitch.lookup(bassWord, language);
            if (b != null && bass == null) {
                bass = new Harmony.Bass(b.step(), b.alter());
            }
            while (peek() == '\'' || peek() == ',') {
                pos++;
            }
        }
        parsePostEvents();
        HarmonyKind kind = CHORD_QUALITIES.get(quality.toLowerCase(Locale.ROOT));
        String text = null;
        if (kind == null) {
            kind = HarmonyKind.OTHER;
            text = quality;
        }
        return new LyMusic.ChordSymbol(new Harmony(new Harmony.Root(root.step(), root.alter()), kind,
                Optional.ofNullable(bass), Optional.ofNullable(text)), length);
    }

    /** Chord-mode modifiers ({@code c1:m7}) mapped to a {@link HarmonyKind}. */
    private static final Map<String, HarmonyKind> CHORD_QUALITIES = Map.ofEntries(
            Map.entry("", HarmonyKind.MAJOR),
            Map.entry("maj", HarmonyKind.MAJOR),
            Map.entry("5", HarmonyKind.MAJOR),
            Map.entry("m", HarmonyKind.MINOR),
            Map.entry("min", HarmonyKind.MINOR),
            Map.entry("m5", HarmonyKind.MINOR),
            Map.entry("7", HarmonyKind.DOMINANT_SEVENTH),
            Map.entry("maj7", HarmonyKind.MAJOR_SEVENTH),
            Map.entry("m7", HarmonyKind.MINOR_SEVENTH),
            Map.entry("min7", HarmonyKind.MINOR_SEVENTH),
            Map.entry("m7.5-", HarmonyKind.HALF_DIMINISHED),
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
            Map.entry("1.5", HarmonyKind.POWER));

    /** Drum-mode note names mapped to the staff position LilyPond prints them at. */
    private static LilyPondPitch.StepAlter drumPitch(String word) {
        return switch (word) {
            case "bd", "bassdrum", "bda", "acousticbassdrum" -> new LilyPondPitch.StepAlter(Step.F, 0);
            case "hh", "hihat", "hhc", "closedhihat", "hho", "openhihat", "hhp", "pedalhihat", "hhho",
                 "halfopenhihat", "cymc", "crashcymbal", "cymr", "ridecymbal", "cymca", "cymra", "cymch",
                 "chinesecymbal", "cyms", "splashcymbal", "rb", "ridebell" -> new LilyPondPitch.StepAlter(Step.G, 0);
            case "tomh", "hightom", "tommh", "highmidtom" -> new LilyPondPitch.StepAlter(Step.E, 0);
            case "tomml", "lowmidtom" -> new LilyPondPitch.StepAlter(Step.D, 0);
            case "toml", "lowtom", "tomfh", "highfloortom" -> new LilyPondPitch.StepAlter(Step.A, 0);
            case "tomfl", "lowfloortom" -> new LilyPondPitch.StepAlter(Step.G, 0);
            default -> new LilyPondPitch.StepAlter(Step.C, 0);
        };
    }

    // ----- commands ------------------------------------------------------------------------------

    private LyMusic parseCommand(Mode mode) {
        String name = readCommandName();
        if (name.isEmpty()) {
            pos++;
            return new LyMusic.Empty();
        }
        Object variable = variables.get(name);
        if (variable instanceof LyMusic music) {
            return music;
        }
        if (variable instanceof MusicFunction function) {
            // Skip its non-music arguments; music arguments follow as usual.
            skipFunctionArguments(function);
            return new LyMusic.Empty();
        }
        if (variable != null) {
            return new LyMusic.Empty();
        }
        switch (name) {
            case "relative" -> {
                LyMusic.RawPitch reference = parseOptionalPitch();
                LyMusic body = parseMusic(mode);
                return body == null ? new LyMusic.Empty() : new LyMusic.Relative(reference, body);
            }
            case "fixed" -> {
                LyMusic.RawPitch reference = parseOptionalPitch();
                LyMusic body = parseMusic(mode);
                return body == null ? new LyMusic.Empty() : new LyMusic.Fixed(reference, body);
            }
            case "absolute" -> {
                LyMusic body = parseMusic(mode);
                return body == null ? new LyMusic.Empty() : new LyMusic.Fixed(null, body);
            }
            case "transpose" -> {
                LyMusic.RawPitch from = parseOptionalPitch();
                LyMusic.RawPitch to = parseOptionalPitch();
                LyMusic body = parseMusic(mode);
                if (body == null) {
                    return new LyMusic.Empty();
                }
                return from == null || to == null ? body : new LyMusic.Transpose(from, to, body);
            }
            case "tuplet" -> {
                int[] ratio = parseFractionArgument();
                skipWhitespace();
                Fraction span = null;
                if (Character.isDigit(peek())) {
                    NoteType savedType = defaultType;
                    int savedDots = defaultDots;
                    Duration d = parseDuration();
                    defaultType = savedType;
                    defaultDots = savedDots;
                    span = d == null ? null : d.length();
                }
                LyMusic body = parseMusic(mode);
                if (body == null || ratio == null) {
                    return body == null ? new LyMusic.Empty() : body;
                }
                return new LyMusic.Tuplet(ratio[0], ratio[1], span, body);
            }
            case "times" -> {
                int[] ratio = parseFractionArgument();
                LyMusic body = parseMusic(mode);
                if (body == null || ratio == null) {
                    return body == null ? new LyMusic.Empty() : body;
                }
                return new LyMusic.Tuplet(ratio[1], ratio[0], null, body);
            }
            case "scaleDurations" -> {
                int[] ratio = parseFractionArgument();
                LyMusic body = parseMusic(mode);
                if (body == null || ratio == null) {
                    return body == null ? new LyMusic.Empty() : body;
                }
                return new LyMusic.Scale(Fraction.of(ratio[0], ratio[1]), body);
            }
            case "repeat" -> {
                skipWhitespace();
                String type = readIdentifier();
                skipWhitespace();
                int count = (int) Math.max(1, readInt(2));
                LyMusic body = parseMusic(mode);
                if (body == null) {
                    return new LyMusic.Empty();
                }
                List<LyMusic> alternatives = new ArrayList<>();
                int save = pos;
                skipWhitespace();
                if (peek() == '\\' && peekCommandName().equals("alternative")) {
                    readCommandName();
                    skipWhitespace();
                    if (peek() == '{') {
                        pos++;
                        while (true) {
                            skipWhitespace();
                            if (atEnd()) {
                                break;
                            }
                            if (peek() == '}') {
                                pos++;
                                break;
                            }
                            int before = pos;
                            LyMusic alt = parseMusic(mode);
                            if (alt != null && !(alt instanceof LyMusic.Empty)) {
                                alternatives.add(alt);
                            }
                            if (pos == before) {
                                pos++;
                            }
                        }
                    }
                } else {
                    pos = save;
                }
                return new LyMusic.Repeat(type, count, body, alternatives);
            }
            case "alternative", "volta" -> {
                if (name.equals("volta")) {
                    skipWhitespace();
                    readNumberToken();
                }
                LyMusic body = parseMusic(mode);
                return body == null ? new LyMusic.Empty() : body;
            }
            case "grace", "acciaccatura", "appoggiatura", "slashedGrace" -> {
                LyMusic body = parseMusic(mode);
                return body == null ? new LyMusic.Empty() : new LyMusic.Grace(body);
            }
            case "afterGrace" -> {
                skipWhitespace();
                if (Character.isDigit(peek())) {
                    parseFractionArgument();
                }
                LyMusic main = parseMusic(mode);
                LyMusic grace = parseMusic(mode);
                if (main == null) {
                    return new LyMusic.Empty();
                }
                return grace == null ? main : new LyMusic.Seq(List.of(main, new LyMusic.Grace(grace)));
            }
            case "new", "context" -> {
                return parseContext(name.equals("new"), mode);
            }
            case "lyricsto" -> {
                skipWhitespace();
                String voice = peek() == '"' ? readString() : readIdentifier();
                return new LyMusic.LyricsTo(voice, parseLyricsArgument());
            }
            case "addlyrics" -> {
                return parseLyricsArgument();
            }
            case "lyricmode", "lyrics" -> {
                return parseLyricsArgument();
            }
            case "chordmode" -> {
                LyMusic body = parseMusic(Mode.CHORDS);
                return body == null ? new LyMusic.Empty() : body;
            }
            case "chords" -> {
                LyMusic body = parseMusic(Mode.CHORDS);
                return body == null ? new LyMusic.Empty()
                        : new LyMusic.Context("ChordNames", null, true, Map.of(), body);
            }
            case "drummode", "drums" -> {
                LyMusic body = parseMusic(Mode.DRUMS);
                if (body == null) {
                    return new LyMusic.Empty();
                }
                return name.equals("drums") ? new LyMusic.Context("DrumStaff", null, true, Map.of(), body) : body;
            }
            case "figuremode", "figures", "notemode" -> {
                if (name.equals("notemode")) {
                    LyMusic body = parseMusic(Mode.NOTES);
                    return body == null ? new LyMusic.Empty() : body;
                }
                skipWhitespace();
                if (peek() == '{') {
                    skipBalancedBlock();
                }
                return new LyMusic.Empty();
            }
            case "markup", "markuplist" -> {
                parseMarkupArgument();
                return new LyMusic.Empty();
            }
            case "time" -> {
                skipWhitespace();
                // Optional beat structure: `\time 2,2,3 7/8`.
                int save = pos;
                while (Character.isDigit(peek()) || peek() == ',') {
                    pos++;
                }
                if (!Character.isWhitespace(peek())) {
                    pos = save;
                }
                skipWhitespace();
                int[] f = parseFractionArgument();
                return f == null ? new LyMusic.Empty() : new LyMusic.TimeSig(f[0], f[1]);
            }
            case "key" -> {
                LyMusic.RawPitch tonic = parseOptionalPitch();
                skipWhitespace();
                String keyMode = "major";
                if (peek() == '\\') {
                    keyMode = readCommandName();
                } else if (peek() == '#') {
                    skipScheme();
                }
                return tonic == null ? new LyMusic.Empty() : new LyMusic.KeySig(tonic, keyMode);
            }
            case "clef" -> {
                skipWhitespace();
                String clef = peek() == '"' ? readString() : readWordToken();
                return new LyMusic.ClefChange(clef);
            }
            case "bar" -> {
                skipWhitespace();
                return peek() == '"' ? new LyMusic.BarLine(readString()) : new LyMusic.Empty();
            }
            case "partial" -> {
                skipWhitespace();
                NoteType savedType = defaultType;
                int savedDots = defaultDots;
                Duration d = parseDuration();
                defaultType = savedType;
                defaultDots = savedDots;
                return d == null ? new LyMusic.Empty() : new LyMusic.Partial(d.length());
            }
            case "tempo" -> {
                return parseTempo();
            }
            case "mark" -> {
                skipWhitespace();
                if (peek() == '\\') {
                    String arg = peekCommandName();
                    if (arg.equals("default")) {
                        readCommandName();
                        return new LyMusic.Mark(null);
                    }
                    if (arg.equals("markup")) {
                        readCommandName();
                        return new LyMusic.Mark(parseMarkupArgument().text());
                    }
                    readCommandName();
                    return variables.get(arg) instanceof Markup m ? new LyMusic.Mark(m.text()) : new LyMusic.Empty();
                }
                if (peek() == '"') {
                    return new LyMusic.Mark(readString());
                }
                if (peek() == '#') {
                    String scheme = skipSchemeAndReturn().trim();
                    String digits = scheme.replaceAll("[^0-9]", "");
                    if (!digits.isEmpty()) {
                        int n = Integer.parseInt(digits);
                        return new LyMusic.Mark(String.valueOf((char) ('A' + Math.max(0, n - 1) % 26)));
                    }
                }
                return new LyMusic.Empty();
            }
            case "textMark", "textEndMark", "sectionLabel" -> {
                Markup m = parseMarkupArgument();
                return m.text().isBlank() ? new LyMusic.Empty() : new LyMusic.TextMark(m.text());
            }
            case "set", "override", "unset", "revert" -> {
                return parsePropertyCommand(name);
            }
            case "once", "temporary", "undo", "single" -> {
                return new LyMusic.Empty();
            }
            case "hide", "omit" -> {
                skipWhitespace();
                if (peek() == '#') {
                    skipScheme();
                    return new LyMusic.Empty();
                }
                String path = readPropertyPath();
                int dot = path.lastIndexOf('.');
                return new LyMusic.SetProperty(dot >= 0 ? path.substring(0, dot) : null,
                        "hide." + path.substring(dot + 1), "##t");
            }
            case "tweak", "offset", "accidentalStyle", "transposition", "instrumentSwitch",
                 "ottava", "label", "bendAfter", "footnote", "parenthesize", "shape", "unfolded",
                 "keepWithTag_", "displayLilyMusic", "void", "harmonicByFret", "harmonicByRatio" -> {
                skipCommandArguments(name);
                return new LyMusic.Empty();
            }
            case "keepWithTag", "removeWithTag", "tag", "pushToTag", "appendToTag" -> {
                Set<String> tags = parseTagList();
                LyMusic body = parseMusic(mode);
                if (body == null) {
                    return new LyMusic.Empty();
                }
                return switch (name) {
                    case "keepWithTag" -> new LyMusic.TagFilter(tags, true, body);
                    case "removeWithTag" -> new LyMusic.TagFilter(tags, false, body);
                    case "tag" -> new LyMusic.Tagged(tags, body);
                    default -> new LyMusic.Empty();
                };
            }
            case "crossStaff", "unfoldRepeats", "compressMMRests", "compressEmptyMeasures", "autoChange",
                 "articulate", "removeUnfolded", "inherit-acceptability", "mark_", "magnifyMusic",
                 "partCombineApart", "partCombineChords" -> {
                if (name.equals("magnifyMusic")) {
                    skipWhitespace();
                    if (peek() == '#') {
                        skipScheme();
                    }
                }
                LyMusic body = parseMusic(mode);
                return body == null ? new LyMusic.Empty() : body;
            }
            case "partCombine", "partcombine", "partCombineUp", "partCombineDown" -> {
                LyMusic first = parseMusic(mode);
                LyMusic second = parseMusic(mode);
                List<LyMusic> voices = new ArrayList<>();
                if (first != null) {
                    voices.add(new LyMusic.Seq(List.of(new LyMusic.VoiceStyle(1), first)));
                }
                if (second != null) {
                    voices.add(new LyMusic.Seq(List.of(new LyMusic.VoiceStyle(2), second)));
                }
                return new LyMusic.Sim(voices, true);
            }
            case "skip" -> {
                skipWhitespace();
                Duration d = parseDuration();
                if (d == null) {
                    LyMusic body = parseMusic(mode);
                    return body == null ? new LyMusic.Empty() : new LyMusic.Seq(List.of(
                            new LyMusic.HideNotes(true), body, new LyMusic.HideNotes(false)));
                }
                return new LyMusic.Event(LyMusic.Event.Kind.SKIP, List.of(), d.type(), d.dots(), d.length(), List.of());
            }
            case "breve", "longa", "maxima" -> {
                pos -= name.length() + 1;
                return parseEvent(LyMusic.Event.Kind.REPEAT_NOTE, List.of());
            }
            case "voiceOne" -> {
                return new LyMusic.VoiceStyle(1);
            }
            case "voiceTwo" -> {
                return new LyMusic.VoiceStyle(2);
            }
            case "voiceThree" -> {
                return new LyMusic.VoiceStyle(3);
            }
            case "voiceFour" -> {
                return new LyMusic.VoiceStyle(4);
            }
            case "oneVoice" -> {
                return new LyMusic.VoiceStyle(0);
            }
            case "stemUp" -> {
                return new LyMusic.StemDirection(true);
            }
            case "stemDown" -> {
                return new LyMusic.StemDirection(false);
            }
            case "stemNeutral" -> {
                return new LyMusic.StemDirection(null);
            }
            case "change" -> {
                skipWhitespace();
                readIdentifier();
                skipWhitespace();
                if (peek() == '=') {
                    pos++;
                    skipWhitespace();
                }
                String staff = peek() == '"' ? readString() : readIdentifier();
                return new LyMusic.ChangeStaff(staff);
            }
            case "cadenzaOn" -> {
                return new LyMusic.Cadenza(true);
            }
            case "cadenzaOff" -> {
                return new LyMusic.Cadenza(false);
            }
            case "break", "pageBreak" -> {
                return new LyMusic.Break();
            }
            case "autoBeamOff" -> {
                return new LyMusic.AutoBeam(false);
            }
            case "autoBeamOn" -> {
                return new LyMusic.AutoBeam(true);
            }
            case "hideNotes" -> {
                return new LyMusic.HideNotes(true);
            }
            case "unHideNotes" -> {
                return new LyMusic.HideNotes(false);
            }
            case "melisma" -> {
                return new LyMusic.Melisma(true);
            }
            case "melismaEnd" -> {
                return new LyMusic.Melisma(false);
            }
            case "divisioMinima", "divisioMaior", "divisioMaxima", "virgula_" -> {
                return new LyMusic.BarLine("|");
            }
            case "finalis" -> {
                return new LyMusic.BarLine("||");
            }
            case "[", "]" -> {
                return new LyMusic.Empty();
            }
            default -> {
                if (isPostEventCommand(name) || NO_ARGUMENT_COMMANDS.contains(name)) {
                    return new LyMusic.Empty();
                }
                // Unknown command: assume it takes no arguments.
                return new LyMusic.Empty();
            }
        }
    }

    private LyMusic parseContext(boolean create, Mode mode) {
        skipWhitespace();
        String type = readIdentifier();
        String name = null;
        Map<String, String> with = new LinkedHashMap<>();
        skipWhitespace();
        if (peek() == '=' ) {
            pos++;
            skipWhitespace();
            name = peek() == '"' ? readString() : readIdentifier();
            skipWhitespace();
        }
        if (peek() == '\\' && peekCommandName().equals("with")) {
            readCommandName();
            with.putAll(parseWithBlock());
        }
        Mode bodyMode = mode;
        String base = baseType(type);
        if (base.equals("ChordNames")) {
            bodyMode = Mode.CHORDS;
        } else if (base.equals("DrumStaff") || base.equals("DrumVoice")) {
            bodyMode = Mode.DRUMS;
        }
        LyMusic body;
        if (base.equals("Lyrics")) {
            skipWhitespace();
            if (peek() == '{') {
                body = parseLyricsBlock();
            } else {
                body = parseMusic(mode);
            }
        } else if (base.equals("FiguredBass")) {
            skipWhitespace();
            if (peek() == '{') {
                skipBalancedBlock();
                body = new LyMusic.Empty();
            } else {
                body = parseMusic(mode);
            }
        } else {
            body = parseMusicWithLyrics(bodyMode);
        }
        return new LyMusic.Context(type, name, create, with, body == null ? new LyMusic.Empty() : body);
    }

    private String baseType(String type) {
        String current = type;
        for (int i = 0; i < 8 && contextTypes.containsKey(current); i++) {
            current = contextTypes.get(current).base();
        }
        return current;
    }

    private LyMusic parseTempo() {
        skipWhitespace();
        String text = null;
        if (peek() == '"') {
            text = readString();
        } else if (peek() == '\\') {
            String command = peekCommandName();
            if (command.equals("markup")) {
                readCommandName();
                text = parseMarkupArgument().text();
            } else if (variables.get(command) instanceof Markup m) {
                readCommandName();
                text = m.text();
            }
        } else if (Character.isLetter(peek())) {
            text = readWordToken();
        }
        skipWhitespace();
        NoteType unit = null;
        int dots = 0;
        int perMinute = 0;
        if (Character.isDigit(peek())) {
            int save = pos;
            long number = readInt(4);
            while (peek() == '.') {
                dots++;
                pos++;
            }
            skipWhitespace();
            if (peek() == '=') {
                pos++;
                skipWhitespace();
                perMinute = (int) readInt(0);
                skipWhitespace();
                if (peek() == '-' || peek() == '~') {
                    pos++;
                    skipWhitespace();
                    readInt(0);
                }
                unit = typeForNumber((int) number);
            } else {
                pos = save;
                dots = 0;
            }
        }
        if (text == null && unit == null) {
            return new LyMusic.Empty();
        }
        return new LyMusic.Tempo(text, unit, dots, perMinute);
    }

    private LyMusic parsePropertyCommand(String command) {
        skipWhitespace();
        if (command.equals("override")) {
            String[] setting = readOverride();
            if (setting[0].endsWith("Stem.direction")) {
                return new LyMusic.StemDirection(stemDirection(setting[1]));
            }
            return new LyMusic.Empty();
        }
        if (command.equals("revert")) {
            int save = pos;
            String path = readPropertyPath();
            pos = save;
            skipPropertySetting(command);
            return path.endsWith("Stem.direction") ? new LyMusic.StemDirection(null) : new LyMusic.Empty();
        }
        String path = readPropertyPath();
        skipWhitespace();
        if (command.equals("unset")) {
            return new LyMusic.Empty();
        }
        if (peek() != '=') {
            return new LyMusic.Empty();
        }
        pos++;
        skipWhitespace();
        String value = parseValueText();
        int dot = path.lastIndexOf('.');
        String context = dot >= 0 ? path.substring(0, dot) : null;
        String property = dot >= 0 ? path.substring(dot + 1) : path;
        return new LyMusic.SetProperty(context, property, value);
    }

    /**
     * Read an {@code \override Grob.property = value}: its path and, for a
     * Scheme value, the raw value text (otherwise null).
     */
    private String[] readOverride() {
        skipWhitespace();
        String path = peek() == '#' ? skipSchemeAndReturn() : readPropertyPath();
        skipWhitespace();
        while (peek() == '#' && peek(1) == '\'') {
            path = path + "." + skipSchemeAndReturn().substring(2);
            skipWhitespace();
        }
        String value = null;
        if (peek() == '=') {
            pos++;
            skipWhitespace();
            if (peek() == '#' || peek() == '$') {
                value = skipSchemeAndReturn().trim();
            } else {
                skipValue();
            }
        }
        return new String[]{path, value};
    }

    /** {@code #UP}/{@code #DOWN}/{@code #1}/{@code #-1} as a stem direction; null for neutral. */
    static Boolean stemDirection(String value) {
        if (value == null) {
            return null;
        }
        String v = value.replace("#", "").replace("'", "").trim().toLowerCase(Locale.ROOT);
        return switch (v) {
            case "up", "1" -> Boolean.TRUE;
            case "down", "-1" -> Boolean.FALSE;
            default -> null;
        };
    }

    /** Skip an {@code \override}/{@code \set}/{@code \revert}/{@code unset} and its arguments. */
    private void skipPropertySetting(String command) {
        skipWhitespace();
        if (peek() == '#') {
            skipScheme();
        } else {
            readPropertyPath();
        }
        skipWhitespace();
        // Old syntax: \override Grob #'property = value.
        while (peek() == '#' && peek(1) == '\'') {
            skipScheme();
            skipWhitespace();
        }
        if (command.equals("revert") || command.equals("unset")) {
            return;
        }
        if (peek() == '=') {
            pos++;
            skipWhitespace();
            skipValue();
        }
    }

    /** Skip a property value: Scheme, string, markup, number, or word. */
    private void skipValue() {
        skipWhitespace();
        char c = peek();
        if (c == '#' || c == '$') {
            skipScheme();
        } else if (c == '"') {
            readString();
        } else if (c == '\\') {
            String command = readCommandName();
            if (command.equals("markup") || command.equals("markuplist")) {
                parseMarkupArgument();
            }
        } else if (c == '{') {
            skipBalancedBlock();
        } else {
            while (!atEnd() && !Character.isWhitespace(peek()) && "{}\\\"".indexOf(peek()) < 0) {
                pos++;
            }
        }
    }

    /**
     * Skip the arguments of an engraving-only command: Scheme values, strings,
     * a property path and, for {@code \tweak}/{@code \offset}, the value.
     */
    private void skipCommandArguments(String command) {
        switch (command) {
            case "tweak", "offset" -> {
                skipWhitespace();
                if (peek() == '#') {
                    skipScheme();
                } else {
                    readPropertyPath();
                }
                skipWhitespace();
                skipValue();
            }
            case "hide", "omit", "accidentalStyle" -> {
                skipWhitespace();
                if (peek() == '#') {
                    skipScheme();
                } else {
                    readPropertyPath();
                }
            }
            case "transposition" -> parseOptionalPitch();
            case "instrumentSwitch", "label", "footnote" -> {
                skipWhitespace();
                if (peek() == '"') {
                    readString();
                } else if (peek() == '#') {
                    skipScheme();
                }
            }
            case "ottava", "bendAfter", "shape", "harmonicByFret", "harmonicByRatio" -> {
                skipWhitespace();
                while (peek() == '#') {
                    skipScheme();
                    skipWhitespace();
                }
            }
            default -> {
                skipWhitespace();
                while (peek() == '#' || peek() == '$') {
                    skipScheme();
                    skipWhitespace();
                }
            }
        }
    }

    private Set<String> parseTagList() {
        skipWhitespace();
        Set<String> tags = new LinkedHashSet<>();
        if (peek() == '#') {
            String scheme = skipSchemeAndReturn();
            for (String part : scheme.replaceAll("[#'()`]", " ").trim().split("\\s+")) {
                if (!part.isEmpty()) {
                    tags.add(part);
                }
            }
        } else {
            String path = readPropertyPath();
            for (String part : path.split("[.,]")) {
                if (!part.isEmpty()) {
                    tags.add(part);
                }
            }
        }
        return tags;
    }

    /** A {@code n/d} argument as written (not reduced: {@code \time 4/4} is not 1/1). */
    private int[] parseFractionArgument() {
        skipWhitespace();
        if (!Character.isDigit(peek())) {
            return null;
        }
        long num = readInt(1);
        long den = 1;
        if (peek() == '/') {
            pos++;
            den = Math.max(1, readInt(1));
        }
        return new int[]{(int) Math.max(1, num), (int) den};
    }

    /** A pitch argument ({@code \relative c'}, {@code \key fis}), or null when none follows. */
    private LyMusic.RawPitch parseOptionalPitch() {
        skipWhitespace();
        if (!Character.isLetter(peek())) {
            return null;
        }
        int save = pos;
        String word = readNoteWord();
        LilyPondPitch.StepAlter sa = LilyPondPitch.lookup(word, language);
        if (sa == null) {
            pos = save;
            return null;
        }
        int marks = 0;
        while (peek() == '\'' || peek() == ',') {
            marks += peek() == '\'' ? 1 : -1;
            pos++;
        }
        return new LyMusic.RawPitch(sa.step(), sa.alter(), marks);
    }

    // ----- lyrics --------------------------------------------------------------------------------

    /** The argument of {@code \addlyrics}/{@code \lyricsto}/{@code \lyricmode}. */
    private LyMusic.Lyrics parseLyricsArgument() {
        skipWhitespace();
        if (peek() == '{') {
            return parseLyricsBlock();
        }
        if (peek() == '\\') {
            String command = readCommandName();
            if (command.equals("lyricmode") || command.equals("lyrics")) {
                return parseLyricsArgument();
            }
            Object value = variables.get(command);
            if (value instanceof LyMusic.Lyrics lyrics) {
                return lyrics;
            }
            if (value instanceof LyMusic music) {
                return lyricsOf(music);
            }
        }
        return new LyMusic.Lyrics(List.of());
    }

    private static LyMusic.Lyrics lyricsOf(LyMusic music) {
        if (music instanceof LyMusic.Lyrics lyrics) {
            return lyrics;
        }
        List<LyMusic.Syllable> out = new ArrayList<>();
        if (music instanceof LyMusic.Seq seq) {
            for (LyMusic item : seq.items()) {
                out.addAll(lyricsOf(item).syllables());
            }
        }
        return new LyMusic.Lyrics(out);
    }

    private LyMusic.Lyrics parseLyricsBlock() {
        List<LyMusic.Syllable> out = new ArrayList<>();
        parseLyricsInto(out);
        return new LyMusic.Lyrics(out);
    }

    private void parseLyricsInto(List<LyMusic.Syllable> out) {
        pos++; // '{'
        while (true) {
            skipWhitespace();
            if (atEnd()) {
                return;
            }
            char c = peek();
            if (c == '}') {
                pos++;
                return;
            }
            if (c == '{') {
                parseLyricsInto(out);
                continue;
            }
            if (c == '<' && peek(1) == '<') {
                pos += 2;
                continue;
            }
            if (c == '>' && peek(1) == '>') {
                pos += 2;
                continue;
            }
            if (c == '"') {
                out.add(LyMusic.Syllable.of(readString()));
                skipLyricDuration();
                continue;
            }
            if (c == '#' || c == '$') {
                skipScheme();
                continue;
            }
            if (c == '\\') {
                String command = readCommandName();
                switch (command) {
                    case "markup" -> out.add(LyMusic.Syllable.of(parseMarkupArgument().text()));
                    case "skip" -> {
                        skipWhitespace();
                        Duration d = parseDuration();
                        out.add(LyMusic.Syllable.skipped());
                        if (d == null) {
                            readWordToken();
                        }
                    }
                    case "set", "override", "unset", "revert" -> skipPropertySetting(command);
                    case "repeat" -> {
                        skipWhitespace();
                        String type = readIdentifier();
                        skipWhitespace();
                        int count = (int) Math.max(1, readInt(2));
                        skipWhitespace();
                        if (peek() == '{') {
                            List<LyMusic.Syllable> body = new ArrayList<>();
                            parseLyricsInto(body);
                            int times = type.equals("unfold") ? count : 1;
                            for (int i = 0; i < times; i++) {
                                out.addAll(body);
                            }
                        }
                    }
                    case "lyricmode", "lyrics" -> {
                        skipWhitespace();
                        if (peek() == '{') {
                            parseLyricsInto(out);
                        }
                    }
                    case "tweak", "offset", "once" -> skipCommandArguments(command);
                    default -> {
                        Object value = variables.get(command);
                        if (value instanceof LyMusic.Lyrics lyrics) {
                            out.addAll(lyrics.syllables());
                        } else if (value instanceof Markup m) {
                            out.add(LyMusic.Syllable.of(m.text()));
                        }
                    }
                }
                continue;
            }
            int start = pos;
            while (!atEnd() && !Character.isWhitespace(peek()) && peek() != '{' && peek() != '}'
                    && !(peek() == '"' && pos > start)) {
                pos++;
            }
            String word = src.substring(start, pos);
            switch (word) {
                case "--" -> {
                    if (!out.isEmpty()) {
                        out.set(out.size() - 1, out.get(out.size() - 1).withHyphen());
                    }
                }
                case "__" -> {
                    // Extender: the previous syllable lasts over a melisma.
                }
                case "_" -> out.add(LyMusic.Syllable.skipped());
                case "|" -> {
                }
                default -> {
                    String text = word.replaceAll("(?<=\\D)\\d+\\.*(\\*\\d+(/\\d+)?)?$", "");
                    if (text.isEmpty() || text.matches("\\d+\\.*")) {
                        continue;
                    }
                    text = text.replace('_', ' ');
                    if (text.contains("~")) {
                        text = text.replace("~", "‿");
                    }
                    out.add(LyMusic.Syllable.of(text));
                }
            }
        }
    }

    private void skipLyricDuration() {
        while (!atEnd() && (Character.isDigit(peek()) || peek() == '.')) {
            pos++;
        }
    }

    // ----- markup --------------------------------------------------------------------------------

    /** Markup commands that take no markup argument (only Scheme values, if anything). */
    private static final Set<String> MARKUP_TERMINALS = Set.of(
            "hspace", "vspace", "fromproperty", "filled-box", "null", "draw-line", "draw-hline",
            "draw-circle", "draw-dashed-line", "draw-dotted-line", "epsfile", "musicglyph", "note",
            "note-by-number", "rest", "rest-by-number", "char", "page-ref", "page-link", "strut",
            "fret-diagram", "fret-diagram-terse", "fret-diagram-verbose", "harp-pedal", "woodwind-diagram",
            "doubleflat", "flat", "natural", "sharp", "doublesharp", "semiflat", "semisharp", "sesquiflat",
            "sesquisharp", "triangle", "arrow-head", "beam", "path", "postscript", "stencil", "verbatim-file",
            "eyeglasses", "left-brace", "right-brace", "segno", "coda", "varcoda", "fermata", "tie", "undertie",
            "overtie", "parenthesize_", "tied-lyric", "first-visible", "wordwrap-field", "justify-field",
            "auto-footnote", "with-link", "lookup", "fraction_", "polygon", "ellipse", "oval_", "general-align_");

    /** Read the markup argument after {@code \markup} and reduce it to text. */
    private Markup parseMarkupArgument() {
        MarkupBuilder b = new MarkupBuilder();
        parseMarkup(b, 0);
        return new Markup(b.text.toString().replaceAll("\\s+", " ").strip(), b.bold, b.italic);
    }

    private static final class MarkupBuilder {
        final StringBuilder text = new StringBuilder();
        boolean bold;
        boolean italic;

        void add(String s) {
            if (s == null || s.isEmpty()) {
                return;
            }
            if (text.length() > 0 && text.charAt(text.length() - 1) != ' ') {
                text.append(' ');
            }
            text.append(s);
        }
    }

    private void parseMarkup(MarkupBuilder b, int depth) {
        skipWhitespace();
        if (atEnd() || depth > 64) {
            return;
        }
        char c = peek();
        if (c == '{') {
            pos++;
            while (true) {
                skipWhitespace();
                if (atEnd()) {
                    return;
                }
                if (peek() == '}') {
                    pos++;
                    return;
                }
                int before = pos;
                parseMarkup(b, depth + 1);
                if (pos == before) {
                    pos++;
                }
            }
        }
        if (c == '"') {
            b.add(readString());
            return;
        }
        if (c == '#' || c == '$') {
            String scheme = skipSchemeAndReturn().trim();
            if (scheme.startsWith("#\"") && scheme.endsWith("\"")) {
                b.add(scheme.substring(2, scheme.length() - 1));
            }
            return;
        }
        if (c == '\\') {
            String command = readCommandName();
            Object value = variables.get(command);
            if (value instanceof Markup m) {
                b.add(m.text());
                b.bold |= m.bold();
                b.italic |= m.italic();
                return;
            }
            switch (command) {
                case "bold" -> b.bold = true;
                case "italic" -> b.italic = true;
                case "flat" -> {
                    b.text.append('♭');
                    return;
                }
                case "sharp" -> {
                    b.text.append('♯');
                    return;
                }
                case "natural" -> {
                    b.text.append('♮');
                    return;
                }
                case "score", "score-lines" -> {
                    skipWhitespace();
                    if (peek() == '{') {
                        skipBalancedBlock();
                    }
                    return;
                }
                case "markup", "markuplist" -> {
                    parseMarkup(b, depth + 1);
                    return;
                }
                default -> {
                }
            }
            // Scheme / string arguments first.
            skipWhitespace();
            while (peek() == '#' || peek() == '$') {
                skipScheme();
                skipWhitespace();
            }
            if (command.equals("with-url") || command.equals("harp-pedal") || command.equals("with-link")
                    || command.equals("fromproperty")) {
                if (peek() == '"') {
                    readString();
                }
                if (!command.equals("with-url")) {
                    return;
                }
                skipWhitespace();
            }
            if (MARKUP_TERMINALS.contains(command)) {
                return;
            }
            parseMarkup(b, depth + 1);
            if (command.equals("combine") || command.equals("overlay_") || command.equals("fraction")) {
                parseMarkup(b, depth + 1);
            }
            return;
        }
        if (c == '}') {
            return;
        }
        int start = pos;
        while (!atEnd() && !Character.isWhitespace(peek()) && "{}\\\"#".indexOf(peek()) < 0) {
            pos++;
        }
        b.add(src.substring(start, pos));
    }

    // ----- lexical helpers -----------------------------------------------------------------------

    private void setLanguage(String name) {
        String lang = LilyPondPitch.language(name);
        if (lang != null) {
            language = lang;
        }
    }

    private boolean atEnd() {
        return pos >= src.length();
    }

    private char peek() {
        return pos < src.length() ? src.charAt(pos) : '\0';
    }

    private char peek(int offset) {
        int i = pos + offset;
        return i < src.length() ? src.charAt(i) : '\0';
    }

    private void skipWhitespace() {
        while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) {
            pos++;
        }
    }

    private void skipToLineEnd() {
        while (!atEnd() && peek() != '\n') {
            pos++;
        }
    }

    /** Read {@code "..."} (at the opening quote), unescaping. */
    private String readString() {
        pos++;
        StringBuilder sb = new StringBuilder();
        while (!atEnd()) {
            char c = peek();
            if (c == '\\' && pos + 1 < src.length()) {
                char next = src.charAt(pos + 1);
                sb.append(next == 'n' ? '\n' : next);
                pos += 2;
                continue;
            }
            pos++;
            if (c == '"') {
                break;
            }
            sb.append(c);
        }
        return sb.toString();
    }

    /** The name of the command at {@code \}, without consuming it. */
    private String peekCommandName() {
        int save = pos;
        String name = readCommandName();
        pos = save;
        return name;
    }

    /**
     * Read {@code \name} and return {@code name}. Names are letters, with
     * {@code -} and {@code _} allowed between letters.
     */
    private String readCommandName() {
        pos++; // '\'
        int start = pos;
        while (!atEnd()) {
            char c = peek();
            if (Character.isLetter(c)) {
                pos++;
            } else if ((c == '-' || c == '_') && pos > start && Character.isLetter(peek(1))) {
                pos++;
            } else {
                break;
            }
        }
        return src.substring(start, pos);
    }

    /** An identifier: letters, with {@code -}/{@code _} between letters, and digits after the first letter. */
    private String readIdentifier() {
        int start = pos;
        while (!atEnd()) {
            char c = peek();
            if (Character.isLetter(c) || (Character.isDigit(c) && pos > start)) {
                pos++;
            } else if ((c == '-' || c == '_') && pos > start && Character.isLetter(peek(1))) {
                pos++;
            } else {
                break;
            }
        }
        return src.substring(start, pos);
    }

    /** A note-name word: letters only, plus English {@code -sharp}/{@code -flat} spellings. */
    private String readNoteWord() {
        int start = pos;
        while (!atEnd() && Character.isLetter(peek())) {
            pos++;
        }
        while (peek() == '-' && (src.startsWith("sharp", pos + 1) || src.startsWith("flat", pos + 1))) {
            pos++;
            while (!atEnd() && Character.isLetter(peek())) {
                pos++;
            }
        }
        return src.substring(start, pos);
    }

    /** A dotted property path such as {@code Staff.TimeSignature.color}. */
    private String readPropertyPath() {
        int start = pos;
        while (!atEnd()) {
            char c = peek();
            if (Character.isLetterOrDigit(c) || c == '.' || c == '-' || c == '_') {
                pos++;
            } else {
                break;
            }
        }
        return src.substring(start, pos);
    }

    /** A whitespace-delimited word (clef names like {@code treble_8}). */
    private String readWordToken() {
        int start = pos;
        while (!atEnd() && !Character.isWhitespace(peek()) && "{}\\\"<>|".indexOf(peek()) < 0) {
            pos++;
        }
        return src.substring(start, pos);
    }

    private void readNumberToken() {
        while (!atEnd() && (Character.isDigit(peek()) || peek() == '.' || peek() == '-' || peek() == '\\'
                || Character.isLetter(peek()))) {
            if (peek() == '\\') {
                readCommandName();
            } else {
                pos++;
            }
        }
    }

    private long readInt(long fallback) {
        int start = pos;
        while (!atEnd() && Character.isDigit(peek()) && pos - start < 9) {
            pos++;
        }
        return pos == start ? fallback : Long.parseLong(src.substring(start, pos));
    }

    /** Skip one token of a block we only scan: a string, Scheme value, command, word or character. */
    private void skipToken() {
        char c = peek();
        if (c == '"') {
            readString();
        } else if (c == '#' || c == '$') {
            skipScheme();
        } else if (c == '\\') {
            readCommandName();
            if (pos < src.length() && peek() == '\\') {
                return;
            }
        } else if (c == '{') {
            skipBalancedBlock();
        } else {
            pos++;
        }
    }

    /** Skip a {@code { ... }} block, honouring strings and Scheme. */
    private void skipBalancedBlock() {
        skipWhitespace();
        if (peek() != '{') {
            return;
        }
        int depth = 0;
        while (!atEnd()) {
            char c = peek();
            if (c == '"') {
                readString();
                continue;
            }
            if (c == '#' || c == '$') {
                skipScheme();
                continue;
            }
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    pos++;
                    return;
                }
            }
            pos++;
        }
    }

    private String skipSchemeAndReturn() {
        int start = pos;
        skipScheme();
        return src.substring(start, pos);
    }

    /** Skip a Scheme expression introduced by {@code #} or {@code $}. */
    private void skipScheme() {
        pos++; // '#' or '$'
        if (peek() == '{') {
            // #{ lilypond #}
            int end = src.indexOf("#}", pos);
            pos = end < 0 ? src.length() : end + 2;
            return;
        }
        skipSchemeDatum();
    }

    private void skipSchemeDatum() {
        while (!atEnd() && (peek() == '\'' || peek() == '`' || peek() == ',' || peek() == '@')) {
            pos++;
        }
        if (atEnd()) {
            return;
        }
        char c = peek();
        if (c == '(') {
            int depth = 0;
            while (!atEnd()) {
                char d = peek();
                if (d == '"') {
                    readString();
                    continue;
                }
                if (d == ';') {
                    skipToLineEnd();
                    continue;
                }
                if (d == '#' && peek(1) == '\\') {
                    pos += 3;
                    continue;
                }
                if (d == '(') {
                    depth++;
                } else if (d == ')') {
                    depth--;
                    if (depth == 0) {
                        pos++;
                        return;
                    }
                }
                pos++;
            }
            return;
        }
        if (c == '"') {
            readString();
            return;
        }
        if (c == '#') {
            // ##t, ##f, #\a
            pos++;
            if (peek() == '\\') {
                pos += 2;
                return;
            }
            while (!atEnd() && Character.isLetterOrDigit(peek())) {
                pos++;
            }
            return;
        }
        while (!atEnd() && !Character.isWhitespace(peek()) && "{}()\"\\".indexOf(peek()) < 0) {
            pos++;
        }
    }

    // ----- durations -----------------------------------------------------------------------------

    static NoteType typeForNumber(int n) {
        return switch (n) {
            case 0, 1 -> NoteType.WHOLE;
            case 2 -> NoteType.HALF;
            case 4 -> NoteType.QUARTER;
            case 8 -> NoteType.EIGHTH;
            case 16 -> NoteType.SIXTEENTH;
            case 32 -> NoteType.THIRTY_SECOND;
            case 64 -> NoteType.SIXTY_FOURTH;
            case 128 -> NoteType.HUNDRED_TWENTY_EIGHTH;
            default -> NoteType.QUARTER;
        };
    }

    /** Length in quarter notes of a note type with dots. */
    static Fraction lengthOf(NoteType type, int dots) {
        Fraction base = switch (type) {
            case MAXIMA -> Fraction.of(32);
            case LONG -> Fraction.of(16);
            case BREVE -> Fraction.of(8);
            case WHOLE -> Fraction.of(4);
            case HALF -> Fraction.of(2);
            case QUARTER -> Fraction.of(1);
            case EIGHTH -> Fraction.of(1, 2);
            case SIXTEENTH -> Fraction.of(1, 4);
            case THIRTY_SECOND -> Fraction.of(1, 8);
            case SIXTY_FOURTH -> Fraction.of(1, 16);
            case HUNDRED_TWENTY_EIGHTH -> Fraction.of(1, 32);
        };
        Fraction total = base;
        Fraction add = base;
        for (int i = 0; i < dots; i++) {
            add = Fraction.of(add.numerator(), add.denominator() * 2);
            total = total.plus(add);
        }
        return total;
    }

    static Fraction multiply(Fraction a, Fraction b) {
        return Fraction.of(Math.multiplyExact(a.numerator(), b.numerator()),
                Math.multiplyExact(a.denominator(), b.denominator()));
    }
}
