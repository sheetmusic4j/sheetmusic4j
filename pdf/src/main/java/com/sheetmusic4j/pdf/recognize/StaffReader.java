package com.sheetmusic4j.pdf.recognize;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.sheetmusic4j.core.model.Accidental;
import com.sheetmusic4j.core.model.Attributes;
import com.sheetmusic4j.core.model.Barline;
import com.sheetmusic4j.core.model.Chord;
import com.sheetmusic4j.core.model.Clef;
import com.sheetmusic4j.core.model.ClefSign;
import com.sheetmusic4j.core.model.Duration;
import com.sheetmusic4j.core.model.KeySignature;
import com.sheetmusic4j.core.model.Measure;
import com.sheetmusic4j.core.model.MusicElement;
import com.sheetmusic4j.core.model.Note;
import com.sheetmusic4j.core.model.NoteType;
import com.sheetmusic4j.core.model.Pitch;
import com.sheetmusic4j.core.model.Rest;
import com.sheetmusic4j.core.model.Step;
import com.sheetmusic4j.core.model.TimeSignature;
import com.sheetmusic4j.pdf.extract.PageContent;
import com.sheetmusic4j.pdf.extract.PageContent.Box;
import com.sheetmusic4j.pdf.extract.PageContent.VLine;
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

    /** Ticks per quarter note. 480 divides evenly down to 128th notes and triplets of them. */
    public static final int DIVISIONS = 480;

    private static final Step[] STEPS = {Step.C, Step.D, Step.E, Step.F, Step.G, Step.A, Step.B};
    private static final NoteType[] REST_TYPES = {NoteType.WHOLE, NoteType.HALF, NoteType.QUARTER, NoteType.EIGHTH,
            NoteType.SIXTEENTH, NoteType.THIRTY_SECOND, NoteType.SIXTY_FOURTH, NoteType.HUNDRED_TWENTY_EIGHTH};
    private static final NoteType[] FILLED_TYPES = {NoteType.QUARTER, NoteType.EIGHTH, NoteType.SIXTEENTH,
            NoteType.THIRTY_SECOND, NoteType.SIXTY_FOURTH, NoteType.HUNDRED_TWENTY_EIGHTH};

    private final Staff staff;
    private final PageContent page;
    private final PartState state;
    private final double space;

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
     * @param firstNumber number of the first measure
     */
    public List<Measure> read(List<Mark> marks, int firstNumber, boolean systemBreak) {
        List<Mark> heads = marks.stream().filter(m -> m.symbol().kind() == Kind.HEAD && !isGrace(m)).toList();
        List<Bar> bars = barlines(heads);
        List<VLine> stems = stems(bars);
        List<Measure> measures = new ArrayList<>();
        double start = staff.x0();
        for (int i = 0; i <= bars.size(); i++) {
            Bar bar = i < bars.size() ? bars.get(i) : null;
            double end = bar == null ? staff.x1() + 2 : bar.first;
            if (bar == null && end - start < 3 * space) {
                break;
            }
            double from = start;
            List<Mark> inside = marks.stream().filter(m -> m.cx() > from && m.cx() <= end)
                    .sorted(Comparator.comparingDouble(Mark::cx)).toList();
            measures.add(readMeasure(firstNumber + measures.size(), inside, stems, i == 0 && systemBreak,
                    bar == null ? null : bar.style));
            if (bar != null) {
                start = bar.last;
            }
        }
        return measures;
    }

    // ---- barlines -----------------------------------------------------------------------------------------------

    private record Bar(double first, double last, Barline style) {
    }

    private List<Bar> barlines(List<Mark> heads) {
        double tol = space * 0.3;
        List<VLine> candidates = new ArrayList<>();
        for (VLine v : page.vlines()) {
            if (v.y0() <= staff.top() + tol && v.y1() >= staff.bottom() - tol && v.x() > staff.x0() + space
                    && v.x() <= staff.x1() + 2 && !isStem(v, heads)) {
                candidates.add(v);
            }
        }
        candidates = withoutOutlines(candidates);
        List<List<VLine>> clusters = new ArrayList<>();
        for (VLine v : candidates) {
            List<VLine> last = clusters.isEmpty() ? null : clusters.get(clusters.size() - 1);
            if (last != null && v.x() - last.get(last.size() - 1).x() <= 1.2 * space) {
                last.add(v);
            } else {
                last = new ArrayList<>();
                last.add(v);
                clusters.add(last);
            }
        }
        List<Bar> bars = new ArrayList<>();
        for (List<VLine> cluster : clusters) {
            VLine first = cluster.get(0);
            VLine last = cluster.get(cluster.size() - 1);
            Barline.Style style = Barline.Style.REGULAR;
            if (cluster.size() > 1) {
                style = last.thickness() > 2 * first.thickness() ? Barline.Style.FINAL : Barline.Style.DOUBLE;
            }
            bars.add(new Bar(first.x(), last.x(), new Barline(style, Barline.Repeat.NONE)));
        }
        return bars;
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
        for (Mark head : heads) {
            boolean atEnd = Math.abs(head.cy() - v.y0()) < 0.9 * space || Math.abs(head.cy() - v.y1()) < 0.9 * space;
            if (atEnd && v.x() >= head.left() - 0.2 * space && v.x() <= head.right() + 0.2 * space) {
                return true;
            }
        }
        return false;
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

    private Measure readMeasure(int number, List<Mark> marks, List<VLine> stems, boolean systemBreak, Barline barline) {
        List<Mark> heads = new ArrayList<>();
        List<Mark> rests = new ArrayList<>();
        List<Mark> accidentals = new ArrayList<>();
        List<Mark> dots = new ArrayList<>();
        List<Mark> flags = new ArrayList<>();
        List<Mark> clefs = new ArrayList<>();
        List<Mark> timeMarks = new ArrayList<>();
        for (Mark m : marks) {
            switch (m.symbol().kind()) {
                case HEAD -> {
                    if (!isGrace(m)) {
                        heads.add(m);
                    }
                }
                case REST -> rests.add(m);
                case ACCIDENTAL -> accidentals.add(m);
                case DOT -> dots.add(m);
                case FLAG -> flags.add(m);
                case CLEF -> clefs.add(m);
                case TIME_COMMON, TIME_CUT, DIGIT -> timeMarks.add(m);
            }
        }
        double firstContent = Double.MAX_VALUE;
        for (Mark m : heads) {
            firstContent = Math.min(firstContent, m.left());
        }
        for (Mark m : rests) {
            firstContent = Math.min(firstContent, m.left());
        }
        final double contentStart = firstContent;

        Attributes.Builder attributes = Attributes.builder();
        boolean changed = false;

        // clef: before the first note it is the measure's clef, later ones apply from where they stand
        Clef startClef = state.clef;
        List<Mark> clefChanges = new ArrayList<>();
        for (Mark m : clefs) {
            if (m.cx() < contentStart) {
                startClef = clefOf(m);
            } else {
                clefChanges.add(m);
            }
        }
        if (startClef == null) {
            startClef = Clef.treble();
        }
        if (!startClef.equals(state.clef)) {
            attributes.clef(startClef);
            changed = true;
        }
        state.clef = startClef;

        // time signature: marks before the first note
        TimeSignature time = timeSignature(timeMarks, contentStart);
        double timeStart = timeMarks.stream().filter(m -> m.cx() < contentStart).mapToDouble(Mark::left).min()
                .orElse(Double.MAX_VALUE);
        if (time != null && !time.equals(state.time)) {
            attributes.timeSignature(time);
            state.time = time;
            changed = true;
        } else if (state.time == null) {
            state.time = TimeSignature.fourFour();
            attributes.timeSignature(state.time);
            changed = true;
        }

        // key signature: the accidentals before the first note (or time signature) that no note owns
        if (!state.started || startsSystem(marks) || hasKeyChange(accidentals, heads, Math.min(contentStart, timeStart))) {
            int fifths = keySignature(accidentals, heads, Math.min(contentStart, timeStart), timeStart != Double.MAX_VALUE);
            if (state.fifths == null || state.fifths != fifths) {
                attributes.keySignature(new KeySignature(fifths));
                state.fifths = fifths;
                changed = true;
            }
        }
        if (!state.started) {
            attributes.divisions(DIVISIONS);
            changed = true;
            state.started = true;
        }

        KeySignature key = new KeySignature(state.fifths == null ? 0 : state.fifths);
        int measureTicks = state.time.beats() * DIVISIONS * 4 / state.time.beatType();
        List<Item> items = items(heads, rests, accidentals, dots, flags, stems, key, clefChanges, startClef);

        Measure.Builder measure = Measure.builder(number);
        if (changed) {
            measure.attributes(attributes.build());
        }
        for (MusicElement element : layOut(items, measureTicks)) {
            measure.addElement(element);
        }
        if (barline != null && barline.style() != Barline.Style.REGULAR) {
            measure.barline(barline);
        }
        measure.forceSystemBreak(systemBreak);
        return measure.build();
    }

    /** Whether the measure holds the clef that opens a system (a clef before any head or rest). */
    private static boolean startsSystem(List<Mark> marks) {
        for (Mark m : marks) {
            if (m.symbol().kind() == Kind.HEAD || m.symbol().kind() == Kind.REST) {
                return false;
            }
            if (m.symbol().kind() == Kind.CLEF) {
                return true;
            }
        }
        return false;
    }

    private Clef clefOf(Mark m) {
        int line = Math.max(1, Math.min(5, staff.stepAt(m.oy()) / 2 + 1));
        return switch (m.symbol().value()) {
            case Symbol.F_CLEF -> new Clef(ClefSign.F, line);
            case Symbol.C_CLEF -> new Clef(ClefSign.C, line);
            default -> new Clef(ClefSign.G, line);
        };
    }

    private TimeSignature timeSignature(List<Mark> marks, double contentStart) {
        StringBuilder top = new StringBuilder();
        StringBuilder bottom = new StringBuilder();
        for (Mark m : marks) {
            if (m.cx() >= contentStart || m.cy() < staff.top() - 0.5 * space || m.cy() > staff.bottom() + 0.5 * space) {
                continue;
            }
            switch (m.symbol().kind()) {
                case TIME_COMMON -> {
                    return new TimeSignature(4, 4);
                }
                case TIME_CUT -> {
                    return new TimeSignature(2, 2);
                }
                case DIGIT -> (m.cy() < staff.midY() ? top : bottom).append(m.symbol().value());
                default -> {
                }
            }
        }
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
        int sharps = 0;
        int flats = 0;
        for (Mark a : accidentals) {
            if (a.cx() >= limit) {
                continue;
            }
            Mark owner = owner(a, heads);
            if (owner != null && (hasTime || owner.left() - a.right() < 2.6 * space)) {
                continue;
            }
            if (a.symbol().value() > 0) {
                sharps++;
            } else if (a.symbol().value() < 0) {
                flats++;
            }
        }
        return sharps >= flats ? sharps : -flats;
    }

    /** Accidentals before the first note that belong to no note: a key signature change inside a system. */
    private boolean hasKeyChange(List<Mark> accidentals, List<Mark> heads, double limit) {
        return accidentals.stream().anyMatch(a -> a.cx() < limit && (owner(a, heads) == null
                || owner(a, heads).left() - a.right() >= 2.6 * space));
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

    /** A note, chord or rest in time order, with what is needed to place it in a voice. */
    private record Item(double x, Boolean stemUp, int step, boolean rest, int ticks, java.util.function.IntFunction<MusicElement> make) {
    }

    private List<Item> items(List<Mark> heads, List<Mark> rests, List<Mark> accidentals, List<Mark> dots,
                             List<Mark> flags, List<VLine> stems, KeySignature key, List<Mark> clefChanges,
                             Clef startClef) {
        List<Item> items = new ArrayList<>();
        Map<Integer, Integer> carried = new HashMap<>();
        // a dot over or under a head is a staccato mark, a duration dot stands to the right of it
        dots = dots.stream().filter(d -> heads.stream().noneMatch(h -> Math.abs(d.cx() - h.cx()) < 0.5 * h.width())).toList();
        Map<Mark, Mark> owners = new HashMap<>();
        for (Mark a : accidentals) {
            Mark head = owner(a, heads);
            if (head != null) {
                owners.merge(head, a, (x, y) -> x.cx() > y.cx() ? x : y);
            }
        }

        // group heads into chords: by shared stem, else by column
        List<List<Mark>> groups = new ArrayList<>();
        Map<VLine, List<Mark>> byStem = new HashMap<>();
        Map<Mark, VLine> stemOf = new HashMap<>();
        for (Mark head : heads) {
            VLine stem = stemFor(head, stems);
            stemOf.put(head, stem);
            if (stem != null) {
                List<Mark> group = byStem.get(stem);
                if (group == null) {
                    group = new ArrayList<>();
                    byStem.put(stem, group);
                    groups.add(group);
                }
                group.add(head);
            } else {
                List<Mark> group = columnGroup(groups, head, stemOf);
                if (group == null) {
                    group = new ArrayList<>();
                    groups.add(group);
                }
                group.add(head);
            }
        }

        for (List<Mark> group : groups) {
            Mark lead = group.get(0);
            VLine stem = stemOf.get(lead);
            Boolean up = stem == null ? null : isUp(stem, lead);
            int headType = lead.symbol().value();
            int flagCount = headType == Symbol.FILLED ? flagsFor(stem, up, flags) : 0;
            int beamCount = headType == Symbol.FILLED ? beamsFor(stem, up) : 0;
            NoteType type = switch (headType) {
                case Symbol.WHOLE -> NoteType.WHOLE;
                case Symbol.HALF -> NoteType.HALF;
                default -> FILLED_TYPES[Math.min(Math.max(flagCount, beamCount), FILLED_TYPES.length - 1)];
            };
            int dotCount = 0;
            for (Mark head : group) {
                dotCount = Math.max(dotCount, dotsAfter(head.right(), staff.stepAt(head.cy()), 1, dots));
            }
            int ticks = ticks(type, dotCount);

            List<Note.Builder> builders = new ArrayList<>();
            List<Mark> ordered = new ArrayList<>(group);
            ordered.sort(Comparator.comparingDouble(Mark::cx));
            for (Mark head : ordered) {
                int step = staff.stepAt(head.cy());
                Clef clef = clefAt(head.cx(), clefChanges, startClef);
                int diatonic = diatonic(clef, step);
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
                        .duration(new Duration(ticks, DIVISIONS))
                        .type(type)
                        .dots(dotCount)
                        .staff(1);
                if (accidental != null) {
                    builder.displayedAccidental(Accidental.fromAlter(alter));
                }
                if (up != null) {
                    builder.stemUp(up);
                }
                builders.add(builder);
            }
            double x = lead.cx();
            int step = staff.stepAt(lead.cy());
            items.add(new Item(x, up, step, false, ticks, voice -> {
                List<Note> notes = builders.stream().map(b -> b.voice(voice).build())
                        .sorted(Comparator.comparingInt(n -> n.pitch().toMidiNumber())).toList();
                return notes.size() == 1 ? notes.get(0) : new Chord(notes);
            }));
        }

        for (Mark rest : rests) {
            int exponent = Math.min(rest.symbol().value(), REST_TYPES.length - 1);
            NoteType type = REST_TYPES[exponent];
            int dotCount = dotsAfter(rest.right(), staff.stepAt(rest.cy()), 2, dots);
            int ticks = ticks(type, dotCount);
            items.add(new Item(rest.cx(), null, staff.stepAt(rest.cy()), true, ticks, voice -> Rest.builder()
                    .duration(new Duration(ticks, DIVISIONS)).type(type).dots(dotCount).staff(1).voice(voice).build()));
        }
        items.sort(Comparator.comparingDouble(Item::x));
        return items;
    }

    private static int ticks(NoteType type, int dots) {
        return (int) type.quarters(dots).times(DIVISIONS).round();
    }

    private Clef clefAt(double x, List<Mark> changes, Clef start) {
        Clef clef = start;
        for (Mark m : changes) {
            if (m.cx() < x) {
                clef = clefOf(m);
            }
        }
        return clef;
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

    private VLine stemFor(Mark head, List<VLine> stems) {
        VLine best = null;
        double bestDistance = Double.MAX_VALUE;
        for (VLine v : stems) {
            if (head.cy() < v.y0() - 0.5 * space || head.cy() > v.y1() + 0.5 * space) {
                continue;
            }
            double d = Math.min(Math.abs(v.x() - head.left()), Math.abs(v.x() - head.right()));
            if (d <= 0.4 * space && d < bestDistance) {
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
    private List<Mark> columnGroup(List<List<Mark>> groups, Mark head, Map<Mark, VLine> stemOf) {
        for (List<Mark> group : groups) {
            Mark other = group.get(0);
            if (stemOf.get(other) != null) {
                continue;
            }
            double dx = Math.abs(other.cx() - head.cx());
            int dStep = Math.abs(staff.stepAt(other.cy()) - staff.stepAt(head.cy()));
            if (dx < 0.3 * space || (dx < 1.2 * head.width() && dStep <= 1)) {
                return group;
            }
        }
        return null;
    }

    private int flagsFor(VLine stem, Boolean up, List<Mark> flags) {
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

    private int beamsFor(VLine stem, Boolean up) {
        if (stem == null) {
            return 0;
        }
        double end = up ? stem.y0() : stem.y1();
        int count = 0;
        List<Box> seen = new ArrayList<>();
        for (Box beam : page.beams()) {
            if (beam.width() < 0.6 * space || beam.height() > 2.5 * space || seen.stream().anyMatch(b -> same(b, beam))) {
                continue;
            }
            if (stem.x() >= beam.left() - 0.3 && stem.x() <= beam.right() + 0.3
                    && end >= beam.top() - 0.4 * space && end <= beam.bottom() + 0.4 * space) {
                seen.add(beam);
                count++;
            }
        }
        return count;
    }

    private static boolean same(Box a, Box b) {
        return Math.abs(a.left() - b.left()) < 0.3 && Math.abs(a.right() - b.right()) < 0.3
                && Math.abs(a.top() - b.top()) < 0.3 && Math.abs(a.bottom() - b.bottom()) < 0.3;
    }

    /** Dots following a head or rest: the first close to it, each further one close to the one before. */
    private int dotsAfter(double right, int step, int stepTolerance, List<Mark> dots) {
        int count = 0;
        double x = right;
        while (true) {
            Mark next = null;
            for (Mark dot : dots) {
                double gap = dot.cx() - x;
                if (gap > 0.05 * space && gap < (count == 0 ? 2.6 : 1.4) * space
                        && Math.abs(staff.stepAt(dot.oy()) - step) <= stepTolerance
                        && (next == null || dot.cx() < next.cx())) {
                    next = dot;
                }
            }
            if (next == null) {
                return count;
            }
            count++;
            x = next.cx();
        }
    }

    // ---- voices -------------------------------------------------------------------------------------------------

    /**
     * Put the items of a measure in voices. One voice unless it would overfill the measure and the items have
     * opposite stem directions, then up stems are voice 1 and down stems voice 2.
     */
    private List<MusicElement> layOut(List<Item> items, int measureTicks) {
        List<MusicElement> out = new ArrayList<>();
        if (items.isEmpty()) {
            out.add(Rest.builder().duration(new Duration(measureTicks, DIVISIONS)).type(NoteType.WHOLE).staff(1).voice(1).build());
            return out;
        }
        if (items.size() == 1 && items.get(0).rest && items.get(0).ticks == ticks(NoteType.WHOLE, 0)) {
            out.add(Rest.builder().duration(new Duration(measureTicks, DIVISIONS)).type(NoteType.WHOLE).staff(1).voice(1).build());
            return out;
        }
        int total = items.stream().mapToInt(Item::ticks).sum();
        boolean hasUp = items.stream().anyMatch(i -> Boolean.TRUE.equals(i.stemUp));
        boolean hasDown = items.stream().anyMatch(i -> Boolean.FALSE.equals(i.stemUp));
        if (total > measureTicks + 2 && hasUp && hasDown) {
            for (Item item : items) {
                boolean upper = item.rest ? item.step >= 4 : !Boolean.FALSE.equals(item.stemUp);
                if (upper) {
                    out.add(item.make.apply(1));
                }
            }
            for (Item item : items) {
                boolean upper = item.rest ? item.step >= 4 : !Boolean.FALSE.equals(item.stemUp);
                if (!upper) {
                    out.add(item.make.apply(2));
                }
            }
            return out;
        }
        for (Item item : items) {
            out.add(item.make.apply(1));
        }
        return out;
    }
}
