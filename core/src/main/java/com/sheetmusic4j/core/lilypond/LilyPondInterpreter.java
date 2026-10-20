package com.sheetmusic4j.core.lilypond;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import com.sheetmusic4j.core.model.Articulation;
import com.sheetmusic4j.core.model.Direction;
import com.sheetmusic4j.core.model.DirectionType;
import com.sheetmusic4j.core.model.DynamicMark;
import com.sheetmusic4j.core.model.Fraction;
import com.sheetmusic4j.core.model.Harmony;
import com.sheetmusic4j.core.model.KeySignature;
import com.sheetmusic4j.core.model.Lyric;
import com.sheetmusic4j.core.model.NoteType;
import com.sheetmusic4j.core.model.Pitch;
import com.sheetmusic4j.core.model.Placement;
import com.sheetmusic4j.core.model.Step;
import com.sheetmusic4j.core.model.Syllabic;
import com.sheetmusic4j.core.model.TimeSignature;
import com.sheetmusic4j.core.model.Tuplet;

/**
 * Gives a {@link LyMusic} tree its musical meaning, the way LilyPond's
 * iterators do: it tracks time, creates the context tree (score, staff groups,
 * staves, voices, lyrics, chord names), resolves relative octaves and
 * transpositions, and records every note and rest as a timed {@link Ev} in its
 * voice. {@link LilyPondScoreAssembler} turns the result into measures.
 */
final class LilyPondInterpreter {

    /** The kind of a context, after resolving aliases and ancient-notation variants. */
    enum CtxKind {
        SCORE, GROUP, PIANO_STAFF, STAFF, VOICE, LYRICS, CHORD_NAMES, DYNAMICS, IGNORED
    }

    /** A LilyPond context: a node in the score's context tree. */
    static final class Ctx {
        final CtxKind kind;
        final String type;
        final String name;
        final Ctx parent;
        final List<Ctx> children = new ArrayList<>();
        String instrumentName;
        String shortInstrumentName;
        /** Group delimiter override from a {@code \layout} context definition. */
        String delimiter;

        // Staff state.
        final TreeMap<Fraction, String> clefs = new TreeMap<>();
        final TreeMap<Fraction, KeySignature> keys = new TreeMap<>();
        Ctx defaultVoice;

        // Group / score state.
        Ctx defaultStaff;

        // Voice state.
        final List<Ev> events = new ArrayList<>();
        Ctx staff;
        Boolean styleStemUp;
        boolean hasStemOverride;
        Boolean stemOverride;
        boolean autoBeam = true;
        boolean hideTupletNumber;
        boolean hideTupletBracket;
        boolean hidden;
        boolean manualMelisma;
        int slurDepth;
        boolean beamOpen;
        Ev pendingTie;
        final List<Pitch> pendingGrace = new ArrayList<>();
        int verses;

        // Chord names / dynamics / lyrics state.
        final List<Timed<Harmony>> harmonies = new ArrayList<>();
        String associatedVoice;

        Ctx(CtxKind kind, String type, String name, Ctx parent) {
            this.kind = kind;
            this.type = type;
            this.name = name;
            this.parent = parent;
            if (parent != null) {
                parent.children.add(this);
            }
        }

        boolean isStaff() {
            return kind == CtxKind.STAFF;
        }
    }

    /** Something at a point in time. */
    record Timed<T>(Fraction time, T value) {
    }

    /** A direction at a point in time, coming from a non-staff context (or the score). */
    record TimedDirection(Fraction time, Direction direction, Ctx context) {
    }

    /** A {@code \repeat volta}: its body and the spans of its alternatives. */
    record RepeatSpan(Fraction start, Fraction end, int count, List<Fraction[]> alternatives) {
    }

    /** A note, chord or rest in a voice. */
    static final class Ev {
        enum Kind {
            NOTE, REST, MEASURE_REST, HIDDEN
        }

        Kind kind;
        Fraction start;
        Fraction length;
        NoteType type;
        int dots;
        /** When set, the written type does not apply (tremolo) and is derived from the length. */
        boolean retype;
        final List<Pitch> pitches = new ArrayList<>();
        final List<Boolean> forcedAccidental = new ArrayList<>();
        boolean[] tieStart = new boolean[0];
        boolean[] tieStop = new boolean[0];
        int actualNotes = 1;
        int normalNotes = 1;
        final List<Tuplet> tuplets = new ArrayList<>();
        Ctx voice;
        Ctx staff;
        Boolean stemUp;
        boolean autoBeam;
        final List<Articulation> articulations = new ArrayList<>();
        final List<Direction> directions = new ArrayList<>();
        int slurStarts;
        int slurStops;
        boolean beamStart;
        boolean beamEnd;
        final List<Pitch> grace = new ArrayList<>();
        /** In a melisma: gets no lyric syllable of its own. */
        boolean melisma;
        final List<Lyric> lyrics = new ArrayList<>();

        Fraction end() {
            return start.plus(length);
        }

        boolean isNote() {
            return kind == Kind.NOTE;
        }

        /**
         * Take over another event's pitches (making this a chord) and
         * markings; with {@code skipDuplicates}, pitches this event already
         * has are not added twice.
         */
        void absorb(Ev other, boolean skipDuplicates) {
            for (int i = 0; i < other.pitches.size(); i++) {
                int midi = other.pitches.get(i).toMidiNumber();
                int existing = -1;
                for (int j = 0; j < pitches.size(); j++) {
                    if (pitches.get(j).toMidiNumber() == midi) {
                        existing = j;
                    }
                }
                boolean otherTieStart = i < other.tieStart.length && other.tieStart[i];
                boolean otherTieStop = i < other.tieStop.length && other.tieStop[i];
                if (skipDuplicates && existing >= 0) {
                    tieStart[existing] |= otherTieStart;
                    tieStop[existing] |= otherTieStop;
                    continue;
                }
                pitches.add(other.pitches.get(i));
                forcedAccidental.add(i < other.forcedAccidental.size() && other.forcedAccidental.get(i));
                tieStart = java.util.Arrays.copyOf(tieStart, pitches.size());
                tieStop = java.util.Arrays.copyOf(tieStop, pitches.size());
                tieStart[pitches.size() - 1] = otherTieStart;
                tieStop[pitches.size() - 1] = otherTieStop;
            }
            for (Articulation a : other.articulations) {
                if (!articulations.contains(a)) {
                    articulations.add(a);
                }
            }
            for (Direction d : other.directions) {
                if (!directions.contains(d)) {
                    directions.add(d);
                }
            }
        }
    }

    /** Timeline facts that apply to the whole score. */
    static final class Global {
        final TreeMap<Fraction, TimeSignature> times = new TreeMap<>();
        final TreeMap<Fraction, Fraction> partials = new TreeMap<>();
        final TreeMap<Fraction, Boolean> cadenza = new TreeMap<>();
        final TreeMap<Fraction, String> bars = new TreeMap<>();
        final TreeMap<Fraction, KeySignature> keys = new TreeMap<>();
        final List<Fraction> breaks = new ArrayList<>();
        final List<TimedDirection> directions = new ArrayList<>();
        final List<RepeatSpan> repeats = new ArrayList<>();
        Integer firstBarNumber;
        boolean timeSignaturesHidden;
        Fraction end = Fraction.ZERO;
    }

    /** The interpreted score. */
    record Result(Ctx root, Global global, Map<String, LilyPondParser.ContextType> contextTypes) {
    }

    // ----- walk state ----------------------------------------------------------------------------

    /** An open tuplet bracket. */
    private static final class TupletMark {
        final int actual;
        final int normal;
        final Fraction span;
        final int number;
        Fraction accumulated = Fraction.ZERO;
        boolean startPending = true;
        Ev last;

        TupletMark(int actual, int normal, Fraction span, int number) {
            this.actual = actual;
            this.normal = normal;
            this.span = span;
            this.number = number;
        }
    }

    /** Lexically scoped state: inherited by nested music, not shared back. */
    private record Frame(Ctx ctx, Fraction scale, boolean retype, boolean grace, List<TupletMark> tuplets,
                         int transposeSteps, int transposeSemitones, List<Set<String>> keepTags,
                         List<Set<String>> removeTags) {

        Frame withCtx(Ctx c) {
            return new Frame(c, scale, retype, grace, tuplets, transposeSteps, transposeSemitones, keepTags,
                    removeTags);
        }
    }

    /** Pitch resolution mode: relative (previous pitch) or absolute with an octave offset. */
    private static final class PitchMode {
        boolean relative;
        Pitch previous = new Pitch(Step.F, 3);
        int octaveOffset;

        PitchMode copy() {
            PitchMode m = new PitchMode();
            m.relative = relative;
            m.previous = previous;
            m.octaveOffset = octaveOffset;
            return m;
        }
    }

    private record LyricRequest(String voiceName, Ctx voice, LyMusic.Lyrics lyrics) {
    }

    private final Map<String, LilyPondParser.ContextType> contextTypes;
    private final Ctx root;
    private final Global global = new Global();
    private PitchMode pitchMode = new PitchMode();
    private List<Pitch> lastPitches = List.of(new Pitch(Step.C, 4));
    private List<Pitch> lastChord = List.of(new Pitch(Step.C, 4));
    private final List<Ctx> allContexts = new ArrayList<>();
    private final List<LyricRequest> lyricRequests = new ArrayList<>();
    private final Deque<Ctx[]> voiceTrackers = new ArrayDeque<>();
    private final Map<LyMusic.Context, Ctx> precreated = new java.util.IdentityHashMap<>();
    private final List<Ctx[]> partCombines = new ArrayList<>();
    private boolean tempoHideNote;
    private Ctx lastVoice;
    private int rehearsalCounter;
    private int depth;

    private LilyPondInterpreter(Map<String, LilyPondParser.ContextType> contextTypes) {
        this.contextTypes = contextTypes;
        this.root = new Ctx(CtxKind.SCORE, "Score", null, null);
        allContexts.add(root);
    }

    static Result interpret(LyMusic music, Map<String, LilyPondParser.ContextType> contextTypes) {
        LilyPondInterpreter interpreter = new LilyPondInterpreter(contextTypes);
        Frame frame = new Frame(interpreter.root, Fraction.of(1), false, false, List.of(), 0, 0, List.of(),
                List.of());
        Fraction end = interpreter.walk(music, Fraction.ZERO, frame);
        interpreter.global.end = Fraction.max(interpreter.global.end, end);
        interpreter.combineParts();
        interpreter.applyLyrics();
        // \global-style variables repeat tempo and marks in every voice: keep one.
        java.util.Set<Object> seen = new java.util.HashSet<>();
        interpreter.global.directions.removeIf(td -> td.context() == null
                && !seen.add(List.of(td.time(), td.direction())));
        return new Result(interpreter.root, interpreter.global, contextTypes);
    }

    // ----- walking -------------------------------------------------------------------------------

    private Fraction walk(LyMusic music, Fraction t, Frame f) {
        if (depth > 200) {
            return t;
        }
        depth++;
        try {
            return walkNode(music, t, f);
        } finally {
            depth--;
        }
    }

    private Fraction walkNode(LyMusic music, Fraction t, Frame f) {
        switch (music) {
            case LyMusic.Seq seq -> {
                Fraction cursor = t;
                for (LyMusic item : seq.items()) {
                    cursor = walk(item, cursor, f);
                }
                return cursor;
            }
            case LyMusic.Sim sim -> {
                return walkSimultaneous(sim, t, f);
            }
            case LyMusic.Context context -> {
                return walkContext(context, t, f);
            }
            case LyMusic.Relative relative -> {
                PitchMode saved = pitchMode;
                pitchMode = new PitchMode();
                pitchMode.relative = true;
                pitchMode.previous = relative.reference() == null ? new Pitch(Step.F, 3)
                        : new Pitch(relative.reference().step(), 3 + relative.reference().octaveMarks(),
                        relative.reference().alter());
                Fraction end = walk(relative.body(), t, f);
                pitchMode = saved;
                return end;
            }
            case LyMusic.Fixed fixed -> {
                PitchMode saved = pitchMode;
                pitchMode = new PitchMode();
                pitchMode.octaveOffset = fixed.reference() == null ? 0 : fixed.reference().octaveMarks();
                Fraction end = walk(fixed.body(), t, f);
                pitchMode = saved;
                return end;
            }
            case LyMusic.Transpose transpose -> {
                int steps = diatonic(transpose.to()) - diatonic(transpose.from());
                int semis = chromatic(transpose.to()) - chromatic(transpose.from());
                Frame inner = new Frame(f.ctx(), f.scale(), f.retype(), f.grace(), f.tuplets(),
                        f.transposeSteps() + steps, f.transposeSemitones() + semis, f.keepTags(), f.removeTags());
                return walk(transpose.body(), t, inner);
            }
            case LyMusic.Tuplet tuplet -> {
                return walkTuplet(tuplet, t, f);
            }
            case LyMusic.Scale scale -> {
                Frame inner = new Frame(f.ctx(), f.scale().times(scale.factor()), f.retype(),
                        f.grace(), f.tuplets(), f.transposeSteps(), f.transposeSemitones(), f.keepTags(),
                        f.removeTags());
                return walk(scale.body(), t, inner);
            }
            case LyMusic.Repeat repeat -> {
                return walkRepeat(repeat, t, f);
            }
            case LyMusic.PartCombine combine -> {
                Ctx staff = staffFor(f, true);
                Ctx[] voices = new Ctx[2];
                for (int i = 0; i < 2; i++) {
                    String name = i == 0 ? "one" : "two";
                    Ctx voice = findChild(staff, CtxKind.VOICE, name);
                    if (voice == null) {
                        voice = newContext(CtxKind.VOICE, "Voice", name, staff);
                        voice.staff = staff;
                    }
                    voice.styleStemUp = i == 0;
                    voices[i] = voice;
                }
                partCombines.add(voices);
                Fraction first = walk(combine.first(), t, f.withCtx(voices[0]));
                Fraction second = walk(combine.second(), t, f.withCtx(voices[1]));
                return Fraction.max(first, second);
            }
            case LyMusic.Grace grace -> {
                Frame inner = new Frame(f.ctx(), f.scale(), f.retype(), true, f.tuplets(), f.transposeSteps(),
                        f.transposeSemitones(), f.keepTags(), f.removeTags());
                walk(grace.body(), t, inner);
                return t;
            }
            case LyMusic.TagFilter filter -> {
                List<Set<String>> keep = new ArrayList<>(f.keepTags());
                List<Set<String>> remove = new ArrayList<>(f.removeTags());
                (filter.keep() ? keep : remove).add(filter.tags());
                Frame inner = new Frame(f.ctx(), f.scale(), f.retype(), f.grace(), f.tuplets(), f.transposeSteps(),
                        f.transposeSemitones(), keep, remove);
                return walk(filter.body(), t, inner);
            }
            case LyMusic.Tagged tagged -> {
                for (Set<String> keep : f.keepTags()) {
                    if (keep.stream().noneMatch(tagged.tags()::contains)) {
                        return t;
                    }
                }
                for (Set<String> remove : f.removeTags()) {
                    if (remove.stream().anyMatch(tagged.tags()::contains)) {
                        return t;
                    }
                }
                return walk(tagged.body(), t, f);
            }
            case LyMusic.AddLyrics addLyrics -> {
                Ctx[] tracker = new Ctx[1];
                voiceTrackers.push(tracker);
                Fraction end = walk(addLyrics.music(), t, f);
                voiceTrackers.pop();
                Ctx voice = tracker[0] != null ? tracker[0] : lastVoice;
                for (LyMusic.Lyrics lyrics : addLyrics.lyrics()) {
                    lyricRequests.add(new LyricRequest(null, voice, lyrics));
                }
                return end;
            }
            case LyMusic.LyricsTo lyricsTo -> {
                lyricRequests.add(new LyricRequest(lyricsTo.voice(), null, lyricsTo.lyrics()));
                return t;
            }
            case LyMusic.Lyrics lyrics -> {
                Ctx lyricsCtx = f.ctx().kind == CtxKind.LYRICS ? f.ctx() : null;
                String voiceName = lyricsCtx == null ? null : lyricsCtx.associatedVoice;
                lyricRequests.add(new LyricRequest(voiceName, voiceName == null ? lastVoice : null, lyrics));
                return t;
            }
            case LyMusic.ChordSymbol chord -> {
                Fraction length = chord.length().times(f.scale());
                if (chord.harmony() != null && !f.grace() && f.ctx().kind != CtxKind.IGNORED) {
                    Ctx target = f.ctx().kind == CtxKind.CHORD_NAMES ? f.ctx() : chordNamesFor(f);
                    target.harmonies.add(new Timed<>(t, chord.harmony()));
                }
                global.end = Fraction.max(global.end, t.plus(length));
                return t.plus(length);
            }
            case LyMusic.Event event -> {
                return walkEvent(event, t, f);
            }
            default -> {
                command(music, t, f);
                return t;
            }
        }
    }

    private Fraction walkSimultaneous(LyMusic.Sim sim, Fraction t, Frame f) {
        Fraction end = t;
        if (!sim.voices()) {
            // Staves exist from the start of the simultaneous music, in the
            // order written, so a voice can \change to a staff written after it.
            // Groups and other contexts are created up front too, to keep the written order.
            for (LyMusic item : sim.items()) {
                if (!(item instanceof LyMusic.Context c)) {
                    continue;
                }
                CtxKind kind = kindOf(resolveType(c.type()));
                if (kind != CtxKind.VOICE && kind != CtxKind.SCORE
                        && (c.create() || (c.name() != null && findByName(kind, c.name()) == null))) {
                    precreated.put(c, newContext(kind, c.type(), c.name(), parentFor(kind, f)));
                }
            }
            for (LyMusic item : sim.items()) {
                end = Fraction.max(end, walk(item, t, f));
            }
            return end;
        }
        // << a \\ b >>: each part in its own voice ("1", "2", ...) of the current staff.
        Ctx staff = staffFor(f, true);
        for (int i = 0; i < sim.items().size(); i++) {
            String voiceName = String.valueOf(i + 1);
            Ctx voice = findChild(staff, CtxKind.VOICE, voiceName);
            if (voice == null) {
                voice = newContext(CtxKind.VOICE, "Voice", voiceName, staff);
                voice.staff = staff;
            }
            voice.styleStemUp = (i % 2) == 0;
            if (i == 0 && sim.items().size() == 1) {
                voice.styleStemUp = null;
            }
            end = Fraction.max(end, walk(sim.items().get(i), t, f.withCtx(voice)));
        }
        return end;
    }

    private Fraction walkContext(LyMusic.Context context, Fraction t, Frame f) {
        String declared = context.type();
        String base = resolveType(declared);
        CtxKind kind = kindOf(base);
        Ctx ctx = precreated.remove(context);
        boolean fresh = ctx != null;
        if (ctx == null && !context.create()) {
            ctx = findExisting(kind, context.name(), f.ctx());
        }
        if (ctx == null) {
            ctx = newContext(kind, declared, context.name(), parentFor(kind, f));
            fresh = true;
        }
        if (fresh) {
            Ctx parent = ctx.parent;
            LilyPondParser.ContextType alias = contextTypes.get(declared);
            if (alias != null && alias.delimiter() != null) {
                ctx.delimiter = alias.delimiter();
            }
            if (kind == CtxKind.VOICE) {
                ctx.staff = nearest(parent, CtxKind.STAFF);
            }
            if (isUnmetered(declared) && !global.cadenza.containsKey(Fraction.ZERO)) {
                global.cadenza.put(Fraction.ZERO, true);
            }
            if (kind == CtxKind.STAFF && (base.equals("DrumStaff") || base.equals("RhythmicStaff"))) {
                ctx.clefs.put(Fraction.ZERO, "percussion");
            }
        }
        for (Map.Entry<String, String> e : context.with().entrySet()) {
            applyProperty(ctx, e.getKey(), e.getValue(), t);
        }
        return walk(context.body(), t, f.withCtx(ctx));
    }

    private Fraction walkTuplet(LyMusic.Tuplet tuplet, Fraction t, Frame f) {
        Fraction factor = Fraction.of(tuplet.normal(), tuplet.actual());
        List<TupletMark> marks = new ArrayList<>(f.tuplets());
        TupletMark mark = new TupletMark(tuplet.actual(), tuplet.normal(), tuplet.span(), marks.size() + 1);
        marks.add(mark);
        Frame inner = new Frame(f.ctx(), f.scale().times(factor), f.retype(), f.grace(),
                List.copyOf(marks), f.transposeSteps(), f.transposeSemitones(), f.keepTags(), f.removeTags());
        Fraction end = walk(tuplet.body(), t, inner);
        if (!mark.startPending && mark.last != null) {
            mark.last.tuplets.add(new Tuplet(mark.number, Tuplet.Type.STOP, true));
        }
        return end;
    }

    private Fraction walkRepeat(LyMusic.Repeat repeat, Fraction t, Frame f) {
        String type = repeat.type();
        int count = Math.max(1, repeat.count());
        if (type.equals("tremolo")) {
            // Each note of the tremolo is shown `count` times as long as written.
            Frame inner = new Frame(f.ctx(), f.scale().times(count), true, f.grace(), f.tuplets(),
                    f.transposeSteps(), f.transposeSemitones(), f.keepTags(), f.removeTags());
            return walk(repeat.body(), t, inner);
        }
        if (type.equals("unfold") || type.equals("percent")) {
            Fraction cursor = t;
            List<LyMusic> alts = repeat.alternatives();
            for (int i = 0; i < count; i++) {
                cursor = walk(repeat.body(), cursor, f);
                if (!alts.isEmpty()) {
                    int altIndex = Math.max(0, alts.size() - (count - i));
                    cursor = walk(alts.get(Math.min(altIndex, alts.size() - 1)), cursor, f);
                }
            }
            return cursor;
        }
        Fraction bodyEnd = walk(repeat.body(), t, f);
        List<Fraction[]> spans = new ArrayList<>();
        Fraction cursor = bodyEnd;
        for (LyMusic alt : repeat.alternatives()) {
            Fraction altEnd = walk(alt, cursor, f);
            spans.add(new Fraction[]{cursor, altEnd});
            cursor = altEnd;
        }
        global.repeats.add(new RepeatSpan(t, bodyEnd, count, spans));
        return cursor;
    }

    // ----- events --------------------------------------------------------------------------------

    private Fraction walkEvent(LyMusic.Event e, Fraction t, Frame f) {
        Fraction length = e.length().times(f.scale());
        Fraction end = t.plus(length);
        Ctx target = eventTarget(f);
        if (target == null) {
            return end;
        }
        if (f.grace()) {
            if (target.kind == CtxKind.VOICE && e.kind() != LyMusic.Event.Kind.SKIP
                    && e.kind() != LyMusic.Event.Kind.REST) {
                target.pendingGrace.addAll(resolvePitches(e, f));
            }
            return t;
        }
        global.end = Fraction.max(global.end, end);
        if (target.kind != CtxKind.VOICE) {
            // Dynamics / mark lines: only their directions matter.
            for (LyMusic.Post p : e.post()) {
                Direction d = directionFor(p);
                if (d != null) {
                    global.directions.add(new TimedDirection(t, d, target));
                }
            }
            return end;
        }
        Ctx voice = target;
        touch(voice);
        if (e.kind() == LyMusic.Event.Kind.SKIP) {
            for (LyMusic.Post p : e.post()) {
                Direction d = directionFor(p);
                if (d != null) {
                    global.directions.add(new TimedDirection(t, d, voice.staff));
                }
            }
            return end;
        }
        Ev ev = new Ev();
        ev.start = t;
        ev.length = length;
        ev.type = e.type();
        ev.dots = e.dots();
        ev.retype = f.retype();
        ev.voice = voice;
        ev.staff = voice.staff;
        ev.autoBeam = voice.autoBeam;
        ev.stemUp = stemFor(voice);
        switch (e.kind()) {
            case REST -> ev.kind = Ev.Kind.REST;
            case MEASURE_REST -> ev.kind = Ev.Kind.MEASURE_REST;
            default -> {
                ev.kind = voice.hidden ? Ev.Kind.HIDDEN : Ev.Kind.NOTE;
                List<Pitch> pitches = resolvePitches(e, f);
                ev.pitches.addAll(pitches);
                for (int i = 0; i < pitches.size(); i++) {
                    boolean forced = e.kind() == LyMusic.Event.Kind.NOTE && i < e.pitches().size()
                            && e.pitches().get(i).forcedAccidental();
                    ev.forcedAccidental.add(forced);
                }
            }
        }
        ev.tieStart = new boolean[ev.pitches.size()];
        ev.tieStop = new boolean[ev.pitches.size()];

        // Ties into this event.
        if (voice.pendingTie != null) {
            Ev previous = voice.pendingTie;
            if (ev.isNote()) {
                for (int i = 0; i < ev.pitches.size(); i++) {
                    int midi = ev.pitches.get(i).toMidiNumber();
                    for (int j = 0; j < previous.pitches.size(); j++) {
                        if (previous.tieStart[j] && previous.pitches.get(j).toMidiNumber() == midi) {
                            ev.tieStop[i] = true;
                        }
                    }
                }
            }
            for (int j = 0; j < previous.pitches.size(); j++) {
                if (previous.tieStart[j]) {
                    int midi = previous.pitches.get(j).toMidiNumber();
                    boolean matched = ev.isNote() && ev.pitches.stream().anyMatch(p -> p.toMidiNumber() == midi);
                    previous.tieStart[j] = matched;
                }
            }
            voice.pendingTie = null;
        }
        // Melisma state before this event's own slurs/beams start.
        ev.melisma = voice.manualMelisma || voice.slurDepth > 0 || (voice.beamOpen && !voice.autoBeam);
        boolean tieAll = false;
        for (LyMusic.Post p : e.post()) {
            switch (p) {
                case LyMusic.Tie tie -> tieAll = true;
                case LyMusic.Slur slur -> {
                    if (slur.start()) {
                        ev.slurStarts++;
                        voice.slurDepth++;
                    } else {
                        ev.slurStops++;
                        voice.slurDepth = Math.max(0, voice.slurDepth - 1);
                    }
                }
                case LyMusic.Beam beam -> {
                    if (beam.start()) {
                        ev.beamStart = true;
                        voice.beamOpen = true;
                    } else {
                        ev.beamEnd = true;
                        voice.beamOpen = false;
                    }
                }
                case LyMusic.Artic artic -> ev.articulations.add(artic.articulation());
                default -> {
                    Direction d = directionFor(p);
                    if (d != null) {
                        ev.directions.add(d);
                    }
                }
            }
        }
        if (ev.isNote()) {
            boolean anyTie = false;
            for (int i = 0; i < ev.pitches.size(); i++) {
                boolean perPitch = e.kind() == LyMusic.Event.Kind.NOTE && i < e.pitches().size()
                        && e.pitches().get(i).tie();
                ev.tieStart[i] = tieAll || perPitch;
                anyTie |= ev.tieStart[i];
            }
            if (anyTie) {
                voice.pendingTie = ev;
            }
            if (!voice.pendingGrace.isEmpty()) {
                ev.grace.addAll(voice.pendingGrace);
                voice.pendingGrace.clear();
            }
        }
        // Tuplet brackets: \hide TupletNumber drops the indicator, \hide TupletBracket just its line.
        int actual = 1;
        int normal = 1;
        boolean showTuplet = !hidden(voice, true);
        boolean bracket = !hidden(voice, false);
        for (TupletMark mark : f.tuplets()) {
            actual *= mark.actual;
            normal *= mark.normal;
            if (mark.startPending) {
                if (showTuplet) {
                    ev.tuplets.add(new Tuplet(mark.number, Tuplet.Type.START, bracket, mark.actual, mark.normal,
                            Placement.DEFAULT));
                }
                mark.startPending = false;
            }
            mark.last = showTuplet ? ev : null;
            mark.accumulated = mark.accumulated.plus(length);
            if (mark.span != null && mark.accumulated.compareTo(mark.span) >= 0) {
                if (showTuplet) {
                    ev.tuplets.add(new Tuplet(mark.number, Tuplet.Type.STOP, bracket));
                }
                mark.accumulated = Fraction.ZERO;
                mark.startPending = true;
            }
        }
        ev.actualNotes = actual;
        ev.normalNotes = normal;
        voice.events.add(ev);
        return end;
    }

    /** Whether tuplet numbers (or brackets) are hidden for this voice or its staff. */
    private static boolean hidden(Ctx voice, boolean number) {
        for (Ctx c = voice; c != null; c = c.parent) {
            if (number ? c.hideTupletNumber : c.hideTupletBracket) {
                return true;
            }
        }
        Ctx staff = voice.staff;
        return staff != null && (number ? staff.hideTupletNumber : staff.hideTupletBracket);
    }

    /**
     * Stem direction of a voice's next note: its own override ({@code \stemUp},
     * {@code \override Stem.direction}) wins over {@code \voiceOne}-style
     * defaults, which win over an override on the staff.
     */
    private static Boolean stemFor(Ctx voice) {
        if (voice.hasStemOverride) {
            return voice.stemOverride;
        }
        if (voice.styleStemUp != null) {
            return voice.styleStemUp;
        }
        Ctx staff = voice.staff != null ? voice.staff : nearest(voice, CtxKind.STAFF);
        return staff != null && staff.hasStemOverride ? staff.stemOverride : null;
    }

    /** Resolve an event's written pitches (relative/absolute, transposed). */
    private List<Pitch> resolvePitches(LyMusic.Event e, Frame f) {
        if (e.kind() == LyMusic.Event.Kind.REPEAT_NOTE) {
            return List.of(lastPitches.get(0));
        }
        if (e.kind() == LyMusic.Event.Kind.REPEAT_CHORD) {
            return lastChord;
        }
        List<Pitch> out = new ArrayList<>();
        Pitch chordReference = pitchMode.previous;
        Pitch first = null;
        for (LyMusic.EventPitch ep : e.pitches()) {
            LyMusic.RawPitch raw = ep.pitch();
            int octave;
            if (pitchMode.relative) {
                octave = relativeOctave(raw.step(), chordReference) + raw.octaveMarks();
            } else {
                octave = 3 + pitchMode.octaveOffset + raw.octaveMarks();
            }
            if (ep.octaveCheck() != null) {
                octave = 3 + ep.octaveCheck();
            }
            Pitch written = new Pitch(raw.step(), octave, raw.alter());
            chordReference = written;
            if (first == null) {
                first = written;
            }
            out.add(transpose(written, f));
        }
        if (first != null && pitchMode.relative) {
            pitchMode.previous = first;
        }
        if (!out.isEmpty()) {
            lastPitches = List.of(out.get(0));
            if (out.size() > 1) {
                lastChord = List.copyOf(out);
            } else {
                lastChord = List.copyOf(out);
            }
        }
        return out;
    }

    private static int relativeOctave(Step step, Pitch previous) {
        int prev = previous.octave() * 7 + previous.step().ordinal();
        int best = previous.octave();
        int bestDiff = Integer.MAX_VALUE;
        for (int octave = previous.octave() - 1; octave <= previous.octave() + 1; octave++) {
            int diff = Math.abs(octave * 7 + step.ordinal() - prev);
            if (diff < bestDiff) {
                bestDiff = diff;
                best = octave;
            }
        }
        return best;
    }

    private static Pitch transpose(Pitch p, Frame f) {
        if (f.transposeSteps() == 0 && f.transposeSemitones() == 0) {
            return p;
        }
        int diatonic = p.octave() * 7 + p.step().ordinal() + f.transposeSteps();
        int semitones = p.octave() * 12 + p.step().semitonesFromC() + p.alter() + f.transposeSemitones();
        int octave = Math.floorDiv(diatonic, 7);
        Step step = Step.values()[Math.floorMod(diatonic, 7)];
        int alter = semitones - (octave * 12 + step.semitonesFromC());
        return new Pitch(step, octave, Math.max(-2, Math.min(2, alter)));
    }

    private static int diatonic(LyMusic.RawPitch p) {
        return (3 + p.octaveMarks()) * 7 + p.step().ordinal();
    }

    private static int chromatic(LyMusic.RawPitch p) {
        return (3 + p.octaveMarks()) * 12 + p.step().semitonesFromC() + p.alter();
    }

    private static Direction directionFor(LyMusic.Post post) {
        return switch (post) {
            case LyMusic.Dynamic dyn -> {
                DynamicMark mark = DynamicMark.fromXml(dyn.mark());
                if (mark == null) {
                    mark = switch (dyn.mark()) {
                        case "pppp", "ppppp" -> DynamicMark.PPP;
                        case "ffff", "fffff" -> DynamicMark.FFF;
                        default -> null;
                    };
                }
                yield mark != null
                        ? new Direction(new DirectionType.Dynamic(mark), Placement.BELOW)
                        : new Direction(new DirectionType.Words(dyn.mark(), true, true), Placement.BELOW);
            }
            case LyMusic.Hairpin hairpin -> new Direction(new DirectionType.Wedge(hairpin.type(), 1), Placement.BELOW);
            case LyMusic.Text text -> text.text() == null || text.text().isBlank() ? null
                    : new Direction(new DirectionType.Words(text.text(), text.italic(), text.bold()),
                    text.placement() == Placement.DEFAULT ? Placement.ABOVE : text.placement());
            default -> null;
        };
    }

    // ----- commands ------------------------------------------------------------------------------

    private void command(LyMusic music, Fraction t, Frame f) {
        switch (music) {
            case LyMusic.TimeSig ts -> {
                if (ts.beats() > 0 && ts.beatType() > 0) {
                    global.times.put(t, new TimeSignature(ts.beats(), ts.beatType()));
                }
            }
            case LyMusic.KeySig key -> {
                Pitch tonic = transpose(new Pitch(key.tonic().step(), 4, key.tonic().alter()), f);
                KeySignature signature = keyFor(tonic.step(), tonic.alter(), key.mode());
                Ctx staff = staffFor(f, false);
                if (staff != null) {
                    staff.keys.put(t, signature);
                } else {
                    global.keys.put(t, signature);
                }
            }
            case LyMusic.ClefChange clef -> {
                Ctx staff = staffFor(f, false);
                if (staff == null && f.ctx().kind != CtxKind.SCORE && f.ctx().kind != CtxKind.GROUP
                        && f.ctx().kind != CtxKind.PIANO_STAFF) {
                    return;
                }
                if (staff == null) {
                    staff = staffFor(f, true);
                }
                if (staff != null) {
                    staff.clefs.put(t, clef.name());
                }
            }
            case LyMusic.BarLine bar -> global.bars.put(t, bar.style());
            case LyMusic.Partial partial -> global.partials.put(t, partial.length());
            case LyMusic.Tempo tempo -> {
                if (tempo.text() != null && !tempo.text().isBlank()) {
                    global.directions.add(new TimedDirection(t, new Direction(
                            new DirectionType.Words(tempo.text(), false, true), Placement.ABOVE), null));
                }
                // tempoHideNote: the metronome mark only sets the playback tempo.
                if (tempo.unit() != null && tempo.perMinute() > 0 && !tempoHideNote) {
                    global.directions.add(new TimedDirection(t, new Direction(
                            new DirectionType.Metronome(tempo.unit(), tempo.dots() > 0, tempo.perMinute()),
                            Placement.ABOVE), null));
                }
            }
            case LyMusic.Mark mark -> {
                String label = mark.label();
                if (label == null) {
                    label = String.valueOf((char) ('A' + rehearsalCounter % 26));
                }
                rehearsalCounter++;
                global.directions.add(new TimedDirection(t, new Direction(new DirectionType.Rehearsal(label),
                        Placement.ABOVE), null));
            }
            case LyMusic.TextMark text -> global.directions.add(new TimedDirection(t, new Direction(
                    new DirectionType.Words(text.text(), false, true), Placement.ABOVE), null));
            case LyMusic.SetProperty set -> {
                Ctx target = contextForSet(f.ctx(), set.context());
                applyProperty(target, set.property(), set.value(), t);
            }
            case LyMusic.VoiceStyle style -> {
                Ctx voice = voiceFor(f);
                if (voice != null) {
                    voice.styleStemUp = style.voice() == 0 ? null : style.voice() % 2 == 1;
                }
            }
            case LyMusic.StemDirection stem -> {
                Ctx voice = voiceFor(f);
                if (voice != null) {
                    voice.hasStemOverride = stem.up() != null;
                    voice.stemOverride = stem.up();
                }
            }
            case LyMusic.ChangeStaff change -> {
                Ctx voice = voiceFor(f);
                Ctx staff = findByName(CtxKind.STAFF, change.staff());
                if (voice != null && staff != null) {
                    voice.staff = staff;
                }
            }
            case LyMusic.Cadenza cadenza -> global.cadenza.put(t, cadenza.on());
            case LyMusic.Break ignored -> global.breaks.add(t);
            case LyMusic.AutoBeam autoBeam -> {
                Ctx voice = voiceFor(f);
                if (voice != null) {
                    voice.autoBeam = autoBeam.on();
                }
            }
            case LyMusic.HideNotes hide -> {
                Ctx voice = voiceFor(f);
                if (voice != null) {
                    voice.hidden = hide.hide();
                }
            }
            case LyMusic.Melisma melisma -> {
                Ctx voice = voiceFor(f);
                if (voice != null) {
                    voice.manualMelisma = melisma.start();
                }
            }
            default -> {
            }
        }
    }

    private void applyProperty(Ctx target, String property, String value, Fraction t) {
        if (target == null || property == null) {
            return;
        }
        String text = value == null ? null : value.strip();
        boolean schemeFalse = "##f".equals(text);
        switch (property) {
            case "instrumentName", "vocalName" -> {
                if (text != null && !text.startsWith("#") && !text.isBlank()) {
                    target.instrumentName = text;
                }
            }
            case "shortInstrumentName", "shortVocalName" -> {
                if (text != null && !text.startsWith("#") && !text.isBlank()) {
                    target.shortInstrumentName = text;
                }
            }
            case "currentBarNumber" -> {
                if (text != null && t.isZero()) {
                    String digits = text.replaceAll("[^0-9]", "");
                    if (!digits.isEmpty()) {
                        global.firstBarNumber = Integer.parseInt(digits);
                    }
                }
            }
            case "autoBeaming" -> {
                for (Ctx c = target; c != null; c = c.parent) {
                    if (c.kind == CtxKind.VOICE) {
                        c.autoBeam = !schemeFalse;
                        return;
                    }
                }
                setAutoBeamBelow(target, !schemeFalse);
            }
            case "timing" -> {
                if (text != null && text.startsWith("##")) {
                    global.cadenza.put(t, schemeFalse);
                }
            }
            case "associatedVoice" -> target.associatedVoice = text;
            case "tempoHideNote" -> tempoHideNote = "##t".equals(text);
            case "hide.TupletNumber" -> target.hideTupletNumber = true;
            case "hide.TupletBracket" -> target.hideTupletBracket = true;
            case "Stem.direction" -> {
                target.stemOverride = LilyPondParser.stemDirection(text);
                target.hasStemOverride = target.stemOverride != null;
            }
            default -> {
            }
        }
    }

    private static void setAutoBeamBelow(Ctx ctx, boolean on) {
        ctx.autoBeam = on;
        for (Ctx child : ctx.children) {
            setAutoBeamBelow(child, on);
        }
    }

    static KeySignature keyFor(Step step, int alter, String mode) {
        int natural = switch (step) {
            case C -> 0;
            case D -> 2;
            case E -> 4;
            case F -> -1;
            case G -> 1;
            case A -> 3;
            case B -> 5;
        };
        int offset = switch (mode == null ? "" : mode.toLowerCase(Locale.ROOT)) {
            case "minor", "aeolian" -> -3;
            case "dorian" -> -2;
            case "phrygian" -> -4;
            case "lydian" -> 1;
            case "mixolydian" -> -1;
            case "locrian" -> -5;
            default -> 0;
        };
        int fifths = natural + alter * 7 + offset;
        while (fifths > 7) {
            fifths -= 12;
        }
        while (fifths < -7) {
            fifths += 12;
        }
        return new KeySignature(fifths);
    }

    // ----- part combining ------------------------------------------------------------------------

    /**
     * Merge {@code \partCombine}d parts where they move together: notes that
     * start together with the same length become one chord (unisons once,
     * stems free), rests in both become one rest. The rest stays apart as
     * two voices, stems up and down.
     */
    private void combineParts() {
        for (Ctx[] pair : partCombines) {
            Ctx first = pair[0];
            Ctx second = pair[1];
            Map<Fraction, Ev> firstByStart = new java.util.HashMap<>();
            for (Ev ev : first.events) {
                firstByStart.putIfAbsent(ev.start, ev);
            }
            java.util.Iterator<Ev> it = second.events.iterator();
            while (it.hasNext()) {
                Ev ev = it.next();
                Ev match = firstByStart.get(ev.start);
                if (match == null || !match.length.equals(ev.length) || match.staff != ev.staff) {
                    continue;
                }
                boolean bothNotes = match.isNote() && ev.isNote();
                boolean bothRests = !match.isNote() && !ev.isNote() && match.kind != Ev.Kind.HIDDEN
                        && ev.kind != Ev.Kind.HIDDEN;
                if (bothNotes) {
                    match.absorb(ev, true);
                    match.stemUp = null;
                    it.remove();
                } else if (bothRests) {
                    it.remove();
                }
            }
        }
    }

    // ----- lyrics --------------------------------------------------------------------------------

    private void applyLyrics() {
        for (LyricRequest request : lyricRequests) {
            Ctx voice = request.voice();
            if (request.voiceName() != null) {
                Ctx named = findByName(CtxKind.VOICE, request.voiceName());
                if (named != null) {
                    voice = named;
                }
            }
            if (voice == null || voice.kind != CtxKind.VOICE) {
                continue;
            }
            int verse = ++voice.verses;
            List<Ev> events = new ArrayList<>(voice.events);
            events.sort((a, b) -> a.start.compareTo(b.start));
            List<LyMusic.Syllable> syllables = request.lyrics().syllables();
            int s = 0;
            boolean previousHyphen = false;
            for (Ev ev : events) {
                if (s >= syllables.size()) {
                    break;
                }
                if (ev.kind != Ev.Kind.NOTE && ev.kind != Ev.Kind.HIDDEN) {
                    continue;
                }
                if (ev.melisma || isTieContinuation(ev)) {
                    continue;
                }
                LyMusic.Syllable syllable = syllables.get(s++);
                if (syllable.skip()) {
                    previousHyphen = false;
                    continue;
                }
                Syllabic syllabic;
                if (previousHyphen) {
                    syllabic = syllable.hyphen() ? Syllabic.MIDDLE : Syllabic.END;
                } else {
                    syllabic = syllable.hyphen() ? Syllabic.BEGIN : Syllabic.SINGLE;
                }
                previousHyphen = syllable.hyphen();
                if (ev.kind == Ev.Kind.NOTE) {
                    ev.lyrics.add(new Lyric(syllable.text(), syllabic, verse));
                }
            }
        }
    }

    private static boolean isTieContinuation(Ev ev) {
        if (ev.tieStop.length == 0) {
            return false;
        }
        for (boolean stop : ev.tieStop) {
            if (!stop) {
                return false;
            }
        }
        return true;
    }

    // ----- contexts ------------------------------------------------------------------------------

    private Ctx newContext(CtxKind kind, String type, String name, Ctx parent) {
        Ctx ctx = new Ctx(kind, type, name, parent);
        allContexts.add(ctx);
        return ctx;
    }

    private String resolveType(String type) {
        String current = type;
        for (int i = 0; i < 8 && contextTypes.containsKey(current); i++) {
            current = contextTypes.get(current).base();
        }
        return current;
    }

    private static boolean isUnmetered(String type) {
        return type.startsWith("Vaticana") || type.startsWith("Gregorian") || type.startsWith("Kievan");
    }

    static CtxKind kindOf(String type) {
        String t = type;
        for (String prefix : new String[]{"Vaticana", "Mensural", "Petrucci", "Kievan", "GregorianTranscription",
                "Gregorian"}) {
            if (t.startsWith(prefix) && t.length() > prefix.length()) {
                t = t.substring(prefix.length());
                break;
            }
        }
        return switch (t) {
            case "Score" -> CtxKind.SCORE;
            case "StaffGroup", "ChoirStaff", "Engraver_group_", "OneStaff" -> CtxKind.GROUP;
            case "PianoStaff", "GrandStaff" -> CtxKind.PIANO_STAFF;
            case "Staff", "DrumStaff", "RhythmicStaff" -> CtxKind.STAFF;
            case "Voice", "DrumVoice", "CueVoice", "NullVoice" -> CtxKind.VOICE;
            case "Lyrics" -> CtxKind.LYRICS;
            case "ChordNames" -> CtxKind.CHORD_NAMES;
            case "Dynamics", "Engraver_group", "NoteNames" -> CtxKind.DYNAMICS;
            default -> CtxKind.IGNORED;
        };
    }

    /** Where a new context of this kind goes, given where the music creating it is. */
    private Ctx parentFor(CtxKind kind, Frame f) {
        Ctx at = f.ctx();
        return switch (kind) {
            case VOICE -> staffFor(f, true);
            case SCORE -> root;
            default -> {
                Ctx c = at;
                while (c != null && c.kind != CtxKind.SCORE && c.kind != CtxKind.GROUP
                        && c.kind != CtxKind.PIANO_STAFF) {
                    c = c.parent;
                }
                yield c == null ? root : c;
            }
        };
    }

    private Ctx findExisting(CtxKind kind, String name, Ctx from) {
        if (name != null) {
            return findByName(kind, name);
        }
        for (Ctx c = from; c != null; c = c.parent) {
            if (c.kind == kind) {
                return c;
            }
        }
        return null;
    }

    private Ctx findByName(CtxKind kind, String name) {
        if (name == null) {
            return null;
        }
        for (int i = allContexts.size() - 1; i >= 0; i--) {
            Ctx c = allContexts.get(i);
            if (c.kind == kind && name.equals(c.name)) {
                return c;
            }
        }
        return null;
    }

    private static Ctx findChild(Ctx parent, CtxKind kind, String name) {
        for (Ctx c : parent.children) {
            if (c.kind == kind && name.equals(c.name)) {
                return c;
            }
        }
        return null;
    }

    private static Ctx nearest(Ctx from, CtxKind kind) {
        for (Ctx c = from; c != null; c = c.parent) {
            if (c.kind == kind) {
                return c;
            }
        }
        return null;
    }

    /** The staff the music at this frame belongs to; with {@code create}, made implicitly when needed. */
    private Ctx staffFor(Frame f, boolean create) {
        Ctx at = f.ctx();
        if (at.kind == CtxKind.VOICE) {
            return at.staff != null ? at.staff : nearest(at, CtxKind.STAFF);
        }
        if (at.kind == CtxKind.STAFF) {
            return at;
        }
        Ctx staff = nearest(at, CtxKind.STAFF);
        if (staff != null || !create) {
            return staff;
        }
        Ctx holder = at;
        while (holder != null && holder.kind != CtxKind.SCORE && holder.kind != CtxKind.GROUP
                && holder.kind != CtxKind.PIANO_STAFF) {
            holder = holder.parent;
        }
        if (holder == null) {
            holder = root;
        }
        if (holder.defaultStaff == null) {
            holder.defaultStaff = newContext(CtxKind.STAFF, "Staff", null, holder);
        }
        return holder.defaultStaff;
    }

    /** The voice notes at this frame go to, created implicitly when needed. */
    private Ctx voiceFor(Frame f) {
        Ctx at = f.ctx();
        if (at.kind == CtxKind.VOICE) {
            return at;
        }
        if (at.kind == CtxKind.IGNORED || at.kind == CtxKind.LYRICS || at.kind == CtxKind.CHORD_NAMES
                || at.kind == CtxKind.DYNAMICS) {
            return null;
        }
        Ctx staff = staffFor(f, true);
        if (staff.defaultVoice == null) {
            staff.defaultVoice = newContext(CtxKind.VOICE, "Voice", null, staff);
            staff.defaultVoice.staff = staff;
        }
        return staff.defaultVoice;
    }

    /** Where an event at this frame goes: a voice, a dynamics/mark line, or nowhere. */
    private Ctx eventTarget(Frame f) {
        Ctx at = f.ctx();
        switch (at.kind) {
            case IGNORED, LYRICS, CHORD_NAMES -> {
                return null;
            }
            case DYNAMICS -> {
                return at;
            }
            default -> {
                return voiceFor(f);
            }
        }
    }

    private Ctx chordNamesFor(Frame f) {
        Ctx staff = staffFor(f, false);
        Ctx holder = staff != null ? staff : f.ctx();
        return holder;
    }

    private Ctx contextForSet(Ctx at, String contextName) {
        if (contextName == null) {
            return at;
        }
        CtxKind kind = kindOf(resolveType(contextName));
        if (contextName.equals("Score") || contextName.equals("Timing")) {
            return root;
        }
        for (Ctx c = at; c != null; c = c.parent) {
            if (c.type.equals(contextName) || resolveType(c.type).equals(contextName)) {
                return c;
            }
        }
        if (kind == CtxKind.STAFF && at.kind == CtxKind.VOICE && at.staff != null) {
            return at.staff;
        }
        for (Ctx c = at; c != null; c = c.parent) {
            if (c.kind == kind) {
                return c;
            }
        }
        if (kind == CtxKind.STAFF) {
            // `\set Staff.instrumentName` before the first note creates the implicit staff.
            Ctx holder = at;
            while (holder != null && holder.kind != CtxKind.SCORE && holder.kind != CtxKind.GROUP
                    && holder.kind != CtxKind.PIANO_STAFF) {
                holder = holder.parent;
            }
            if (holder != null) {
                if (holder.defaultStaff == null) {
                    holder.defaultStaff = newContext(CtxKind.STAFF, "Staff", null, holder);
                }
                return holder.defaultStaff;
            }
        }
        return at;
    }

    private void touch(Ctx voice) {
        lastVoice = voice;
        for (Ctx[] tracker : voiceTrackers) {
            if (tracker[0] == null) {
                tracker[0] = voice;
            }
        }
    }
}
