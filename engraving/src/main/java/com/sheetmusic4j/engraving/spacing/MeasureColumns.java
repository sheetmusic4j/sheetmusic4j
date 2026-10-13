package com.sheetmusic4j.engraving.spacing;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.sheetmusic4j.core.model.Chord;
import com.sheetmusic4j.core.model.Fraction;
import com.sheetmusic4j.core.model.Lyric;
import com.sheetmusic4j.core.model.MeasureTiming;
import com.sheetmusic4j.core.model.MusicElement;
import com.sheetmusic4j.core.model.Note;
import com.sheetmusic4j.core.model.Rest;
import com.sheetmusic4j.engraving.analysis.PartInfo;
import com.sheetmusic4j.engraving.analysis.ScoreAnalysis;
import com.sheetmusic4j.engraving.glyph.Glyph;
import com.sheetmusic4j.engraving.glyph.GlyphMapper;
import com.sheetmusic4j.engraving.glyph.GlyphMetrics;
import com.sheetmusic4j.engraving.glyph.MarkingCategory;
import com.sheetmusic4j.engraving.layout.LayoutOptions;
import com.sheetmusic4j.engraving.text.LyricText;

/**
 * The onset columns of one measure index across the whole system: every
 * distinct time at which any visible note, chord or rest of any part,
 * staff or voice starts. Spacing is decided per column, and every staff
 * places its elements at their column's x, so simultaneous notes line up
 * vertically across staves and voices.
 *
 * <p>A column's width weight follows the traditional sub-linear duration
 * curve ({@code width(d) ∝ d^α}, α ≈ 0.6, as used by OSMD and printed
 * engraving practice) through Gourlay's rule: a column of length {@code Δ}
 * (time until the next column) gets {@code Δ · s^(α-1)}, where {@code s}
 * is the shortest note sounding during it - for a single voice simply
 * {@code d^α} per note;
 * extra weight and a left shift are reserved for the widest accidental,
 * augmentation dots and grace-note run among the elements starting in the
 * column. Minimum distances are enforced per staff: an element only needs
 * room before the next element on its own staff, so a dotted note in one
 * instrument doesn't push away an unrelated note in another.
 */
public final class MeasureColumns {

    /**
     * Sub-linear exponent applied to a column's duration (in quarters) when
     * computing its horizontal width weight. Values between 0.5 and 0.7 give
     * the traditional engraving "square-root-ish" spacing curve; the default
     * of 0.6 is close to what OSMD uses.
     */
    private static final double NOTE_WIDTH_ALPHA = 0.6;

    /**
     * Extra weight added to a column when one of its notes carries a
     * displayed accidental, in "quarter-note width" units. Reserves space so
     * the accidental doesn't collide with the previous notehead.
     */
    private static final double ACCIDENTAL_RESERVE_WEIGHT = 0.35;

    /**
     * Extra weight added to a column per augmentation dot, in "quarter-note
     * width" units.
     */
    private static final double DOT_RESERVE_WEIGHT = 0.15;

    /**
     * Extra weight added to a column per grace note, in "quarter-note width"
     * units, mirroring {@link #ACCIDENTAL_RESERVE_WEIGHT}.
     */
    private static final double GRACE_NOTE_RESERVE_WEIGHT = 0.25;

    /**
     * Minimum horizontal advance per column, expressed in staff-line gaps.
     * Acts as a safety rail for pathologically dense measures; may push
     * content past the barline (acceptable in the MVP).
     */
    private static final double MIN_NOTE_ADVANCE_GAPS = 1.2;

    /**
     * Distance (in staff-line gaps) from a notehead's center to the center
     * of its accidental. A column whose notes carry accidentals is shifted
     * right by this plus the widest accidental's half width (from
     * {@link GlyphMetrics}), so the accidental sits inside the column's slot
     * rather than colliding with the previous element.
     */
    public static final double ACCIDENTAL_OFFSET_GAPS = 1.5;

    /**
     * Clearance (in staff-line gaps) between two neighbouring lyric
     * syllables on the same staff.
     */
    private static final double LYRIC_PADDING_GAPS = 0.6;

    /**
     * Width of a measure, in staff-line gaps, per unit of column weight (one
     * quarter note's worth) at natural spacing - before rows are stretched
     * to fill the system. Rows are broken by these natural widths, so this
     * sets how dense a system is. Calibrated against the reference PDFs of
     * {@code CompareFxViewWithReferenceTest}: the largest value at which
     * the dense orchestral sample still spreads over a plausible number of
     * systems at this library's fixed staff size.
     */
    private static final double NATURAL_GAPS_PER_WEIGHT = 4.25;

    /**
     * Trailing padding (in staff-line gaps) reserved after the rightmost
     * augmentation dot, so the dot's own glyph width doesn't touch the next
     * notehead.
     */
    private static final double DOT_TRAILING_PAD_GAPS = 0.5;

    /**
     * Horizontal spacing (in staff-line gaps) between consecutive grace
     * noteheads within one run, and per grace note reserved as a left-side
     * shift before the main note. A run of {@code n} grace notes reaches
     * back {@code n} times this many gaps from the main note, so that same
     * distance must be reserved as the main note's left shift or the run
     * collides with whatever precedes it (previous note, or the barline).
     */
    public static final double GRACE_NOTE_SPACING_GAPS = 1.7;

    /**
     * Maximum ratio between the largest and smallest column weight within
     * one measure. See {@link #capWeightRatio}.
     */
    private static final double MAX_WEIGHT_RATIO = 4.0;

    /**
     * Smallest distance (in staff-line gaps) between two adjacent columns
     * that share no staff - just enough to keep them in onset order.
     */
    private static final double MIN_COLUMN_GAP_GAPS = 0.3;

    private final Fraction[] onsets;
    private final double[] weights;
    private final double[] leftShifts;
    private final List<Constraint> constraints;
    private final double columnGap;
    private final Fraction end;

    private MeasureColumns(Fraction[] onsets, double[] weights, double[] leftShifts,
                           List<Constraint> constraints, double columnGap, Fraction end) {
        this.onsets = onsets;
        this.weights = weights;
        this.leftShifts = leftShifts;
        this.constraints = constraints;
        this.columnGap = columnGap;
        this.end = end;
    }

    /**
     * "Column {@code to} starts at least {@code minDistance} after column
     * {@code from}": an element on some staff needs that much room before
     * the next element on the same staff, which starts {@code quarters}
     * later. {@code to == onsets.length} stands for the measure end.
     */
    private record Constraint(int from, int to, double minDistance, double quarters) {
    }

    /**
     * The columns of every measure index of the score.
     */
    public static List<MeasureColumns> forScore(List<PartInfo> parts, int measureCount, LayoutOptions options) {
        List<MeasureColumns> columns = new ArrayList<>(measureCount);
        for (int idx = 0; idx < measureCount; idx++) {
            columns.add(of(parts, idx, options));
        }
        return columns;
    }

    /**
     * The columns of measure {@code measureIdx}, gathered from every part
     * that has such a measure.
     */
    public static MeasureColumns of(List<PartInfo> parts, int measureIdx, LayoutOptions options) {
        double gap = options.staffLineGap();
        Map<Fraction, Column> byOnset = new TreeMap<>();
        // Per staff (part + staff number): what the elements starting at
        // each onset need before the next element on that staff, so
        // spacing constraints only apply between elements that can
        // actually collide.
        Map<String, TreeMap<Fraction, Slot>> lanes = new HashMap<>();
        List<Fraction[]> sounding = new ArrayList<>();
        Fraction end = Fraction.ZERO;
        for (int partIdx = 0; partIdx < parts.size(); partIdx++) {
            PartInfo part = parts.get(partIdx);
            if (measureIdx >= part.measures().size()) {
                continue;
            }
            MeasureTiming timing = part.timing(measureIdx);
            end = Fraction.max(end, timing.duration());
            for (MeasureTiming.Timed timed : timing.elements()) {
                MusicElement element = timed.element();
                if (!isPlaced(element)) {
                    continue;
                }
                byOnset.computeIfAbsent(timed.onset(), k -> new Column()).add(element, gap);
                if (element.duration().quarters().isPositive()) {
                    sounding.add(new Fraction[]{timed.onset(), timed.end(), element.duration().quarters()});
                }
                int staff = MeasureTiming.voiceKey(element).staff();
                // Lyrics are drawn under the part's first staff only.
                double lyricHalf = staff == 1 ? lyricWidth(element, options) / 2.0 : 0.0;
                lanes.computeIfAbsent(partIdx + ":" + staff, k -> new TreeMap<>())
                        .merge(timed.onset(), new Slot(minSlot(element, gap), lyricHalf), Slot::max);
            }
        }
        int n = byOnset.size();
        Fraction[] onsets = byOnset.keySet().toArray(new Fraction[0]);
        Fraction measureEnd = n > 0 ? Fraction.max(end, onsets[n - 1]) : end;
        double[] weights = new double[n];
        double[] leftShifts = new double[n];
        int i = 0;
        for (Column column : byOnset.values()) {
            Fraction next = i + 1 < n ? onsets[i + 1] : measureEnd;
            double delta = next.minus(onsets[i]).toDouble();
            double shortest = shortestSounding(sounding, onsets[i], delta);
            double weight = (delta > 0 ? delta * Math.pow(shortest, NOTE_WIDTH_ALPHA - 1) : 0.0) + column.extraWeight;
            weights[i] = weight > 0 ? weight : 1.0;
            leftShifts[i] = column.leftShift;
            i++;
        }
        capWeightRatio(weights);
        if (n > 0) {
            // A syllable under the measure's first note is centered on it and
            // reaches left towards the barline, where the previous measure's
            // last syllable may end: start the content far enough in.
            for (TreeMap<Fraction, Slot> lane : lanes.values()) {
                Slot first = lane.get(onsets[0]);
                if (first != null) {
                    leftShifts[0] = Math.max(leftShifts[0], first.lyricHalf());
                }
            }
        }
        List<Constraint> constraints = new ArrayList<>();
        double lyricPadding = LYRIC_PADDING_GAPS * gap;
        for (TreeMap<Fraction, Slot> lane : lanes.values()) {
            Map.Entry<Fraction, Slot> previous = null;
            for (Map.Entry<Fraction, Slot> entry : lane.entrySet()) {
                if (previous != null) {
                    Slot slot = previous.getValue();
                    double lyrics = slot.lyricHalf() > 0 || entry.getValue().lyricHalf() > 0
                            ? slot.lyricHalf() + entry.getValue().lyricHalf() + lyricPadding
                            : 0.0;
                    constraints.add(new Constraint(Arrays.binarySearch(onsets, previous.getKey()),
                            Arrays.binarySearch(onsets, entry.getKey()), Math.max(slot.minSlot(), lyrics),
                            entry.getKey().minus(previous.getKey()).toDouble()));
                }
                previous = entry;
            }
            if (previous != null) {
                Slot slot = previous.getValue();
                double lyrics = slot.lyricHalf() > 0 ? slot.lyricHalf() + lyricPadding : 0.0;
                constraints.add(new Constraint(Arrays.binarySearch(onsets, previous.getKey()), n,
                        Math.max(slot.minSlot(), lyrics), measureEnd.minus(previous.getKey()).toDouble()));
            }
        }
        constraints.sort(Comparator.comparingInt(Constraint::to));
        return new MeasureColumns(onsets, weights, leftShifts, List.copyOf(constraints),
                MIN_COLUMN_GAP_GAPS * gap, measureEnd);
    }

    /**
     * Whether an element occupies a column: notes, chords and visible rests.
     * Invisible rests only advance their voice's time.
     */
    public static boolean isPlaced(MusicElement element) {
        return MeasureTiming.isTimed(element) && !(element instanceof Rest rest && !rest.visible());
    }

    public boolean isEmpty() {
        return onsets.length == 0;
    }

    /**
     * The measure's total width weight, used to share a row's width between
     * its measures.
     */
    public double weight() {
        double sum = 0.0;
        for (double w : weights) {
            sum += w;
        }
        return sum > 0 ? sum : 1.0;
    }

    /**
     * Absolute floor for the measure's nominal width: the tightest column
     * placement that still gives every element its minimum slot before the
     * next element on its own staff, plus the two side gaps
     * ({@code contentStart}/right padding) every measure reserves, and never
     * less than {@link LayoutOptions#measureMinWidth()}.
     *
     * <p>Without this, a system full of equally dense measures (e.g. a
     * continuous tuplet accompaniment) would each get an equal — but for
     * their content, far too small — proportional share of the system
     * width, and the per-element safety rail would then force massive
     * overflow past the barlines instead of the row simply breaking sooner.
     */
    public double minWidth(LayoutOptions options) {
        if (isEmpty()) {
            return options.measureMinWidth();
        }
        double[] starts = solve(new double[onsets.length + 1], 2 * options.staffLineGap());
        return Math.max(options.measureMinWidth(), starts[onsets.length]);
    }

    /**
     * The measure's width at natural spacing - proportional to its weight,
     * never below {@link #minWidth(LayoutOptions)}. Rows are packed by these
     * widths and then stretched to fill the system.
     */
    public double naturalWidth(LayoutOptions options) {
        double gap = options.staffLineGap();
        double natural = isEmpty() ? 0.0 : 2 * gap + weight() * NATURAL_GAPS_PER_WEIGHT * gap;
        return Math.max(minWidth(options), natural);
    }

    /**
     * The smallest pixels-per-quarter at which every element still gets its
     * minimum slot before the next element on its staff, for
     * time-proportional STRIP layout.
     */
    public double minPixelsPerQuarter() {
        double ppq = 0.0;
        for (Constraint c : constraints) {
            if (c.quarters() > 0) {
                ppq = Math.max(ppq, c.minDistance() / c.quarters());
            }
        }
        return ppq;
    }

    /**
     * Notation (PAGE) placement: share {@code available} between the columns
     * by weight, push columns right wherever an element would not get its
     * minimum slot before the next element on its staff, then shift each
     * column right within its slot by the room its accidentals and grace
     * notes need on the left.
     *
     * <p>Where minimum distances push columns right, the proportional
     * shares are scaled down until the measure fits {@code available} again
     * (when that is possible at all - see {@link #minWidth}).
     *
     * @return the x of every column, in onset order
     */
    public double[] pagePositions(double contentStart, double available) {
        double sumWeights = 0.0;
        for (double w : weights) {
            sumWeights += w;
        }
        double[] proportional = new double[onsets.length + 1];
        for (int i = 0; i < onsets.length; i++) {
            proportional[i] = sumWeights > 0
                    ? available * (weights[i] / sumWeights)
                    : available / onsets.length;
        }
        double[] starts = solve(proportional, contentStart);
        double target = contentStart + available;
        if (starts[onsets.length] > target + 1e-6) {
            starts = fit(proportional, contentStart, target);
        }
        double[] xs = new double[onsets.length];
        for (int i = 0; i < onsets.length; i++) {
            xs[i] = starts[i] + leftShifts[i];
        }
        return xs;
    }

    /**
     * The largest uniform scale of the proportional advances at which the
     * measure still ends by {@code target}, found by bisection (the end
     * position only grows with the scale); the fully compressed placement
     * when even that overruns.
     */
    private double[] fit(double[] proportional, double origin, double target) {
        double[] scaled = new double[proportional.length];
        double low = 0.0;
        double high = 1.0;
        for (int iteration = 0; iteration < 40; iteration++) {
            double mid = (low + high) / 2.0;
            if (solve(scale(proportional, mid, scaled), origin)[onsets.length] <= target) {
                low = mid;
            } else {
                high = mid;
            }
        }
        return solve(scale(proportional, low, scaled), origin);
    }

    private static double[] scale(double[] values, double factor, double[] into) {
        for (int i = 0; i < values.length; i++) {
            into[i] = values[i] * factor;
        }
        return into;
    }

    /**
     * Column start positions, left to right: each column starts its
     * predecessor's preferred advance ({@code advances[i - 1]}, at least the
     * column gap) after it, pushed further right by any constraint ending
     * there. Index {@code onsets.length} is the measure end.
     */
    private double[] solve(double[] advances, double origin) {
        double[] starts = new double[onsets.length + 1];
        starts[0] = origin;
        int c = 0;
        for (int j = 1; j <= onsets.length; j++) {
            double start = starts[j - 1] + Math.max(advances[j - 1], j < onsets.length ? columnGap : 0.0);
            for (; c < constraints.size() && constraints.get(c).to() == j; c++) {
                Constraint constraint = constraints.get(c);
                // Distances are between drawn notes: a column is drawn its
                // left shift into its slot, so the next one starts that
                // much further on.
                start = Math.max(start, starts[constraint.from()] + leftShifts[constraint.from()]
                        + constraint.minDistance());
            }
            starts[j] = start;
        }
        return starts;
    }

    /**
     * Time-proportional (STRIP) placement: x is a global linear function of
     * absolute musical onset, so equal durations get equal width and the
     * cursor moves at constant speed across the whole score (including over
     * barlines).
     *
     * @return the x of every column, in onset order
     */
    public double[] stripPositions(double timeZeroX, double measureStartQ, double pixelsPerQuarter) {
        double[] xs = new double[onsets.length];
        for (int i = 0; i < onsets.length; i++) {
            xs[i] = timeZeroX + (measureStartQ + onsets[i].toDouble()) * pixelsPerQuarter;
        }
        return xs;
    }

    /**
     * The x of the column starting at {@code onset}, or - for an onset no
     * placed element starts at, like a direction ahead of an invisible
     * rest - of the first column after it, the last column, or
     * {@code fallbackX} when the measure has no columns.
     */
    public double xAt(double[] positions, Fraction onset, double fallbackX) {
        int i = Arrays.binarySearch(onsets, onset);
        if (i >= 0) {
            return positions[i];
        }
        int insertion = -i - 1;
        if (insertion < positions.length) {
            return positions[insertion];
        }
        return positions.length > 0 ? positions[positions.length - 1] : fallbackX;
    }

    /**
     * Time from the last column to the end of the measure, in quarters: the
     * trailing whitespace before the next downbeat.
     */
    public double trailingQuarters() {
        return isEmpty() ? 0.0 : end.minus(onsets[onsets.length - 1]).toDouble();
    }

    /**
     * Clamp every positive value in {@code weights} to at most {@link
     * #MAX_WEIGHT_RATIO} times the smallest positive value present.
     *
     * <p>Width weight grows with duration ({@code d^0.6}), which is correct
     * engraving practice — but within one measure this can swing far enough
     * that a single very long note (e.g. a triple-dotted quarter) claims
     * several times the horizontal room a neighbouring very short note (e.g.
     * a 32nd) gets, reading as a wide empty gap next to a cramped one rather
     * than a smooth "longer note, more room" gradient. Capping the ratio
     * keeps the direction of the effect (longer still gets more space) while
     * bounding how extreme the spread can get.
     */
    private static void capWeightRatio(double[] weights) {
        double min = Double.MAX_VALUE;
        for (double w : weights) {
            if (w > 0 && w < min) {
                min = w;
            }
        }
        if (min == Double.MAX_VALUE) {
            return;
        }
        double ceiling = min * MAX_WEIGHT_RATIO;
        for (int i = 0; i < weights.length; i++) {
            if (weights[i] > ceiling) {
                weights[i] = ceiling;
            }
        }
    }

    /**
     * What the elements starting at one onset need: the most extra weight
     * and the widest left shift among them.
     */
    private static final class Column {
        double extraWeight;
        double leftShift;

        void add(MusicElement element, double gap) {
            extraWeight = Math.max(extraWeight, extraWeight(element));
            leftShift = Math.max(leftShift, leftShift(element, gap));
        }

        private static double extraWeight(MusicElement e) {
            double extras = 0.0;
            if (ScoreAnalysis.hasAccidental(e)) {
                extras += ACCIDENTAL_RESERVE_WEIGHT;
            }
            extras += DOT_RESERVE_WEIGHT * ScoreAnalysis.dotCount(e);
            extras += GRACE_NOTE_RESERVE_WEIGHT * ScoreAnalysis.graceNoteCount(e);
            return extras;
        }

        /**
         * Room an element needs to the left of its notehead, inside its own
         * slot, for its widest accidental and its grace-note run.
         */
        private static double leftShift(MusicElement element, double gap) {
            double shift = 0.0;
            for (Note note : notesOf(element)) {
                Glyph accidental = GlyphMapper.accidentalGlyph(note.displayedAccidental().orElse(null));
                if (accidental != null) {
                    shift = Math.max(shift, ACCIDENTAL_OFFSET_GAPS * gap + GlyphMetrics.halfWidth(accidental, gap));
                }
            }
            int graceNotes = ScoreAnalysis.graceNoteCount(element);
            if (graceNotes > 0) {
                shift += GRACE_NOTE_SPACING_GAPS * graceNotes * gap;
            }
            return shift;
        }
    }

    /**
     * Duration of the shortest note or rest sounding at {@code onset}, from
     * any staff - Gourlay's spacing rule: a column gets space in proportion
     * to its own length, at the rate of the shortest note in progress, so
     * that splitting a long note's span into several columns (because
     * another staff moves faster) doesn't add space. Falls back to
     * {@code delta} where nothing sounds.
     */
    private static double shortestSounding(List<Fraction[]> sounding, Fraction onset, double delta) {
        double shortest = Double.MAX_VALUE;
        for (Fraction[] note : sounding) {
            if (note[0].compareTo(onset) <= 0 && note[1].compareTo(onset) > 0) {
                shortest = Math.min(shortest, note[2].toDouble());
            }
        }
        return shortest == Double.MAX_VALUE ? delta : shortest;
    }

    private static List<Note> notesOf(MusicElement element) {
        return switch (element) {
            case Note note -> List.of(note);
            case Chord chord -> chord.notes();
            default -> List.of();
        };
    }

    /**
     * Width of the widest verse-1 lyric syllable under an element (the only
     * verse drawn), or {@code 0} when it has none or lyrics are hidden.
     */
    private static double lyricWidth(MusicElement element, LayoutOptions options) {
        Note carrier = ScoreAnalysis.lyricCarrier(element);
        if (carrier == null || !options.isVisible(MarkingCategory.LYRIC)) {
            return 0.0;
        }
        double fontSize = options.staffLineGap() * LyricText.FONT_SIZE_GAPS;
        double width = 0.0;
        for (Lyric lyric : carrier.lyrics()) {
            if (lyric.verse() == 1 && !lyric.text().isEmpty()) {
                width = Math.max(width, options.textMetrics().width(LyricText.of(lyric), fontSize));
            }
        }
        return width;
    }

    /**
     * What the elements starting at one onset on one staff need before the
     * next element on that staff: their minimum slot, and half the width of
     * their lyric syllable (which is centered under the note).
     */
    private record Slot(double minSlot, double lyricHalf) {

        static Slot max(Slot a, Slot b) {
            return new Slot(Math.max(a.minSlot, b.minSlot), Math.max(a.lyricHalf, b.lyricHalf));
        }
    }

    /**
     * Minimum horizontal room an element needs before the next element on
     * its staff: normally just {@code MIN_NOTE_ADVANCE_GAPS * gap}, but
     * widened for augmentation dots - drawn starting at
     * {@code noteX + 1.2 * gap} and spaced {@code 0.6 * gap} apart - and for
     * a grace-note run so it fits within the element's own slot.
     */
    private static double minSlot(MusicElement element, double gap) {
        double slot = MIN_NOTE_ADVANCE_GAPS * gap;
        int dots = ScoreAnalysis.dotCount(element);
        if (dots > 0) {
            slot = Math.max(slot, gap * (1.2 + (dots - 1) * 0.6 + DOT_TRAILING_PAD_GAPS));
        }
        int graceNotes = ScoreAnalysis.graceNoteCount(element);
        if (graceNotes > 0) {
            slot = Math.max(slot, gap * (GRACE_NOTE_SPACING_GAPS * graceNotes + 1.0));
        }
        return slot;
    }
}
