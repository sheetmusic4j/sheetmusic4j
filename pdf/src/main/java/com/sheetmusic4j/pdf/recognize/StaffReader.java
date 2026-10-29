package com.sheetmusic4j.pdf.recognize;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.stream.Stream;

import com.sheetmusic4j.core.model.Accidental;
import com.sheetmusic4j.core.model.Attributes;
import com.sheetmusic4j.core.model.Barline;
import com.sheetmusic4j.core.model.Beam;
import com.sheetmusic4j.core.model.Chord;
import com.sheetmusic4j.core.model.Clef;
import com.sheetmusic4j.core.model.ClefSign;
import com.sheetmusic4j.core.model.Direction;
import com.sheetmusic4j.core.model.DirectionType;
import com.sheetmusic4j.core.model.Duration;
import com.sheetmusic4j.core.model.DynamicMark;
import com.sheetmusic4j.core.model.KeySignature;
import com.sheetmusic4j.core.model.Measure;
import com.sheetmusic4j.core.model.MusicElement;
import com.sheetmusic4j.core.model.Note;
import com.sheetmusic4j.core.model.NoteType;
import com.sheetmusic4j.core.model.Pitch;
import com.sheetmusic4j.core.model.Placement;
import com.sheetmusic4j.core.model.Rest;
import com.sheetmusic4j.core.model.Slur;
import com.sheetmusic4j.core.model.Step;
import com.sheetmusic4j.core.model.TimeModification;
import com.sheetmusic4j.core.model.TimeSignature;
import com.sheetmusic4j.core.model.Tuplet;
import com.sheetmusic4j.pdf.extract.PageContent;
import com.sheetmusic4j.pdf.extract.PageContent.Box;
import com.sheetmusic4j.pdf.extract.PageContent.Glyph;
import com.sheetmusic4j.pdf.extract.PageContent.VLine;
import com.sheetmusic4j.pdf.symbol.MusicFonts;
import com.sheetmusic4j.pdf.symbol.Symbol;
import com.sheetmusic4j.pdf.symbol.Symbol.Kind;

/**
 * Reads one staff of one system into measures. Barlines split the staff; inside a measure noteheads become pitches
 * through their height on the staff, stems and flags/beams give the duration, dots and accidentals attach to the
 * nearest head, and heads sharing a stem form a chord.
 *
 * <p>Known limits: no tuplets, ties, slurs, grace notes, ornaments or dynamics; a second voice is only recognised
 * from opposite stem directions when a measure would otherwise be overfull.
 */
public final class StaffReader {

    /** Ticks per quarter note. 1680 divides evenly into 64th notes and into triplet, quintuplet and septuplet parts of them. */
    public static final int DIVISIONS = 1680;

    /** Slur number of a slur that runs from one system into the next, outside the 1-6 used inside a system. */
    private static final int CROSS_SYSTEM_SLUR = 7;

    private static final Step[] STEPS = {Step.C, Step.D, Step.E, Step.F, Step.G, Step.A, Step.B};
    private static final NoteType[] REST_TYPES = {NoteType.WHOLE, NoteType.HALF, NoteType.QUARTER, NoteType.EIGHTH,
            NoteType.SIXTEENTH, NoteType.THIRTY_SECOND, NoteType.SIXTY_FOURTH, NoteType.HUNDRED_TWENTY_EIGHTH};
    private static final NoteType[] FILLED_TYPES = {NoteType.QUARTER, NoteType.EIGHTH, NoteType.SIXTEENTH,
            NoteType.THIRTY_SECOND, NoteType.SIXTY_FOURTH, NoteType.HUNDRED_TWENTY_EIGHTH};

    private final Staff staff;
    private final PageContent page;
    private final PartState state;
    private final double space;
    private final Set<Mark> tieStarts = new HashSet<>();
    private final Set<Mark> tieStops = new HashSet<>();
    private final Map<Mark, List<Slur>> slurs = new HashMap<>();
    private List<Digit> digits = List.of();
    private List<Hairpin> hairpins = List.of();

    public StaffReader(Staff staff, PageContent page, PartState state) {
        this.staff = staff;
        this.page = page;
        this.state = state;
        this.space = staff.space();
    }

    /**
     * Read the measures of this staff.
     *
     * @param marks       the symbols that belong to this staff
     * @param hairpins    the crescendo and diminuendo wedges that belong to this staff
     * @param firstNumber number of the first measure
     */
    List<Measure> read(List<Mark> marks, List<Hairpin> hairpins, int firstNumber, boolean systemBreak) {
        this.hairpins = hairpins.stream().sorted(Comparator.comparingDouble(Hairpin::start)).toList();
        List<Mark> heads = marks.stream().filter(m -> m.symbol().kind() == Kind.HEAD && !isGrace(m)).toList();
        linkCurves(heads);
        digits = digits();
        List<Bar> bars = barlines(heads);
        List<VLine> stems = stems(bars);
        List<Measure> measures = new ArrayList<>();
        for (Span span : spans(bars)) {
            List<Mark> inside = marks.stream().filter(m -> m.cx() > span.from && m.cx() <= span.end)
                    .sorted(Comparator.comparingDouble(Mark::cx)).toList();
            measures.add(readMeasure(firstNumber + measures.size(), inside, stems, measures.isEmpty() && systemBreak,
                    span.closing, span.from, span.end));
        }
        return measures;
    }

    /** The stretch of the staff one measure takes: from the previous bar to the one that closes it. */
    private record Span(double from, double end, Barline closing) {
    }

    /** One span per bar, and one for what follows the last bar unless that is only white space. */
    private List<Span> spans(List<Bar> bars) {
        List<Span> spans = new ArrayList<>();
        double start = staff.x0();
        for (Bar bar : bars) {
            spans.add(new Span(start, bar.first, bar.style));
            start = bar.last;
        }
        if (staff.x1() + 2 - start >= 3 * space) {
            spans.add(new Span(start, staff.x1() + 2, null));
        }
        return spans;
    }

    // ---- barlines -----------------------------------------------------------------------------------------------

    private record Bar(double first, double last, Barline style) {
    }

    private List<Bar> barlines(List<Mark> heads) {
        List<List<VLine>> clusters = new ArrayList<>();
        for (VLine v : withoutOutlines(barCandidates(heads))) {
            List<VLine> last = clusters.isEmpty() ? null : clusters.get(clusters.size() - 1);
            if (last != null && v.x() - last.get(last.size() - 1).x() <= 1.2 * space) {
                last.add(v);
            } else {
                clusters.add(new ArrayList<>(List.of(v)));
            }
        }
        return clusters.stream().map(StaffReader::barOf).toList();
    }

    /** Vertical lines that run the height of the staff and are not stems. */
    private List<VLine> barCandidates(List<Mark> heads) {
        double tol = space * 0.3;
        return page.vlines().stream()
                .filter(v -> v.y0() <= staff.top() + tol && v.y1() >= staff.bottom() - tol && v.x() > staff.x0() + space
                        && v.x() <= staff.x1() + 2 && !isStem(v, heads))
                .toList();
    }

    /** One bar from its lines: a thin line is a regular bar, a thin and a thick one the final bar, two thin a double. */
    private static Bar barOf(List<VLine> cluster) {
        VLine first = cluster.get(0);
        VLine last = cluster.get(cluster.size() - 1);
        Barline.Style style = Barline.Style.REGULAR;
        if (cluster.size() > 1) {
            style = last.thickness() > 2 * first.thickness() ? Barline.Style.FINAL : Barline.Style.DOUBLE;
        }
        return new Bar(first.x(), last.x(), new Barline(style, Barline.Repeat.NONE));
    }

    /**
     * A bar drawn as a filled rectangle with an outline stroke shows up as several lines; keep the widest and drop
     * those that lie within its body. The result is sorted by x.
     */
    private static List<VLine> withoutOutlines(List<VLine> lines) {
        List<VLine> kept = new ArrayList<>();
        lines.stream().sorted(Comparator.comparingDouble(VLine::thickness).reversed()).forEach(v -> {
            if (kept.stream().noneMatch(k -> Math.abs(v.x() - k.x()) <= k.thickness() / 2 + 0.35)) {
                kept.add(v);
            }
        });
        kept.sort(Comparator.comparingDouble(VLine::x));
        return kept;
    }

    /** A line that ends at a notehead of this staff is a stem, not a barline. */
    private boolean isStem(VLine v, List<Mark> heads) {
        return heads.stream().anyMatch(head -> {
            boolean atEnd = Math.abs(head.cy() - v.y0()) < 0.9 * space || Math.abs(head.cy() - v.y1()) < 0.9 * space;
            return atEnd && v.x() >= head.left() - 0.2 * space && v.x() <= head.right() + 0.2 * space;
        });
    }

    private List<VLine> stems(List<Bar> bars) {
        return page.vlines().stream()
                .filter(v -> v.y1() - v.y0() >= 1.5 * space && v.y0() < staff.bottom() + 10 * space
                        && v.y1() > staff.top() - 10 * space && v.x() >= staff.x0() && v.x() <= staff.x1())
                .filter(v -> !(v.y0() <= staff.top() + 0.3 * space && v.y1() >= staff.bottom() - 0.3 * space
                        && bars.stream().anyMatch(b -> Math.abs(b.first - v.x()) < 0.4 || Math.abs(b.last - v.x()) < 0.4)))
                .toList();
    }

    private boolean isGrace(Mark head) {
        return head.width() > 0 && head.width() < 1.05 * space;
    }

    // ---- one measure --------------------------------------------------------------------------------------------

    /** The marks of one measure sorted by what they are. */
    private record Contents(List<Mark> heads, List<Mark> rests, List<Mark> accidentals, List<Mark> dots,
                            List<Mark> flags, List<Mark> clefs, List<Mark> timeMarks) {

        static Contents of(List<Mark> marks, Predicate<Mark> grace) {
            return new Contents(
                    ofKind(marks, Kind.HEAD).stream().filter(grace.negate()).toList(),
                    ofKind(marks, Kind.REST), ofKind(marks, Kind.ACCIDENTAL), ofKind(marks, Kind.DOT),
                    ofKind(marks, Kind.FLAG), ofKind(marks, Kind.CLEF),
                    ofKind(marks, Kind.TIME_COMMON, Kind.TIME_CUT, Kind.DIGIT));
        }

        private static List<Mark> ofKind(List<Mark> marks, Kind... kinds) {
            Set<Kind> wanted = Set.of(kinds);
            return marks.stream().filter(m -> wanted.contains(m.symbol().kind())).toList();
        }

        /** Left edge of the first head or rest; everything before it is clef, key or time signature. */
        double contentStart() {
            return Stream.concat(heads.stream(), rests.stream()).mapToDouble(Mark::left).min().orElse(Double.MAX_VALUE);
        }

        /** Left edge of the first time signature mark before the content, or {@code Double.MAX_VALUE}. */
        double timeStart(double contentStart) {
            return timeMarks.stream().filter(m -> m.cx() < contentStart).mapToDouble(Mark::left).min()
                    .orElse(Double.MAX_VALUE);
        }
    }

    private Measure readMeasure(int number, List<Mark> marks, List<VLine> stems, boolean systemBreak, Barline barline,
                                double from, double to) {
        Contents contents = Contents.of(marks, this::isGrace);
        double contentStart = contents.contentStart();
        double timeStart = contents.timeStart(contentStart);

        Attributes.Builder attributes = Attributes.builder();
        List<Mark> clefChanges = new ArrayList<>();
        Clef startClef = startClef(contents, contentStart, clefChanges);
        boolean changed = showClef(startClef, attributes);
        // a clef printed after the first note, often just before the barline, is the clef of the next measure
        state.clef = clefChanges.isEmpty() ? startClef : clefOf(clefChanges.get(clefChanges.size() - 1));
        changed |= readTime(contents, contentStart, attributes);
        changed |= readKey(contents, systemBreak || !state.started, Math.min(contentStart, timeStart),
                timeStart != Double.MAX_VALUE, attributes);
        if (!state.started) {
            attributes.divisions(DIVISIONS);
            changed = true;
            state.started = true;
        }

        KeySignature key = new KeySignature(state.fifths == null ? 0 : state.fifths);
        int measureTicks = state.time.beats() * DIVISIONS * 4 / state.time.beatType();
        List<Item> items = new MeasureNotes(contents, key, clefChanges, startClef).items(stems);
        items.addAll(directions(marks, from, to, items.stream().mapToDouble(i -> i.x).toArray()));
        List<Digit> tupletDigits = digits.stream().filter(d -> d.cx() > contentStart).toList();

        Measure.Builder measure = Measure.builder(number);
        if (changed) {
            measure.attributes(attributes.build());
        }
        layOut(items, measureTicks, tupletDigits).forEach(measure::addElement);
        if (barline != null && barline.style() != Barline.Style.REGULAR) {
            measure.barline(barline);
        }
        measure.forceSystemBreak(systemBreak);
        return measure.build();
    }

    /**
     * The clef the measure starts with: one printed before the first note, else the one in force. Clefs after the
     * first note are collected in {@code changes}.
     */
    private Clef startClef(Contents contents, double contentStart, List<Mark> changes) {
        Clef clef = state.clef;
        for (Mark m : contents.clefs()) {
            if (m.cx() < contentStart) {
                clef = clefOf(m);
            } else {
                changes.add(m);
            }
        }
        return clef == null ? Clef.treble() : clef;
    }

    /**
     * Announce the clef in the measure's attributes when it differs from the one the measures so far announced.
     *
     * @return whether it was announced
     */
    private boolean showClef(Clef clef, Attributes.Builder attributes) {
        boolean changed = !clef.equals(state.shownClef);
        if (changed) {
            attributes.clef(clef);
        }
        state.shownClef = clef;
        return changed;
    }

    /** The time signature carries over from the previous system when the measure shows none; 4/4 to begin with. */
    private boolean readTime(Contents contents, double contentStart, Attributes.Builder attributes) {
        TimeSignature shown = timeSignature(contents.timeMarks(), contentStart);
        TimeSignature next = shown != null ? shown : state.time;
        if (next == null) {
            next = TimeSignature.fourFour();
        }
        boolean changed = !next.equals(state.time);
        if (changed) {
            attributes.timeSignature(next);
        }
        state.time = next;
        return changed;
    }

    /**
     * The key signature is the accidentals before the first note (or time signature) that no note owns. Systems
     * repeat it, so it is read at every system start; inside a system only when such accidentals show up.
     */
    private boolean readKey(Contents contents, boolean systemStart, double limit, boolean hasTime,
                            Attributes.Builder attributes) {
        boolean candidate = systemStart || hasKeyChange(contents.accidentals(), contents.heads(), limit);
        if (!candidate) {
            return false;
        }
        int fifths = keySignature(contents.accidentals(), contents.heads(), limit, hasTime);
        if (state.fifths != null && state.fifths == fifths) {
            return false;
        }
        attributes.keySignature(new KeySignature(fifths));
        state.fifths = fifths;
        return true;
    }

    private Clef clefOf(Mark m) {
        int line = Math.clamp(staff.stepAt(m.oy()) / 2L + 1, 1, 5);
        return switch (m.symbol().value()) {
            case Symbol.F_CLEF -> new Clef(ClefSign.F, line);
            case Symbol.C_CLEF -> new Clef(ClefSign.C, line);
            default -> new Clef(ClefSign.G, line);
        };
    }

    private TimeSignature timeSignature(List<Mark> marks, double contentStart) {
        StringBuilder top = new StringBuilder();
        StringBuilder bottom = new StringBuilder();
        List<Mark> inStaff = marks.stream()
                .filter(m -> m.cx() < contentStart && m.cy() >= staff.top() - 0.5 * space
                        && m.cy() <= staff.bottom() + 0.5 * space)
                .toList();
        for (Mark m : inStaff) {
            switch (m.symbol().kind()) {
                case TIME_COMMON -> {
                    return new TimeSignature(4, 4);
                }
                case TIME_CUT -> {
                    return new TimeSignature(2, 2);
                }
                case DIGIT -> (m.cy() < staff.midY() ? top : bottom).append(m.symbol().value());
                default -> {
                    // only the marks above are part of a time signature
                }
            }
        }
        return numbersToTime(top, bottom);
    }

    private static TimeSignature numbersToTime(StringBuilder top, StringBuilder bottom) {
        if (top.isEmpty() || bottom.isEmpty()) {
            return null;
        }
        try {
            int beats = Integer.parseInt(top.toString());
            int type = Integer.parseInt(bottom.toString());
            return beats > 0 && type > 0 ? new TimeSignature(beats, type) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private int keySignature(List<Mark> accidentals, List<Mark> heads, double limit, boolean hasTime) {
        double maxGap = hasTime ? Double.MAX_VALUE : 2.6 * space;
        List<Mark> keyMarks = accidentals.stream()
                .filter(a -> a.cx() < limit && !isNoteAccidental(a, heads, maxGap))
                .toList();
        long sharps = keyMarks.stream().filter(a -> a.symbol().value() > 0).count();
        long flats = keyMarks.stream().filter(a -> a.symbol().value() < 0).count();
        return (int) (sharps >= flats ? sharps : -flats);
    }

    /** Accidentals before the first note that belong to no note: a key signature change inside a system. */
    private boolean hasKeyChange(List<Mark> accidentals, List<Mark> heads, double limit) {
        return accidentals.stream().anyMatch(a -> a.cx() < limit && !isNoteAccidental(a, heads, 2.6 * space));
    }

    /** Whether an accidental has a head of its own, no further than {@code maxGap} to its right. */
    private boolean isNoteAccidental(Mark accidental, List<Mark> heads, double maxGap) {
        Mark owner = owner(accidental, heads);
        return owner != null && owner.left() - accidental.right() < maxGap;
    }

    /** The head an accidental belongs to: same step, to its right, close by. */
    private Mark owner(Mark accidental, List<Mark> heads) {
        int step = staff.stepAt(accidental.oy());
        Mark best = null;
        for (Mark head : heads) {
            double gap = head.left() - accidental.right();
            if (gap >= -0.3 * space && gap <= 4.5 * space && staff.stepAt(head.cy()) == step
                    && (best == null || head.left() < best.left())) {
                best = head;
            }
        }
        return best;
    }

    // ---- notes and rests ----------------------------------------------------------------------------------------

    /** A note, chord or rest in time order, with what is needed to place it in a voice and a tuplet. */
    private static final class Item {
        final double x;
        final Boolean stemUp;
        final int step;
        final boolean rest;
        final boolean direction;
        final int baseTicks;
        final BiFunction<Item, Integer, MusicElement> maker;
        int actual;
        int normal;
        Tuplet.Type edge;

        Item(double x, Boolean stemUp, int step, boolean rest, int baseTicks,
             BiFunction<Item, Integer, MusicElement> maker) {
            this.x = x;
            this.stemUp = stemUp;
            this.step = step;
            this.rest = rest;
            this.direction = false;
            this.baseTicks = baseTicks;
            this.maker = maker;
        }

        /** A marking without duration, such as a dynamic, placed by its x. */
        Item(double x, MusicElement direction) {
            this.x = x;
            this.stemUp = null;
            this.step = 0;
            this.rest = false;
            this.direction = true;
            this.baseTicks = 0;
            this.maker = (self, voice) -> direction;
        }

        int ticks() {
            return actual > 0 ? (int) Math.round(baseTicks * (double) normal / actual) : baseTicks;
        }

        void clearTuplet() {
            actual = 0;
            normal = 0;
            edge = null;
        }

        MusicElement element(int voice) {
            return maker.apply(this, voice);
        }

        TimeModification timeModification() {
            return actual > 0 ? new TimeModification(actual, normal) : null;
        }

        Tuplet tuplet() {
            return actual > 0 && edge != null ? new Tuplet(1, edge, false, actual, normal, Placement.DEFAULT) : null;
        }
    }

    /** Heads that share a stem (or a column when stemless), with the beams that hold the stem. */
    private static final class Group {
        final List<Mark> heads;
        final VLine stem;
        final boolean up;
        List<Box> beams = List.of();
        final List<Beam> beamStates = new ArrayList<>();

        Group(List<Mark> heads, VLine stem, boolean up) {
            this.heads = heads;
            this.stem = stem;
            this.up = up;
        }

        double x() {
            return heads.get(0).cx();
        }
    }

    /**
     * Turns the heads and rests of one measure into items. The accidentals of the bar carry from note to note, so
     * the notes must be made in order.
     */
    private final class MeasureNotes {
        private final Contents contents;
        private final KeySignature key;
        private final List<Mark> clefChanges;
        private final Clef startClef;
        private final List<Mark> durationDots;
        private final Map<Mark, Mark> owners = new HashMap<>();
        private final Map<Integer, Integer> carried = new HashMap<>();

        MeasureNotes(Contents contents, KeySignature key, List<Mark> clefChanges, Clef startClef) {
            this.contents = contents;
            this.key = key;
            this.clefChanges = clefChanges;
            this.startClef = startClef;
            // a dot over or under a head is a staccato mark, a duration dot stands to the right of it
            this.durationDots = contents.dots().stream()
                    .filter(d -> contents.heads().stream().noneMatch(h -> Math.abs(d.cx() - h.cx()) < 0.5 * h.width()))
                    .toList();
            for (Mark accidental : contents.accidentals()) {
                Mark head = owner(accidental, contents.heads());
                if (head != null) {
                    owners.merge(head, accidental, (a, b) -> a.cx() > b.cx() ? a : b);
                }
            }
        }

        List<Item> items(List<VLine> stems) {
            List<Group> groups = groups(contents.heads(), stems);
            assignBeams(groups);
            List<Item> items = new ArrayList<>();
            for (Group group : groups) {
                items.add(noteItem(group));
            }
            for (Mark rest : contents.rests()) {
                items.add(restItem(rest));
            }
            items.sort(Comparator.comparingDouble((Item i) -> i.x));
            return items;
        }

        private Item noteItem(Group group) {
            Mark lead = group.heads.get(0);
            NoteType type = noteType(group);
            int dotCount = 0;
            for (Mark head : group.heads) {
                dotCount = Math.max(dotCount, dotsAfter(head.right(), staff.stepAt(head.cy()), 1, durationDots));
            }
            List<Note.Builder> builders = new ArrayList<>();
            for (Mark head : group.heads.stream().sorted(Comparator.comparingDouble(Mark::cx)).toList()) {
                builders.add(noteBuilder(group, head, type, dotCount));
            }
            Boolean stemUp = group.stem == null ? null : group.up;
            return new Item(lead.cx(), stemUp, staff.stepAt(lead.cy()), false, ticks(type, dotCount),
                    (self, voice) -> chordOf(builders, self, voice));
        }

        private NoteType noteType(Group group) {
            return switch (group.heads.get(0).symbol().value()) {
                case Symbol.WHOLE -> NoteType.WHOLE;
                case Symbol.HALF -> NoteType.HALF;
                default -> {
                    int flags = flagsFor(group.stem, group.up, contents.flags());
                    yield FILLED_TYPES[Math.clamp(Math.max(flags, group.beams.size()), 0, FILLED_TYPES.length - 1)];
                }
            };
        }

        private Note.Builder noteBuilder(Group group, Mark head, NoteType type, int dotCount) {
            int diatonic = diatonic(clefAt(head.cx()), staff.stepAt(head.cy()));
            Step letter = STEPS[Math.floorMod(diatonic, 7)];
            Mark accidental = owners.get(head);
            int alter;
            if (accidental != null) {
                alter = accidental.symbol().value();
                carried.put(diatonic, alter);
            } else {
                alter = carried.getOrDefault(diatonic, key.alter(letter));
            }
            Note.Builder builder = Note.builder()
                    .pitch(new Pitch(letter, Math.floorDiv(diatonic, 7), alter))
                    .type(type)
                    .dots(dotCount)
                    .tieStart(tieStarts.contains(head))
                    .tieStop(tieStops.contains(head))
                    .staff(1);
            if (accidental != null) {
                builder.displayedAccidental(Accidental.fromAlter(alter));
            }
            if (group.stem != null) {
                builder.stemUp(group.up);
            }
            group.beamStates.forEach(builder::addBeam);
            slurs.getOrDefault(head, List.of()).forEach(builder::addSlur);
            return builder;
        }

        private Clef clefAt(double x) {
            Clef clef = startClef;
            for (Mark m : clefChanges) {
                if (m.cx() < x) {
                    clef = clefOf(m);
                }
            }
            return clef;
        }

        private Item restItem(Mark rest) {
            NoteType type = REST_TYPES[Math.min(rest.symbol().value(), REST_TYPES.length - 1)];
            int dotCount = dotsAfter(rest.right(), staff.stepAt(rest.cy()), 2, durationDots);
            return new Item(rest.cx(), null, staff.stepAt(rest.cy()), true, ticks(type, dotCount), (self, voice) -> {
                Rest.Builder builder = Rest.builder().duration(new Duration(self.ticks(), DIVISIONS)).type(type)
                        .dots(dotCount).staff(1).voice(voice);
                if (self.timeModification() != null) {
                    builder.timeModification(self.timeModification());
                }
                if (self.tuplet() != null) {
                    builder.addTuplet(self.tuplet());
                }
                return builder.build();
            });
        }

        /** Group heads into chords: by shared stem, else by column. */
        private List<Group> groups(List<Mark> heads, List<VLine> stems) {
            List<Group> groups = new ArrayList<>();
            Map<VLine, Group> byStem = new HashMap<>();
            List<Group> stemless = new ArrayList<>();
            for (Mark head : heads) {
                VLine stem = stemFor(head, stems);
                Group group = stem != null ? byStem.get(stem) : columnGroup(stemless, head);
                if (group == null) {
                    group = new Group(new ArrayList<>(), stem, stem != null && isUp(stem, head));
                    groups.add(group);
                    if (stem != null) {
                        byStem.put(stem, group);
                    } else {
                        stemless.add(group);
                    }
                }
                group.heads.add(head);
            }
            for (Group group : groups) {
                if (group.stem != null && group.heads.get(0).symbol().value() == Symbol.FILLED) {
                    group.beams = beamsAt(group.stem, group.up);
                }
            }
            return groups;
        }

        private VLine stemFor(Mark head, List<VLine> stems) {
            VLine best = null;
            double bestDistance = Double.MAX_VALUE;
            for (VLine v : stems) {
                double d = Math.min(Math.abs(v.x() - head.left()), Math.abs(v.x() - head.right()));
                boolean alongStem = head.cy() >= v.y0() - 0.5 * space && head.cy() <= v.y1() + 0.5 * space;
                if (alongStem && d <= 0.4 * space && d < bestDistance) {
                    best = v;
                    bestDistance = d;
                }
            }
            return best;
        }

        private static boolean isUp(VLine stem, Mark head) {
            return Math.abs(head.cy() - stem.y1()) < Math.abs(head.cy() - stem.y0());
        }

        /** Stemless heads (whole notes) stack in a column; a second sits one head width aside. */
        private Group columnGroup(List<Group> groups, Mark head) {
            for (Group group : groups) {
                Mark other = group.heads.get(0);
                double dx = Math.abs(other.cx() - head.cx());
                int dStep = Math.abs(staff.stepAt(other.cy()) - staff.stepAt(head.cy()));
                if (dx < 0.3 * space || (dx < 1.2 * head.width() && dStep <= 1)) {
                    return group;
                }
            }
            return null;
        }

        private int flagsFor(VLine stem, boolean up, List<Mark> flags) {
            if (stem == null) {
                return 0;
            }
            double end = up ? stem.y0() : stem.y1();
            int count = 0;
            for (Mark flag : flags) {
                boolean nearX = Math.abs(flag.left() - stem.x()) < 0.6 * space || Math.abs(flag.glyph().x() - stem.x()) < 0.6 * space;
                boolean nearY = flag.cy() > end - 2.8 * space && flag.cy() < end + 2.8 * space;
                if (nearX && nearY) {
                    count = Math.max(count, flag.symbol().value());
                }
            }
            return count;
        }

        /**
         * The beams a stem is attached to, the outermost (widest) first. A beam level is a filled polygon that covers
         * the stem's x and its tip; partial beams (hooks) are short polygons that only reach the one stem.
         */
        private List<Box> beamsAt(VLine stem, boolean up) {
            double end = up ? stem.y0() : stem.y1();
            List<Box> found = new ArrayList<>();
            for (Box beam : page.beams()) {
                boolean beamShape = beam.width() >= 0.6 * space && beam.height() <= 2.5 * space;
                boolean holdsStem = stem.x() >= beam.left() - 0.3 && stem.x() <= beam.right() + 0.3 && beamAlongStem(beam, end, up);
                if (beamShape && holdsStem && found.stream().noneMatch(b -> same(b, beam))) {
                    found.add(beam);
                }
            }
            found.sort(Comparator.comparingDouble((Box b) -> -Math.round(b.width() * 2) / 2.0)
                    .thenComparingDouble(b -> Math.abs(b.centerY() - end)));
            return found;
        }

        /**
         * Whether a beam lies along the stem near its tip: not beyond the tip, and no further in than a few beam levels.
         * Secondary beams sit inside the primary one, so they do not touch the tip itself.
         */
        private boolean beamAlongStem(Box beam, double end, boolean up) {
            return up ? beam.bottom() >= end - 0.4 * space && beam.top() <= end + 2.2 * space
                    : beam.top() <= end + 0.4 * space && beam.bottom() >= end - 2.2 * space;
        }

        /** Beam states per group and level: the first stem under a polygon begins it, the last ends it. */
        private static void assignBeams(List<Group> groups) {
            for (Group group : groups) {
                for (int level = 1; level <= group.beams.size(); level++) {
                    Box box = group.beams.get(level - 1);
                    List<Group> members = groups.stream()
                            .filter(o -> o.beams.stream().anyMatch(b -> same(b, box)))
                            .sorted(Comparator.comparingDouble(Group::x)).toList();
                    group.beamStates.add(new Beam(level, beamState(group, box, members)));
                }
            }
        }

        private static Beam.State beamState(Group group, Box box, List<Group> members) {
            if (members.size() < 2) {
                return box.centerX() > group.stem.x() ? Beam.State.FORWARD_HOOK : Beam.State.BACKWARD_HOOK;
            }
            if (members.get(0) == group) {
                return Beam.State.BEGIN;
            }
            return members.get(members.size() - 1) == group ? Beam.State.END : Beam.State.CONTINUE;
        }

        /** Dots following a head or rest: the first close to it, each further one close to the one before. */
        private int dotsAfter(double right, int step, int stepTolerance, List<Mark> dots) {
            int count = 0;
            double x = right;
            Mark next = nextDot(x, count, step, stepTolerance, dots);
            while (next != null) {
                count++;
                x = next.cx();
                next = nextDot(x, count, step, stepTolerance, dots);
            }
            return count;
        }

        private Mark nextDot(double x, int count, int step, int stepTolerance, List<Mark> dots) {
            Mark next = null;
            for (Mark dot : dots) {
                double gap = dot.cx() - x;
                if (gap > 0.05 * space && gap < (count == 0 ? 2.6 : 1.4) * space
                        && Math.abs(staff.stepAt(dot.oy()) - step) <= stepTolerance
                        && (next == null || dot.cx() < next.cx())) {
                    next = dot;
                }
            }
            return next;
        }
    }

    /** A single note, or a chord when several heads share the stem. */
    private static MusicElement chordOf(List<Note.Builder> builders, Item item, int voice) {
        List<Note> notes = new ArrayList<>();
        for (Note.Builder builder : builders) {
            builder.voice(voice).duration(new Duration(item.ticks(), DIVISIONS));
            if (item.timeModification() != null) {
                builder.timeModification(item.timeModification());
            }
            if (item.tuplet() != null) {
                builder.addTuplet(item.tuplet());
            }
            notes.add(builder.build());
        }
        notes.sort(Comparator.comparingInt(n -> n.pitch().toMidiNumber()));
        return notes.size() == 1 ? notes.get(0) : new Chord(notes);
    }

    private static int ticks(NoteType type, int dots) {
        return (int) type.quarters(dots).times(DIVISIONS).round();
    }

    /** Diatonic number of the note at a staff step: octave * 7 + index of C..B. */
    private static int diatonic(Clef clef, int step) {
        int base = switch (clef.sign()) {
            case F -> 3 * 7 + 3;
            case C -> 4 * 7;
            default -> 4 * 7 + 4;
        };
        return base + step - 2 * (clef.line() - 1);
    }

    private static boolean same(Box a, Box b) {
        return Math.abs(a.left() - b.left()) < 0.3 && Math.abs(a.right() - b.right()) < 0.3
                && Math.abs(a.top() - b.top()) < 0.3 && Math.abs(a.bottom() - b.bottom()) < 0.3;
    }

    // ---- dynamics and wedges ------------------------------------------------------------------------------------

    private List<Item> directions(List<Mark> marks, double from, double to, double[] noteXs) {
        List<Item> out = new ArrayList<>();
        for (Mark m : marks) {
            if (m.symbol().kind() == Kind.DYNAMIC) {
                out.add(dynamic(DynamicMark.values()[m.symbol().value()], m));
            }
        }
        out.addAll(letterDynamics(marks.stream().filter(m -> m.symbol().kind() == Kind.DYNAMIC_LETTER).toList()));
        for (int i = 0; i < hairpins.size(); i++) {
            // neighbouring wedges can overlap by a few points (<> as one figure), so they alternate between two numbers
            out.addAll(wedge(hairpins.get(i), i % 2 + 1, from, to, noteXs));
        }
        return out;
    }

    /** Dynamics drawn letter by letter ({@code m} + {@code f}): letters that touch form one marking. */
    private List<Item> letterDynamics(List<Mark> letters) {
        List<Item> out = new ArrayList<>();
        StringBuilder text = new StringBuilder();
        Mark first = null;
        Mark previous = null;
        for (Mark m : letters) {
            boolean apart = previous != null && (m.left() - previous.right() > 0.8 * space
                    || Math.abs(m.cy() - previous.cy()) > 0.6 * space);
            if (apart) {
                addLetters(out, text, first);
                text.setLength(0);
                first = null;
            }
            first = first == null ? m : first;
            text.append((char) m.symbol().value());
            previous = m;
        }
        addLetters(out, text, first);
        return out;
    }

    private void addLetters(List<Item> out, StringBuilder text, Mark first) {
        DynamicMark mark = first == null ? null : DynamicMark.fromXml(text.toString());
        if (mark != null) {
            out.add(dynamic(mark, first));
        }
    }

    /**
     * A dynamic belongs to the note it hangs under, which the engraver finds from the order of the elements, so it is
     * ordered by the glyph's left edge: a dynamic centred under a head still comes before that head.
     */
    private Item dynamic(DynamicMark mark, Mark glyph) {
        Placement placement = glyph.cy() > staff.midY() ? Placement.BELOW : Placement.ABOVE;
        return new Item(glyph.left() - 0.5 * space, new Direction(new DirectionType.Dynamic(mark), placement));
    }

    /**
     * The start and stop of a wedge that begin or end in this measure. The engraver anchors each to the next note
     * (the last one when none follows), so a wedge whose two ends share an anchor would have no length and is
     * left out.
     */
    private List<Item> wedge(Hairpin h, int number, double from, double to, double[] noteXs) {
        double startKey = h.start() - 1.2 * space;
        boolean here = h.start() > from && h.end() <= to;
        if (here && noteXs.length > 0 && anchor(noteXs, startKey) >= anchor(noteXs, h.end())) {
            return List.of();
        }
        Placement placement = h.y() > staff.midY() ? Placement.BELOW : Placement.ABOVE;
        List<Item> out = new ArrayList<>();
        if (h.start() > from && h.start() <= to) {
            DirectionType.WedgeType type = h.crescendo() ? DirectionType.WedgeType.CRESCENDO
                    : DirectionType.WedgeType.DIMINUENDO;
            out.add(new Item(startKey, new Direction(new DirectionType.Wedge(type, number), placement)));
        }
        if (h.end() > from && h.end() <= to) {
            out.add(new Item(h.end(), new Direction(new DirectionType.Wedge(DirectionType.WedgeType.STOP, number), placement)));
        }
        return out;
    }

    /** The x of the note a direction placed at {@code x} is anchored to: the next one, else the last. */
    private static double anchor(double[] noteXs, double x) {
        return Arrays.stream(noteXs).filter(n -> n > x).min().orElse(Arrays.stream(noteXs).max().orElse(x));
    }

    // ---- ties and slurs -----------------------------------------------------------------------------------------

    /** A curve with the heads at its two ends; an end without a head is where the curve runs on or in. */
    private record Ends(Box curve, Mark from, Mark to) {
    }

    /**
     * Pair every curve with the heads at its two ends. A flat curve between heads of the same pitch is a tie, any
     * other curve that starts and ends at a head is a slur. A curve that runs into the next system has no head at
     * one end; it is carried over to the next system through {@link PartState}.
     */
    private void linkCurves(List<Mark> heads) {
        List<Ends> ends = new ArrayList<>();
        for (Box c : curvesAtStaff()) {
            ends.add(new Ends(c, headAt(heads, c.left(), c), headAt(heads, c.right(), c)));
        }
        // a curve that starts before the first head carries on from the previous system
        ends.stream().filter(e -> e.from == null && e.to != null && e.curve.left() <= staff.x0() + 14 * space)
                .forEach(this::continueCurve);
        state.pendingTie = false;
        state.pendingSlur = null;
        int number = 0;
        for (Ends e : ends) {
            number = linkCurve(e, number);
        }
    }

    private List<Box> curvesAtStaff() {
        List<Box> curves = new ArrayList<>();
        for (Box c : page.curves()) {
            boolean near = c.centerY() > staff.top() - 5 * space && c.centerY() < staff.bottom() + 5 * space
                    && c.right() > staff.x0() && c.left() < staff.x1();
            if (near && c.width() >= 1.2 * space && curves.stream().noneMatch(o -> same(o, c))) {
                curves.add(c);
            }
        }
        return curves;
    }

    private boolean isFlat(Box curve) {
        return curve.height() <= 1.4 * space;
    }

    private void continueCurve(Ends e) {
        if (isFlat(e.curve) && state.pendingTie) {
            tieStops.add(e.to);
        } else if (!isFlat(e.curve) && state.pendingSlur != null) {
            addSlur(e.to, new Slur(CROSS_SYSTEM_SLUR, Slur.Type.STOP, state.pendingSlur));
        }
    }

    private void addSlur(Mark head, Slur slur) {
        slurs.computeIfAbsent(head, k -> new ArrayList<>()).add(slur);
    }

    /** Link one curve; returns the number of the last slur used in this system. */
    private int linkCurve(Ends e, int number) {
        if (e.from != null && e.to == null) {
            if (e.curve.right() >= staff.x1() - 4 * space) {
                runOn(e);
            }
            return number;
        }
        if (e.from == null || e.to.cx() <= e.from.cx()) {
            return number;
        }
        if (isFlat(e.curve) && staff.stepAt(e.from.cy()) == staff.stepAt(e.to.cy())) {
            tieStarts.add(e.from);
            tieStops.add(e.to);
            return number;
        }
        if (e.curve.width() < 2 * space) {
            return number;
        }
        int next = number % 6 + 1;
        Placement placement = e.curve.centerY() < e.from.cy() ? Placement.ABOVE : Placement.BELOW;
        addSlur(e.from, new Slur(next, Slur.Type.START, placement));
        addSlur(e.to, new Slur(next, Slur.Type.STOP, placement));
        return next;
    }

    /** A tie or slur that starts here and ends in the next system. */
    private void runOn(Ends e) {
        if (isFlat(e.curve)) {
            tieStarts.add(e.from);
            state.pendingTie = true;
        } else {
            state.pendingSlur = e.curve.centerY() < e.from.cy() ? Placement.ABOVE : Placement.BELOW;
            addSlur(e.from, new Slur(CROSS_SYSTEM_SLUR, Slur.Type.START, state.pendingSlur));
        }
    }

    /** The head closest to one end of a curve, by distance along the staff and a little by height. */
    private Mark headAt(List<Mark> heads, double x, Box curve) {
        Mark best = null;
        double bestScore = 1.5 * space;
        for (Mark head : heads) {
            double score = Math.abs(head.cx() - x) + 0.25 * Math.abs(head.cy() - curve.centerY());
            if (Math.abs(head.cx() - x) < 1.5 * space && score < bestScore) {
                best = head;
                bestScore = score;
            }
        }
        return best;
    }

    // ---- tuplets ------------------------------------------------------------------------------------------------

    /** Single digits near the staff that can be tuplet numbers; multi-digit numbers are bar or rehearsal numbers. */
    private record Digit(double cx, int value) {
    }

    /** Notes in the time of {@code NORMAL[actual]} for a tuplet number; 2 and 4 are left out, they are usually text such as "a 2". */
    private static final int[] NORMAL = {0, 0, 0, 2, 0, 4, 4, 4, 0, 8};

    private List<Digit> digits() {
        List<Digit> near = new ArrayList<>();
        for (Glyph g : page.glyphs()) {
            Digit digit = digitAt(g);
            if (digit != null) {
                near.add(digit);
            }
        }
        return near.stream()
                .filter(d -> d.value() >= 2 && d.cx() > staff.x0() + 4 * space && d.cx() < staff.x1())
                .filter(d -> near.stream().noneMatch(o -> o != d && Math.abs(o.cx() - d.cx()) < 1.6 * space))
                .toList();
    }

    private Digit digitAt(Glyph g) {
        int value = digitValue(g);
        double cy = g.box().width() > 0 ? g.box().centerY() : g.y();
        if (value < 0 || cy <= staff.top() - 4 * space || cy >= staff.bottom() + 4 * space) {
            return null;
        }
        return new Digit(g.box().width() > 0 ? g.box().centerX() : g.x(), value);
    }

    private static int digitValue(Glyph g) {
        Symbol symbol = MusicFonts.classify(g).orElse(null);
        if (symbol != null) {
            return symbol.kind() == Kind.DIGIT ? symbol.value() : -1;
        }
        String text = g.text();
        return text != null && text.length() == 1 && Character.isDigit(text.charAt(0)) ? text.charAt(0) - '0' : -1;
    }

    /**
     * Give each tuplet number the run of items it is centred over: {@code n} consecutive notes, chords or rests whose
     * middle is closest to the number. Their durations are then scaled to the normal count.
     */
    private void applyTuplets(List<Item> items, List<Digit> digits) {
        for (Digit digit : digits) {
            int n = digit.value();
            if (n < NORMAL.length && NORMAL[n] != 0 && items.size() >= n) {
                markTuplet(items, bestWindow(items, digit), n);
            }
        }
    }

    /** Index of the first item of the unmarked run of {@code n} items centred closest to the digit, or -1. */
    private int bestWindow(List<Item> items, Digit digit) {
        int n = digit.value();
        int best = -1;
        double bestDistance = 1.5 * space;
        for (int i = 0; i + n <= items.size(); i++) {
            double distance = Math.abs((items.get(i).x + items.get(i + n - 1).x) / 2 - digit.cx());
            if (distance < bestDistance && items.subList(i, i + n).stream().noneMatch(it -> it.actual > 0)) {
                best = i;
                bestDistance = distance;
            }
        }
        return best;
    }

    private static void markTuplet(List<Item> items, int start, int n) {
        if (start < 0) {
            return;
        }
        long sum = items.subList(start, start + n).stream().mapToLong(it -> it.baseTicks).sum();
        if (sum * NORMAL[n] % n != 0) {
            return;
        }
        for (int k = 0; k < n; k++) {
            Item item = items.get(start + k);
            item.actual = n;
            item.normal = NORMAL[n];
            if (k == 0) {
                item.edge = Tuplet.Type.START;
            } else if (k == n - 1) {
                item.edge = Tuplet.Type.STOP;
            }
        }
    }

    // ---- voices -------------------------------------------------------------------------------------------------

    /**
     * Put the items of a measure in voices. One voice unless it would overfill the measure and the items have
     * opposite stem directions, then up stems are voice 1 and down stems voice 2.
     */
    private List<MusicElement> layOut(List<Item> all, int measureTicks, List<Digit> digits) {
        List<Item> items = all.stream().filter(i -> !i.direction).toList();
        List<Item> directions = all.stream().filter(i -> i.direction).toList();
        List<MusicElement> out = new ArrayList<>();
        boolean wholeRest = items.size() == 1 && items.get(0).rest && items.get(0).ticks() == ticks(NoteType.WHOLE, 0);
        if (items.isEmpty() || wholeRest) {
            out.add(Rest.builder().duration(new Duration(measureTicks, DIVISIONS)).type(NoteType.WHOLE).staff(1).voice(1).build());
            directions.forEach(d -> out.add(d.element(1)));
            return out;
        }
        applyTuplets(items, digits);
        if (needsSecondVoice(items, measureTicks)) {
            items.forEach(Item::clearTuplet);
            List<Item> upper = items.stream().filter(this::isUpperVoice).toList();
            List<Item> lower = items.stream().filter(i -> !isUpperVoice(i)).toList();
            applyTuplets(upper, digits);
            applyTuplets(lower, digits);
            inTimeOrder(upper, directions).forEach(i -> out.add(i.element(1)));
            lower.forEach(i -> out.add(i.element(2)));
        } else {
            inTimeOrder(items, directions).forEach(i -> out.add(i.element(1)));
        }
        return out;
    }

    private static boolean needsSecondVoice(List<Item> items, int measureTicks) {
        int total = items.stream().mapToInt(Item::ticks).sum();
        boolean hasUp = items.stream().anyMatch(i -> Boolean.TRUE.equals(i.stemUp));
        boolean hasDown = items.stream().anyMatch(i -> Boolean.FALSE.equals(i.stemUp));
        return total > measureTicks + 2 && hasUp && hasDown;
    }

    private static List<Item> inTimeOrder(List<Item> items, List<Item> directions) {
        List<Item> merged = new ArrayList<>(items);
        merged.addAll(directions);
        merged.sort(Comparator.comparingDouble((Item i) -> i.x));
        return merged;
    }

    private boolean isUpperVoice(Item item) {
        return item.rest ? item.step >= 4 : !Boolean.FALSE.equals(item.stemUp);
    }
}
