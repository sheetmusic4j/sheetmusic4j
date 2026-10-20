package com.sheetmusic4j.core.lilypond;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.sheetmusic4j.core.lilypond.LilyPondInterpreter.Ctx;
import com.sheetmusic4j.core.lilypond.LilyPondInterpreter.CtxKind;
import com.sheetmusic4j.core.lilypond.LilyPondInterpreter.Ev;
import com.sheetmusic4j.core.lilypond.LilyPondInterpreter.Global;
import com.sheetmusic4j.core.lilypond.LilyPondInterpreter.RepeatSpan;
import com.sheetmusic4j.core.lilypond.LilyPondInterpreter.Timed;
import com.sheetmusic4j.core.lilypond.LilyPondInterpreter.TimedDirection;
import com.sheetmusic4j.core.model.Accidental;
import com.sheetmusic4j.core.model.Attributes;
import com.sheetmusic4j.core.model.Barline;
import com.sheetmusic4j.core.model.Beam;
import com.sheetmusic4j.core.model.Chord;
import com.sheetmusic4j.core.model.Clef;
import com.sheetmusic4j.core.model.ClefSign;
import com.sheetmusic4j.core.model.Duration;
import com.sheetmusic4j.core.model.Fraction;
import com.sheetmusic4j.core.model.GroupSymbol;
import com.sheetmusic4j.core.model.KeySignature;
import com.sheetmusic4j.core.model.Measure;
import com.sheetmusic4j.core.model.MusicElement;
import com.sheetmusic4j.core.model.Note;
import com.sheetmusic4j.core.model.NoteType;
import com.sheetmusic4j.core.model.Part;
import com.sheetmusic4j.core.model.PartGroup;
import com.sheetmusic4j.core.model.Pitch;
import com.sheetmusic4j.core.model.Placement;
import com.sheetmusic4j.core.model.Rest;
import com.sheetmusic4j.core.model.Score;
import com.sheetmusic4j.core.model.Slur;
import com.sheetmusic4j.core.model.Step;
import com.sheetmusic4j.core.model.TimeModification;
import com.sheetmusic4j.core.model.TimeSignature;
import com.sheetmusic4j.core.model.Tuplet;

/**
 * Turns an interpreted LilyPond score into parts and measures: it lays the
 * bar grid from time signatures, pickups and cadenzas, cuts notes at bar lines
 * (tying the pieces), fills each voice's gaps with invisible rests, and works
 * out what LilyPond leaves implicit - accidentals, beams, staff grouping.
 */
final class LilyPondScoreAssembler {

    private static final Fraction FOUR_FOUR = Fraction.of(4);
    private static final int MAX_DIVISIONS = 1 << 20;

    private final Global global;
    private final List<Fraction> bars;
    private final List<Fraction[]> cadenzas;
    private int divisions = 1;

    private LilyPondScoreAssembler(Global global) {
        this.global = global;
        this.cadenzas = cadenzaSpans(global);
        this.bars = barGrid();
    }

    /** Add the parts and part groups of an interpreted score to {@code score}. */
    static void assemble(LilyPondInterpreter.Result result, Score.Builder score) {
        new LilyPondScoreAssembler(result.global()).build(result.root(), score);
    }

    // ----- structure -----------------------------------------------------------------------------

    /** A part: one staff, or the staves of a piano/grand staff. */
    private static final class PartPlan {
        final Ctx owner;
        final List<Ctx> staves = new ArrayList<>();

        PartPlan(Ctx owner) {
            this.owner = owner;
        }
    }

    private void build(Ctx root, Score.Builder score) {
        List<Ctx> order = new ArrayList<>();
        dfs(root, order);
        Map<Ctx, List<Ev>> eventsByStaff = new IdentityHashMap<>();
        for (Ctx ctx : order) {
            if (ctx.kind == CtxKind.VOICE) {
                for (Ev ev : ctx.events) {
                    Ctx staff = ev.staff != null ? ev.staff : ctx.staff;
                    if (staff != null) {
                        eventsByStaff.computeIfAbsent(staff, k -> new ArrayList<>()).add(ev);
                    }
                }
            }
        }

        // Parts, in context order. Staves without any voice are dropped.
        List<PartPlan> parts = new ArrayList<>();
        Map<Ctx, PartPlan> partByOwner = new IdentityHashMap<>();
        Map<Ctx, PartPlan> partByStaff = new IdentityHashMap<>();
        for (Ctx ctx : order) {
            if (!ctx.isStaff()) {
                continue;
            }
            boolean hasVoice = ctx.children.stream().anyMatch(c -> c.kind == CtxKind.VOICE)
                    || eventsByStaff.containsKey(ctx);
            if (!hasVoice) {
                continue;
            }
            Ctx owner = ctx;
            for (Ctx c = ctx.parent; c != null; c = c.parent) {
                if (c.kind == CtxKind.PIANO_STAFF) {
                    if (!isInstrumentGroup(c)) {
                        owner = c;
                    }
                    break;
                }
            }
            PartPlan plan = partByOwner.get(owner);
            if (plan == null) {
                plan = new PartPlan(owner);
                partByOwner.put(owner, plan);
                parts.add(plan);
            }
            plan.staves.add(ctx);
            partByStaff.put(ctx, plan);
        }
        if (parts.isEmpty()) {
            return;
        }

        // Exact divisions for every time and length that occurs.
        long lcm = 1;
        for (Fraction b : bars) {
            lcm = lcm(lcm, b.denominator());
        }
        for (List<Ev> events : eventsByStaff.values()) {
            for (Ev ev : events) {
                lcm = lcm(lcm, ev.start.denominator());
                lcm = lcm(lcm, ev.length.denominator());
                // Decomposed pieces of a tuplet note need its own denominators.
                lcm = lcm(lcm, ev.length.denominator() * Math.max(1, ev.normalNotes));
            }
        }
        divisions = (int) Math.max(1, Math.min(lcm, MAX_DIVISIONS));
        if (divisions % 2 != 0 && divisions * 2L <= MAX_DIVISIONS) {
            divisions *= 2;
        }

        // Directions and chord symbols by part.
        Map<PartPlan, List<Timed<MusicElement>>> markings = new IdentityHashMap<>();
        for (PartPlan plan : parts) {
            markings.put(plan, new ArrayList<>());
        }
        for (TimedDirection td : global.directions) {
            PartPlan target = td.context() == null ? parts.get(0) : partNear(td.context(), order, partByStaff, parts);
            markings.get(target).add(new Timed<>(td.time(), td.direction()));
        }
        for (Ctx ctx : order) {
            if (!ctx.harmonies.isEmpty()) {
                PartPlan target = ctx.isStaff() && partByStaff.containsKey(ctx) ? partByStaff.get(ctx)
                        : partNear(ctx, order, partByStaff, parts);
                for (Timed<com.sheetmusic4j.core.model.Harmony> h : ctx.harmonies) {
                    markings.get(target).add(new Timed<>(h.time(), h.value()));
                }
            }
        }

        int index = 0;
        for (PartPlan plan : parts) {
            index++;
            score.addPart(buildPart(plan, "P" + index, eventsByStaff, markings.get(plan)));
        }
        addPartGroups(root, parts, partByStaff, score);
    }

    /**
     * A grand staff whose staves are named separately (percussion, say) is a
     * braced group of instruments rather than one instrument on several staves.
     */
    private static boolean isInstrumentGroup(Ctx ctx) {
        if (!ctx.type.contains("GrandStaff") || ctx.instrumentName != null) {
            return false;
        }
        List<Ctx> below = new ArrayList<>();
        dfs(ctx, below);
        return below.stream().anyMatch(c -> c.isStaff() && c.instrumentName != null);
    }

    private static void dfs(Ctx ctx, List<Ctx> out) {
        out.add(ctx);
        for (Ctx child : ctx.children) {
            dfs(child, out);
        }
    }

    /**
     * The part a non-staff context (chord names, dynamics, mark line) belongs
     * to: the next staff below it, or the staff above for dynamics.
     */
    private static PartPlan partNear(Ctx ctx, List<Ctx> order, Map<Ctx, PartPlan> partByStaff, List<PartPlan> parts) {
        if (ctx.isStaff() && partByStaff.containsKey(ctx)) {
            return partByStaff.get(ctx);
        }
        int at = order.indexOf(ctx);
        boolean below = ctx.kind != CtxKind.DYNAMICS || ctx.type.contains("Mark") || ctx.type.equals("Engraver_group");
        if (ctx.kind == CtxKind.DYNAMICS && ctx.type.equals("Dynamics")) {
            below = false;
        }
        if (at >= 0) {
            if (below) {
                for (int i = at + 1; i < order.size(); i++) {
                    PartPlan p = partByStaff.get(order.get(i));
                    if (p != null) {
                        return p;
                    }
                }
            }
            for (int i = at - 1; i >= 0; i--) {
                PartPlan p = partByStaff.get(order.get(i));
                if (p != null) {
                    return p;
                }
            }
            for (int i = at + 1; i < order.size(); i++) {
                PartPlan p = partByStaff.get(order.get(i));
                if (p != null) {
                    return p;
                }
            }
        }
        return parts.get(0);
    }

    private static void addPartGroups(Ctx root, List<PartPlan> parts, Map<Ctx, PartPlan> partByStaff,
                                      Score.Builder score) {
        List<Ctx> order = new ArrayList<>();
        dfs(root, order);
        int number = 0;
        for (Ctx ctx : order) {
            boolean braced = ctx.kind == CtxKind.PIANO_STAFF && isInstrumentGroup(ctx);
            if (ctx.kind != CtxKind.GROUP && !braced) {
                continue;
            }
            int first = Integer.MAX_VALUE;
            int last = -1;
            List<Ctx> below = new ArrayList<>();
            dfs(ctx, below);
            for (Ctx c : below) {
                PartPlan p = partByStaff.get(c);
                if (p != null) {
                    int i = parts.indexOf(p);
                    first = Math.min(first, i);
                    last = Math.max(last, i);
                }
            }
            if (last < 0) {
                continue;
            }
            GroupSymbol symbol = braced ? GroupSymbol.BRACE : switch (ctx.delimiter == null ? "" : ctx.delimiter) {
                case "square" -> GroupSymbol.SQUARE;
                case "brace" -> GroupSymbol.BRACE;
                case "line" -> GroupSymbol.LINE;
                case "none" -> GroupSymbol.NONE;
                default -> GroupSymbol.BRACKET;
            };
            boolean choir = ctx.type.contains("Choir");
            score.addPartGroup(new PartGroup(++number, first, last, symbol, !choir,
                    ctx.instrumentName, ctx.shortInstrumentName));
        }
    }

    // ----- bar grid ------------------------------------------------------------------------------

    private static List<Fraction[]> cadenzaSpans(Global g) {
        List<Fraction[]> spans = new ArrayList<>();
        Fraction start = null;
        for (Map.Entry<Fraction, Boolean> e : g.cadenza.entrySet()) {
            if (e.getValue() && start == null) {
                start = e.getKey();
            } else if (!e.getValue() && start != null) {
                if (e.getKey().compareTo(start) > 0) {
                    spans.add(new Fraction[]{start, e.getKey()});
                }
                start = null;
            }
        }
        if (start != null) {
            spans.add(new Fraction[]{start, Fraction.max(g.end, start)});
        }
        return spans;
    }

    private boolean inCadenza(Fraction t) {
        for (Fraction[] span : cadenzas) {
            if (t.compareTo(span[0]) >= 0 && t.compareTo(span[1]) < 0) {
                return true;
            }
        }
        return false;
    }

    private TimeSignature timeAt(Fraction t) {
        Map.Entry<Fraction, TimeSignature> e = global.times.floorEntry(t);
        return e == null ? null : e.getValue();
    }

    private static Fraction measureLength(TimeSignature ts) {
        return ts == null ? FOUR_FOUR : Fraction.of(4L * ts.beats(), ts.beatType());
    }

    /** Bar line times from 0 to the end of the music, both included. */
    private List<Fraction> barGrid() {
        List<Fraction> out = new ArrayList<>();
        out.add(Fraction.ZERO);
        Fraction end = global.end;
        if (!end.isPositive()) {
            out.add(measureLength(timeAt(Fraction.ZERO)));
            return out;
        }
        List<Fraction> explicit = new ArrayList<>();
        for (Map.Entry<Fraction, String> e : global.bars.entrySet()) {
            if (!e.getValue().isEmpty() && e.getKey().isPositive() && e.getKey().compareTo(end) < 0) {
                explicit.add(e.getKey());
            }
        }
        Fraction t = Fraction.ZERO;
        Fraction carry = null;
        int guard = 0;
        while (t.compareTo(end) < 0 && guard++ < 100_000) {
            Fraction length = carry != null ? carry : measureLength(timeAt(t));
            Fraction partial = global.partials.get(t);
            if (carry == null && partial != null && partial.isPositive()) {
                length = partial;
            }
            carry = null;
            Fraction remaining = length;
            Fraction cursor = t;
            Fraction next = null;
            List<Fraction> extra = new ArrayList<>();
            for (int i = 0; i < 1000 && next == null; i++) {
                Fraction candidate = cursor.plus(remaining);
                Fraction[] cad = cadenzaFrom(cursor, candidate);
                if (cad != null) {
                    Fraction a = Fraction.max(cad[0], cursor);
                    remaining = remaining.minus(a.minus(cursor));
                    Fraction cut = firstBetween(explicit, Fraction.max(t, a), cad[1], t);
                    if (cut != null) {
                        next = cut;
                        carry = remaining.isPositive() ? remaining : null;
                    } else if (cad[1].compareTo(end) >= 0) {
                        next = end;
                    } else {
                        cursor = cad[1];
                    }
                } else {
                    next = candidate;
                    for (Fraction change : global.times.keySet()) {
                        if (change.compareTo(t) > 0 && change.compareTo(next) < 0) {
                            next = change;
                        }
                    }
                    for (Fraction b : explicit) {
                        if (b.compareTo(t) > 0 && b.compareTo(next) < 0) {
                            extra.add(b);
                        }
                    }
                }
            }
            if (next == null || next.compareTo(t) <= 0) {
                next = t.plus(measureLength(timeAt(t)));
            }
            if (next.compareTo(end) > 0) {
                next = end;
            }
            for (Fraction b : extra) {
                if (b.compareTo(next) < 0) {
                    out.add(b);
                }
            }
            out.add(next);
            t = next;
        }
        return out;
    }

    /** The first cadenza that starts in (or is running at) {@code [from, to)}. */
    private Fraction[] cadenzaFrom(Fraction from, Fraction to) {
        for (Fraction[] span : cadenzas) {
            boolean running = span[0].compareTo(from) <= 0 && span[1].compareTo(from) > 0;
            boolean starts = span[0].compareTo(from) >= 0 && span[0].compareTo(to) < 0;
            if (running || starts) {
                return span;
            }
        }
        return null;
    }

    private static Fraction firstBetween(List<Fraction> times, Fraction fromInclusive, Fraction toExclusive,
                                         Fraction after) {
        for (Fraction b : times) {
            if (b.compareTo(after) > 0 && b.compareTo(fromInclusive) >= 0 && b.compareTo(toExclusive) < 0) {
                return b;
            }
        }
        return null;
    }

    private int measureIndexAt(Fraction t) {
        int lo = 0;
        int hi = bars.size() - 2;
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (bars.get(mid).compareTo(t) <= 0) {
                lo = mid;
            } else {
                hi = mid - 1;
            }
        }
        return Math.max(0, lo);
    }

    // ----- parts ---------------------------------------------------------------------------------

    /** One displayed element (or piece of a split event) before it becomes a model object. */
    private static final class Piece {
        Ev ev;
        Fraction start;
        Fraction length;
        NoteType type;
        int dots;
        boolean first;
        boolean last;
        boolean measureRest;
        boolean invisible;
        Accidental[] accidentals;
        List<Beam> beams = List.of();

        Fraction end() {
            return start.plus(length);
        }

        boolean isNote() {
            return ev != null && ev.kind == Ev.Kind.NOTE;
        }
    }

    private Part buildPart(PartPlan plan, String id, Map<Ctx, List<Ev>> eventsByStaff,
                           List<Timed<MusicElement>> markings) {
        int measureCount = bars.size() - 1;
        int staffCount = plan.staves.size();
        // pieces[staff][lane][measure]
        List<List<List<List<Piece>>>> pieces = new ArrayList<>();
        for (Ctx staff : plan.staves) {
            List<List<Ev>> lanes = lanes(eventsByStaff.getOrDefault(staff, List.of()));
            if (lanes.isEmpty()) {
                lanes.add(new ArrayList<>());
            }
            List<List<List<Piece>>> staffPieces = new ArrayList<>();
            for (int lane = 0; lane < lanes.size(); lane++) {
                List<List<Piece>> byMeasure = new ArrayList<>();
                for (int m = 0; m < measureCount; m++) {
                    byMeasure.add(new ArrayList<>());
                }
                for (Ev ev : lanes.get(lane)) {
                    for (Piece p : piecesOf(ev)) {
                        byMeasure.get(measureIndexAt(p.start)).add(p);
                    }
                }
                for (int m = 0; m < measureCount; m++) {
                    fillGaps(byMeasure.get(m), m, lane == 0);
                }
                staffPieces.add(byMeasure);
            }
            pieces.add(staffPieces);
        }

        for (int s = 0; s < staffCount; s++) {
            for (int m = 0; m < measureCount; m++) {
                assignAccidentals(plan.staves.get(s), pieces.get(s), m);
                for (List<List<Piece>> lane : pieces.get(s)) {
                    assignBeams(lane.get(m), m);
                }
            }
        }

        // Markings by measure.
        List<List<Timed<MusicElement>>> markingsByMeasure = new ArrayList<>();
        for (int m = 0; m < measureCount; m++) {
            markingsByMeasure.add(new ArrayList<>());
        }
        for (Timed<MusicElement> t : markings) {
            markingsByMeasure.get(measureIndexAt(t.time())).add(t);
        }
        for (int s = 0; s < staffCount; s++) {
            for (List<List<Piece>> lane : pieces.get(s)) {
                for (int m = 0; m < measureCount; m++) {
                    for (Piece p : lane.get(m)) {
                        if (p.first && p.ev != null) {
                            for (com.sheetmusic4j.core.model.Direction d : p.ev.directions) {
                                markingsByMeasure.get(m).add(new Timed<>(p.start, d));
                            }
                        }
                    }
                }
            }
        }

        Part.Builder part = Part.builder(id);
        Ctx nameSource = plan.owner;
        String name = nameSource.instrumentName;
        String abbreviation = nameSource.shortInstrumentName;
        if (name == null && staffCount == 1) {
            name = plan.staves.get(0).instrumentName;
            abbreviation = plan.staves.get(0).shortInstrumentName;
        }
        if (name == null) {
            // e.g. an instrument name that is an incipit score: show the short name.
            name = abbreviation;
        }
        if (name != null) {
            part.name(name);
        }
        if (abbreviation != null) {
            part.abbreviation(abbreviation);
        }

        boolean pickup = global.partials.containsKey(Fraction.ZERO);
        int firstNumber = global.firstBarNumber != null ? global.firstBarNumber : 1;
        if (pickup) {
            firstNumber--;
        }
        KeySignature previousKey = null;
        TimeSignature previousTime = null;
        List<Clef> previousClefs = null;
        for (int m = 0; m < measureCount; m++) {
            Fraction start = bars.get(m);
            Fraction end = bars.get(m + 1);
            Measure.Builder mb = Measure.builder(firstNumber + m);

            KeySignature key = keyAt(plan.staves.get(0), start);
            TimeSignature time = timeAt(start);
            // Unmetered music (chant, cadenza from the start) shows no time signature.
            boolean unmetered = global.timeSignaturesHidden
                    || (global.times.isEmpty() && Boolean.TRUE.equals(global.cadenza.get(Fraction.ZERO)));
            TimeSignature shownTime = unmetered ? null : time == null ? TimeSignature.fourFour() : time;
            List<Clef> clefs = new ArrayList<>();
            for (Ctx staff : plan.staves) {
                clefs.add(clefAt(staff, start));
            }
            if (m == 0) {
                Attributes.Builder ab = Attributes.builder().divisions(divisions).keySignature(key)
                        .timeSignature(shownTime).clefs(clefs);
                if (staffCount > 1) {
                    ab.staves(staffCount);
                }
                mb.attributes(ab.build());
            } else {
                Attributes.Builder ab = Attributes.builder();
                boolean changed = false;
                if (!key.equals(previousKey)) {
                    ab.keySignature(key);
                    changed = true;
                }
                if (shownTime != null && !shownTime.equals(previousTime)) {
                    ab.timeSignature(shownTime);
                    changed = true;
                }
                if (!clefs.equals(previousClefs)) {
                    ab.clefs(clefs);
                    changed = true;
                }
                if (changed) {
                    mb.attributes(ab.build());
                }
            }
            previousKey = key;
            previousTime = shownTime;
            previousClefs = clefs;

            // Elements: staff by staff, voice by voice; markings go with the first voice.
            List<MusicElement> elements = new ArrayList<>();
            for (int s = 0; s < staffCount; s++) {
                List<List<List<Piece>>> lanes = pieces.get(s);
                for (int lane = 0; lane < lanes.size(); lane++) {
                    List<Piece> measurePieces = lanes.get(lane).get(m);
                    if (measurePieces.isEmpty()) {
                        continue;
                    }
                    List<Timed<MusicElement>> laneMarkings = s == 0 && lane == 0
                            ? sortedMarkings(markingsByMeasure.get(m)) : List.of();
                    int nextMarking = 0;
                    for (Piece p : measurePieces) {
                        while (nextMarking < laneMarkings.size()
                                && laneMarkings.get(nextMarking).time().compareTo(p.start) <= 0) {
                            elements.add(laneMarkings.get(nextMarking++).value());
                        }
                        elements.add(toElement(p, s + 1, lane + 1, start, end));
                    }
                    while (nextMarking < laneMarkings.size()) {
                        elements.add(laneMarkings.get(nextMarking++).value());
                    }
                }
            }
            mb.elements(elements);

            Barline closing = closingBarline(end, m == measureCount - 1);
            if (closing != null) {
                mb.barline(closing);
            }
            if (opensRepeat(start)) {
                mb.leadingRepeatStart(true);
            }
            String ending = endingAt(start);
            if (ending != null) {
                mb.ending(ending);
            }
            if (m > 0 && global.breaks.contains(start)) {
                mb.forceSystemBreak(true);
            }
            part.addMeasure(mb.build());
        }
        return part.build();
    }

    private static List<Timed<MusicElement>> sortedMarkings(List<Timed<MusicElement>> markings) {
        List<Timed<MusicElement>> sorted = new ArrayList<>(markings);
        sorted.sort((a, b) -> a.time().compareTo(b.time()));
        return sorted;
    }

    /**
     * A staff's events split into lanes: one per voice, plus extra lanes for
     * events that overlap within a voice (simultaneous music in one voice).
     * Same-time, same-length notes in a voice merge into a chord.
     */
    private static List<List<Ev>> lanes(List<Ev> events) {
        Map<Ctx, List<Ev>> byVoice = new LinkedHashMap<>();
        for (Ev ev : events) {
            byVoice.computeIfAbsent(ev.voice, k -> new ArrayList<>()).add(ev);
        }
        List<List<Ev>> lanes = new ArrayList<>();
        for (List<Ev> voiceEvents : byVoice.values()) {
            List<Ev> sorted = new ArrayList<>(voiceEvents);
            sorted.sort((a, b) -> a.start.compareTo(b.start));
            List<List<Ev>> voiceLanes = new ArrayList<>();
            for (Ev ev : sorted) {
                boolean placed = false;
                for (List<Ev> lane : voiceLanes) {
                    Ev last = lane.get(lane.size() - 1);
                    if (last.start.equals(ev.start) && last.length.equals(ev.length) && last.isNote() && ev.isNote()) {
                        last.absorb(ev, false);
                        placed = true;
                        break;
                    }
                    if (last.end().compareTo(ev.start) <= 0) {
                        lane.add(ev);
                        placed = true;
                        break;
                    }
                }
                if (!placed) {
                    List<Ev> lane = new ArrayList<>();
                    lane.add(ev);
                    voiceLanes.add(lane);
                }
            }
            lanes.addAll(voiceLanes);
        }
        return lanes;
    }

    /** Cut an event at bar lines, and each cut into notatable values. */
    private List<Piece> piecesOf(Ev ev) {
        List<Piece> out = new ArrayList<>();
        Fraction start = ev.start;
        Fraction end = ev.end();
        if (!end.isPositive() || !ev.length.isPositive()) {
            return out;
        }
        int m = measureIndexAt(start);
        while (start.compareTo(end) < 0 && m < bars.size() - 1) {
            Fraction barEnd = bars.get(m + 1);
            Fraction segEnd = Fraction.min(end, barEnd);
            Fraction segLength = segEnd.minus(start);
            if (segLength.isPositive()) {
                Fraction measureStart = bars.get(m);
                boolean wholeEvent = start.equals(ev.start) && segEnd.equals(end);
                if (ev.kind == Ev.Kind.HIDDEN) {
                    out.add(piece(ev, start, segLength, NoteType.fromQuarterValue(segLength.toDouble()), 0, true));
                } else if (ev.kind == Ev.Kind.MEASURE_REST && start.equals(measureStart) && segEnd.equals(barEnd)) {
                    Piece p = piece(ev, start, segLength, NoteType.WHOLE, 0, false);
                    p.measureRest = true;
                    out.add(p);
                } else if (wholeEvent && !ev.retype && ev.kind != Ev.Kind.MEASURE_REST) {
                    out.add(piece(ev, start, segLength, ev.type, ev.dots, false));
                } else {
                    // Undo the tuplet scaling to find written values, then scale each back.
                    Fraction ratio = Fraction.of(ev.actualNotes, ev.normalNotes);
                    Fraction written = LilyPondParser.multiply(segLength, ratio);
                    Fraction cursor = start;
                    for (Fraction[] value : decompose(written)) {
                        Fraction len = LilyPondParser.multiply(value[0], Fraction.of(ev.normalNotes, ev.actualNotes));
                        NoteType type = NoteType.values()[(int) value[1].numerator()];
                        out.add(piece(ev, cursor, len, type, (int) value[2].numerator(), false));
                        cursor = cursor.plus(len);
                    }
                }
            }
            start = segEnd;
            m++;
        }
        if (!out.isEmpty()) {
            out.get(0).first = true;
            out.get(out.size() - 1).last = true;
        }
        return out;
    }

    private static Piece piece(Ev ev, Fraction start, Fraction length, NoteType type, int dots, boolean invisible) {
        Piece p = new Piece();
        p.ev = ev;
        p.start = start;
        p.length = length;
        p.type = type;
        p.dots = dots;
        p.invisible = invisible;
        return p;
    }

    /**
     * Greedy split of a written length into note values with up to two dots:
     * {length, type ordinal, dots} triples.
     */
    private static List<Fraction[]> decompose(Fraction length) {
        List<Fraction[]> out = new ArrayList<>();
        Fraction remaining = length;
        while (remaining.isPositive() && out.size() < 16) {
            Fraction best = null;
            int bestType = -1;
            int bestDots = 0;
            for (NoteType type : NoteType.values()) {
                for (int dots = 0; dots <= 2; dots++) {
                    Fraction len = LilyPondParser.lengthOf(type, dots);
                    if (len.compareTo(remaining) <= 0 && (best == null || len.compareTo(best) > 0)) {
                        best = len;
                        bestType = type.ordinal();
                        bestDots = dots;
                    }
                }
            }
            if (best == null) {
                out.add(new Fraction[]{remaining, Fraction.of(NoteType.HUNDRED_TWENTY_EIGHTH.ordinal()), Fraction.ZERO});
                break;
            }
            out.add(new Fraction[]{best, Fraction.of(bestType), Fraction.of(bestDots)});
            remaining = remaining.minus(best);
        }
        return out;
    }

    /** Fill a voice's gaps in a measure with invisible rests. */
    private void fillGaps(List<Piece> measurePieces, int m, boolean firstLane) {
        Fraction start = bars.get(m);
        Fraction end = bars.get(m + 1);
        if (measurePieces.isEmpty()) {
            if (firstLane) {
                measurePieces.add(gap(start, end.minus(start)));
            }
            return;
        }
        measurePieces.sort((a, b) -> a.start.compareTo(b.start));
        List<Piece> filled = new ArrayList<>();
        Fraction cursor = start;
        for (Piece p : measurePieces) {
            if (p.start.compareTo(cursor) > 0) {
                filled.add(gap(cursor, p.start.minus(cursor)));
            }
            filled.add(p);
            cursor = Fraction.max(cursor, p.end());
        }
        if (cursor.compareTo(end) < 0) {
            filled.add(gap(cursor, end.minus(cursor)));
        }
        measurePieces.clear();
        measurePieces.addAll(filled);
    }

    private static Piece gap(Fraction start, Fraction length) {
        Piece p = new Piece();
        p.start = start;
        p.length = length;
        p.type = NoteType.fromQuarterValue(length.toDouble());
        p.invisible = true;
        p.first = true;
        p.last = true;
        return p;
    }

    // ----- accidentals and beams -----------------------------------------------------------------

    /**
     * LilyPond's default accidental rule: an accidental is shown when a
     * note's alteration differs from what the key signature, or an earlier
     * note on the same staff line in this measure, implies.
     */
    private void assignAccidentals(Ctx staff, List<List<List<Piece>>> lanes, int m) {
        List<Piece> notes = new ArrayList<>();
        for (List<List<Piece>> lane : lanes) {
            for (Piece p : lane.get(m)) {
                if (p.isNote() && !p.invisible) {
                    notes.add(p);
                }
            }
        }
        notes.sort((a, b) -> a.start.compareTo(b.start));
        KeySignature key = keyAt(staff, bars.get(m));
        Map<Integer, Integer> state = new HashMap<>();
        for (Piece p : notes) {
            Ev ev = p.ev;
            p.accidentals = new Accidental[ev.pitches.size()];
            for (int i = 0; i < ev.pitches.size(); i++) {
                Pitch pitch = ev.pitches.get(i);
                boolean continuation = !p.first || (i < ev.tieStop.length && ev.tieStop[i]);
                if (continuation) {
                    continue;
                }
                int line = pitch.diatonicStepNumber();
                int expected = state.getOrDefault(line, keyAlter(pitch.step(), key));
                boolean forced = i < ev.forcedAccidental.size() && ev.forcedAccidental.get(i);
                if (pitch.alter() != expected || forced) {
                    p.accidentals[i] = Accidental.fromAlter(pitch.alter());
                    state.put(line, pitch.alter());
                }
            }
        }
    }

    private static int keyAlter(Step step, KeySignature key) {
        int fifths = key.fifths();
        Step[] sharps = {Step.F, Step.C, Step.G, Step.D, Step.A, Step.E, Step.B};
        Step[] flats = {Step.B, Step.E, Step.A, Step.D, Step.G, Step.C, Step.F};
        for (int i = 0; i < Math.abs(fifths) && i < 7; i++) {
            if ((fifths > 0 ? sharps : flats)[i] == step) {
                return fifths > 0 ? 1 : -1;
            }
        }
        return 0;
    }

    private static boolean beamable(Piece p) {
        return p.isNote() && !p.invisible && !p.measureRest
                && p.type.ordinal() >= NoteType.EIGHTH.ordinal();
    }

    /** Beam one voice's pieces in one measure: explicit [ ] first, then automatic by beat. */
    private void assignBeams(List<Piece> measurePieces, int m) {
        List<List<Piece>> groups = new ArrayList<>();
        List<Piece> manual = null;
        List<Piece> auto = new ArrayList<>();
        int autoWindow = Integer.MIN_VALUE;
        for (Piece p : measurePieces) {
            Ev ev = p.ev;
            if (manual == null && p.first && ev != null && ev.beamStart && beamable(p)) {
                closeGroup(auto, groups);
                manual = new ArrayList<>();
            }
            if (manual != null) {
                if (beamable(p)) {
                    manual.add(p);
                }
                if (ev != null && p.last && ev.beamEnd) {
                    closeGroup(manual, groups);
                    manual = null;
                }
                continue;
            }
            boolean autoOk = beamable(p) && ev.autoBeam && !inCadenza(p.start);
            int window = autoOk ? beatWindow(p.start, m) : Integer.MIN_VALUE;
            if (!autoOk || window != autoWindow) {
                closeGroup(auto, groups);
                autoWindow = window;
            }
            if (autoOk) {
                auto.add(p);
            }
        }
        closeGroup(auto, groups);
        if (manual != null) {
            closeGroup(manual, groups);
        }
        mergeEighthGroups(groups, m);
        for (List<Piece> group : groups) {
            applyBeams(group);
        }
    }

    private static void closeGroup(List<Piece> group, List<List<Piece>> groups) {
        if (group.size() >= 2) {
            groups.add(new ArrayList<>(group));
        }
        group.clear();
    }

    /** The beat-group index of a moment within its measure, for automatic beaming. */
    private int beatWindow(Fraction t, int m) {
        Fraction offset = metricOffset(t, m);
        TimeSignature ts = timeAt(bars.get(m));
        Fraction[] structure = beatStructure(ts);
        Fraction cursor = Fraction.ZERO;
        int index = 0;
        while (index < 512) {
            Fraction len = structure[index % structure.length];
            cursor = cursor.plus(len);
            if (offset.compareTo(cursor) < 0) {
                return index;
            }
            index++;
        }
        return index;
    }

    /** Position within the (metric) measure: a pickup measure starts part-way through. */
    private Fraction metricOffset(Fraction t, int m) {
        Fraction start = bars.get(m);
        Fraction end = bars.get(m + 1);
        Fraction full = measureLength(timeAt(start));
        Fraction actual = end.minus(start);
        Fraction offset = t.minus(start);
        if (m == 0 && actual.compareTo(full) < 0 && global.partials.containsKey(Fraction.ZERO)) {
            offset = offset.plus(full.minus(actual));
        }
        return offset;
    }

    /** Beat lengths (in quarters) used to group beams, following LilyPond's defaults. */
    private static Fraction[] beatStructure(TimeSignature ts) {
        int beats = ts == null ? 4 : ts.beats();
        int beatType = ts == null ? 4 : ts.beatType();
        Fraction unit = Fraction.of(4, beatType);
        if (beatType >= 8 && beats % 3 == 0 && beats > 3) {
            return new Fraction[]{unit.times(3)};
        }
        if (beatType >= 8 && beats == 5) {
            return new Fraction[]{unit.times(3), unit.times(2)};
        }
        if (beatType >= 8 && beats == 7) {
            return new Fraction[]{unit.times(2), unit.times(2), unit.times(3)};
        }
        if (beatType >= 8) {
            return new Fraction[]{unit.times(beats <= 3 ? beats : 2)};
        }
        return new Fraction[]{unit};
    }

    /**
     * LilyPond beams plain eighths across beats: in 4/4 and 2/2 by half bar,
     * in 3/4 and 2/4 by whole bar. Adjacent beat groups of only eighths merge.
     */
    private void mergeEighthGroups(List<List<Piece>> groups, int m) {
        TimeSignature ts = timeAt(bars.get(m));
        int beats = ts == null ? 4 : ts.beats();
        int beatType = ts == null ? 4 : ts.beatType();
        Fraction span;
        if ((beats == 4 && beatType == 4) || (beats == 2 && beatType == 2)) {
            span = Fraction.of(2);
        } else if ((beats == 3 || beats == 2) && beatType == 4) {
            span = Fraction.of(beats);
        } else {
            return;
        }
        for (int i = 0; i + 1 < groups.size(); ) {
            List<Piece> a = groups.get(i);
            List<Piece> b = groups.get(i + 1);
            Piece lastA = a.get(a.size() - 1);
            Piece firstB = b.get(0);
            boolean contiguous = lastA.end().equals(firstB.start);
            boolean plainEighths = allPlainEighths(a) && allPlainEighths(b);
            Fraction startA = metricOffset(a.get(0).start, m);
            Fraction endB = metricOffset(b.get(b.size() - 1).end(), m);
            long spanIndexA = floorDiv(startA, span);
            boolean sameSpan = spanIndexA == floorDiv(endB.minus(Fraction.of(1, 1000)), span);
            if (contiguous && plainEighths && sameSpan) {
                a.addAll(b);
                groups.remove(i + 1);
            } else {
                i++;
            }
        }
    }

    private static long floorDiv(Fraction value, Fraction unit) {
        return Math.floorDiv(value.numerator() * unit.denominator(), value.denominator() * unit.numerator());
    }

    private static boolean allPlainEighths(List<Piece> group) {
        for (Piece p : group) {
            if (p.type != NoteType.EIGHTH || p.dots != 0 || p.ev.actualNotes != p.ev.normalNotes) {
                return false;
            }
        }
        return true;
    }

    private static void applyBeams(List<Piece> group) {
        int size = group.size();
        int[] levels = new int[size];
        int maxLevel = 0;
        for (int i = 0; i < size; i++) {
            levels[i] = group.get(i).type.ordinal() - NoteType.EIGHTH.ordinal() + 1;
            maxLevel = Math.max(maxLevel, levels[i]);
        }
        List<List<Beam>> perPiece = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            perPiece.add(new ArrayList<>());
        }
        for (int level = 1; level <= maxLevel; level++) {
            int i = 0;
            while (i < size) {
                if (levels[i] < level) {
                    i++;
                    continue;
                }
                int from = i;
                int to = i;
                while (to + 1 < size && levels[to + 1] >= level) {
                    to++;
                }
                if (to > from) {
                    perPiece.get(from).add(new Beam(level, Beam.State.BEGIN));
                    for (int k = from + 1; k < to; k++) {
                        perPiece.get(k).add(new Beam(level, Beam.State.CONTINUE));
                    }
                    perPiece.get(to).add(new Beam(level, Beam.State.END));
                } else {
                    perPiece.get(from).add(new Beam(level, from > 0 ? Beam.State.BACKWARD_HOOK
                            : Beam.State.FORWARD_HOOK));
                }
                i = to + 1;
            }
        }
        for (int i = 0; i < size; i++) {
            group.get(i).beams = perPiece.get(i);
        }
    }

    // ----- model elements ------------------------------------------------------------------------

    private Duration duration(Fraction quarters) {
        long value = Math.round((double) quarters.numerator() * divisions / quarters.denominator());
        return new Duration((int) Math.max(1, value), divisions);
    }

    private MusicElement toElement(Piece p, int staff, int voice, Fraction measureStart, Fraction measureEnd) {
        Ev ev = p.ev;
        if (ev == null || p.invisible || ev.kind != Ev.Kind.NOTE) {
            Rest.Builder rb = Rest.builder()
                    .duration(duration(p.length))
                    .type(p.type)
                    .dots(p.dots)
                    .staff(staff)
                    .voice(voice)
                    .visible(!p.invisible && ev != null);
            if (ev != null && ev.actualNotes != ev.normalNotes && !p.invisible) {
                rb.timeModification(new TimeModification(ev.actualNotes, ev.normalNotes));
                if (p.first) {
                    ev.tuplets.stream().filter(t -> t.type() == Tuplet.Type.START).forEach(rb::addTuplet);
                }
                if (p.last) {
                    ev.tuplets.stream().filter(t -> t.type() == Tuplet.Type.STOP).forEach(rb::addTuplet);
                }
            }
            return rb.build();
        }
        List<Note> notes = new ArrayList<>();
        for (int i = 0; i < ev.pitches.size(); i++) {
            Note.Builder nb = Note.builder()
                    .pitch(ev.pitches.get(i))
                    .duration(duration(p.length))
                    .type(p.type)
                    .dots(p.dots)
                    .staff(staff)
                    .voice(voice)
                    .tieStart(!p.last || (i < ev.tieStart.length && ev.tieStart[i]))
                    .tieStop(!p.first || (i < ev.tieStop.length && ev.tieStop[i]));
            if (p.accidentals != null && i < p.accidentals.length && p.accidentals[i] != null) {
                nb.displayedAccidental(p.accidentals[i]);
            }
            if (ev.stemUp != null) {
                nb.stemUp(ev.stemUp);
            }
            if (i == 0) {
                nb.beams(p.beams);
                if (ev.actualNotes != ev.normalNotes) {
                    nb.timeModification(new TimeModification(ev.actualNotes, ev.normalNotes));
                    if (p.first) {
                        ev.tuplets.stream().filter(t -> t.type() == Tuplet.Type.START).forEach(nb::addTuplet);
                    }
                    if (p.last) {
                        ev.tuplets.stream().filter(t -> t.type() == Tuplet.Type.STOP).forEach(nb::addTuplet);
                    }
                }
                if (p.first) {
                    nb.lyrics(ev.lyrics);
                    nb.articulations(ev.articulations);
                    nb.graceNotes(ev.grace);
                    for (int k = 0; k < ev.slurStarts; k++) {
                        nb.addSlur(new Slur(1, Slur.Type.START, Placement.DEFAULT));
                    }
                }
                if (p.last) {
                    for (int k = 0; k < ev.slurStops; k++) {
                        nb.addSlur(new Slur(1, Slur.Type.STOP, Placement.DEFAULT));
                    }
                }
            }
            notes.add(nb.build());
        }
        return notes.size() == 1 ? notes.get(0) : new Chord(notes);
    }

    // ----- attributes and bar lines --------------------------------------------------------------

    private KeySignature keyAt(Ctx staff, Fraction t) {
        Map.Entry<Fraction, KeySignature> e = staff.keys.floorEntry(t);
        if (e != null) {
            return e.getValue();
        }
        e = global.keys.floorEntry(t);
        return e == null ? KeySignature.cMajor() : e.getValue();
    }

    private static Clef clefAt(Ctx staff, Fraction t) {
        Map.Entry<Fraction, String> e = staff.clefs.floorEntry(t);
        return e == null ? Clef.treble() : clefFromName(e.getValue());
    }

    /** Map a LilyPond clef name (e.g. {@code "treble"}, {@code "bass_8"}) to a {@link Clef}. */
    static Clef clefFromName(String name) {
        if (name == null) {
            return Clef.treble();
        }
        String full = name.trim().toLowerCase(Locale.ROOT);
        java.util.regex.Matcher octave = java.util.regex.Pattern.compile("([_^])\\(?(8|15)\\)?$").matcher(full);
        int octaveChange = 0;
        if (octave.find()) {
            octaveChange = (octave.group(2).equals("15") ? 2 : 1) * (octave.group(1).equals("_") ? -1 : 1);
        }
        String lc = octave.replaceAll("");
        Clef named = switch (lc) {
            case "treble", "violin", "g", "g2", "gg" -> Clef.treble();
            case "french" -> new Clef(ClefSign.G, 1);
            case "soprano" -> new Clef(ClefSign.C, 1);
            case "mezzosoprano" -> new Clef(ClefSign.C, 2);
            case "alto", "c" -> new Clef(ClefSign.C, 3);
            case "tenor" -> new Clef(ClefSign.C, 4);
            case "baritone" -> new Clef(ClefSign.C, 5);
            case "varbaritone" -> new Clef(ClefSign.F, 3);
            case "bass", "f" -> Clef.bass();
            case "subbass" -> new Clef(ClefSign.F, 5);
            case "percussion" -> new Clef(ClefSign.PERCUSSION, 3);
            case "tab", "moderntab" -> new Clef(ClefSign.TAB, 3);
            default -> null;
        };
        if (named != null) {
            return octaveChange == 0 ? named : new Clef(named.sign(), named.line(), octaveChange);
        }
        // Ancient clefs: "vaticana-do3", "mensural-c2", "petrucci-f3", ...
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(do|fa|sol|c|f|g)(\\d)").matcher(lc);
        if (matcher.find()) {
            int line = Integer.parseInt(matcher.group(2));
            ClefSign sign = switch (matcher.group(1)) {
                case "do", "c" -> ClefSign.C;
                case "fa", "f" -> ClefSign.F;
                default -> ClefSign.G;
            };
            return new Clef(sign, Math.max(1, Math.min(5, line)));
        }
        return Clef.treble();
    }

    private Barline closingBarline(Fraction end, boolean lastMeasure) {
        Barline.Style style = null;
        Barline.Repeat repeat = Barline.Repeat.NONE;
        String bar = global.bars.get(end);
        if (bar != null) {
            switch (bar) {
                case "||" -> style = Barline.Style.DOUBLE;
                case "|.", "|.|" -> style = Barline.Style.FINAL;
                case ".|" -> style = Barline.Style.HEAVY_LIGHT;
                case ":|.", ":|", ":|]", ":.|", ":|.|", ":||" -> {
                    style = Barline.Style.FINAL;
                    repeat = Barline.Repeat.BACKWARD;
                }
                case ":..:", ":|.|:", ":|.:", ":|][|:", "::", ":.|.:" -> {
                    style = Barline.Style.FINAL;
                    repeat = Barline.Repeat.BACKWARD;
                }
                default -> {
                }
            }
        }
        for (RepeatSpan r : global.repeats) {
            if (r.alternatives().isEmpty() ? r.end().equals(end) : endsNonFinalAlternative(r, end)) {
                style = Barline.Style.FINAL;
                repeat = Barline.Repeat.BACKWARD;
            }
        }
        return style == null ? null : new Barline(style, repeat);
    }

    private static boolean endsNonFinalAlternative(RepeatSpan r, Fraction end) {
        List<Fraction[]> alts = r.alternatives();
        for (int i = 0; i < alts.size() - 1; i++) {
            if (alts.get(i)[1].equals(end)) {
                return true;
            }
        }
        return false;
    }

    private boolean opensRepeat(Fraction start) {
        String bar = global.bars.get(start);
        if (bar != null && (bar.endsWith("|:") || bar.equals(".|:") || bar.equals(":..:") || bar.equals("::")
                || bar.equals(":|.:") || bar.equals(":.|.:") || bar.equals("|:."))) {
            return true;
        }
        for (RepeatSpan r : global.repeats) {
            if (r.start().equals(start) && start.isPositive()) {
                return true;
            }
        }
        return false;
    }

    private String endingAt(Fraction start) {
        for (RepeatSpan r : global.repeats) {
            List<Fraction[]> alts = r.alternatives();
            for (int i = 0; i < alts.size(); i++) {
                Fraction[] span = alts.get(i);
                if (start.compareTo(span[0]) >= 0 && start.compareTo(span[1]) < 0) {
                    int extra = Math.max(0, r.count() - alts.size());
                    if (i == 0 && extra > 0) {
                        StringBuilder label = new StringBuilder();
                        for (int k = 1; k <= extra + 1; k++) {
                            label.append(k).append(k <= extra ? ", " : ".");
                        }
                        return label.toString();
                    }
                    return (i + 1 + extra) + ".";
                }
            }
        }
        return null;
    }

    private static long lcm(long a, long b) {
        if (a == 0 || b == 0) {
            return Math.max(a, b);
        }
        long g = gcd(a, b);
        long result = a / g * b;
        return result > MAX_DIVISIONS ? a : result;
    }

    private static long gcd(long a, long b) {
        while (b != 0) {
            long t = a % b;
            a = b;
            b = t;
        }
        return Math.abs(a);
    }
}
