package com.sheetmusic4j.engraving.spacing;

import java.util.ArrayList;
import java.util.List;

import com.sheetmusic4j.core.model.Clef;
import com.sheetmusic4j.core.model.KeySignature;
import com.sheetmusic4j.core.model.Measure;
import com.sheetmusic4j.core.model.MusicElement;
import com.sheetmusic4j.core.model.TimeSignature;
import com.sheetmusic4j.engraving.layout.LayoutOptions;
import com.sheetmusic4j.engraving.layout.MeasureLayout;
import com.sheetmusic4j.engraving.analysis.PartInfo;
import com.sheetmusic4j.engraving.analysis.ScoreAnalysis;

/**
 * Horizontal spacing: how wide measures are, how a row's width is shared
 * between them, and where each time-consuming element sits inside its
 * measure.
 */
public final class HorizontalSpacing {

    /**
     * Sub-linear exponent applied to a note's duration (in quarters) when
     * computing its horizontal width weight. Values between 0.5 and 0.7 give
     * the traditional engraving "square-root-ish" spacing curve; the default
     * of 0.6 is close to what OSMD uses.
     */
    private static final double NOTE_WIDTH_ALPHA = 0.6;

    /**
     * Extra weight added to a note's width when it carries a displayed
     * accidental, in "quarter-note width" units. Reserves space so the
     * accidental doesn't collide with the previous notehead.
     */
    private static final double ACCIDENTAL_RESERVE_WEIGHT = 0.35;

    /**
     * Extra weight added to a note's width per augmentation dot, in
     * "quarter-note width" units.
     */
    private static final double DOT_RESERVE_WEIGHT = 0.15;

    /**
     * Minimum horizontal advance per time-consuming element, expressed in
     * staff-line gaps. Acts as a safety rail for pathologically dense
     * measures; may push content past the barline (acceptable in the MVP).
     */
    private static final double MIN_NOTE_ADVANCE_GAPS = 1.2;

    /**
     * Left-side shift (in staff-line gaps) applied to a note's x position
     * when it carries an accidental. This ensures the accidental glyph sits
     * inside the note's reserved slot rather than colliding with the
     * previous element.
     *
     * <p>The accidental itself is drawn centered {@code gap * 1.5} to the
     * left of the note (see {@code StaffRowEngraver}'s accidental placement)
     * and its own glyph then extends roughly another half-gap further left at
     * this layout's font size (see {@code SmuflGlyphs#halfAdvanceWidth} for
     * {@code ACCIDENTAL_FLAT}/{@code ACCIDENTAL_SHARP}/{@code
     * ACCIDENTAL_NATURAL}) - this value must clear that whole reach, or the
     * accidental collides with the previous element regardless of how much
     * the note itself gets shifted.
     */
    private static final double ACCIDENTAL_RESERVE_GAPS = 2.0;

    /**
     * Left-side shift for a note whose accidental is a double sharp/flat:
     * {@link #ACCIDENTAL_RESERVE_GAPS} sized for the narrower single
     * accidentals, but a double glyph draws almost twice as wide (see
     * {@code SmuflGlyphs#halfAdvanceWidth} for {@code
     * ACCIDENTAL_DOUBLE_FLAT}/{@code ACCIDENTAL_DOUBLE_SHARP}) and needs
     * more room to stay clear of the previous element.
     */
    public static final double DOUBLE_ACCIDENTAL_RESERVE_GAPS = 2.4;

    /**
     * Trailing padding (in staff-line gaps) reserved after the rightmost
     * augmentation dot, so the dot's own glyph width doesn't touch the next
     * notehead.
     */
    private static final double DOT_TRAILING_PAD_GAPS = 0.5;

    /**
     * Horizontal spacing (in staff-line gaps) between consecutive grace
     * noteheads within one run, and per grace note reserved as a left-side
     * shift before the main note - see
     * {@code StaffRowEngraver#buildGraceNotePlacement}. A run of {@code n}
     * grace notes reaches back {@code n} times this many gaps from the main
     * note, so that same distance must be reserved as the main note's left
     * shift or the run collides with whatever precedes it (previous note,
     * or the barline).
     */
    public static final double GRACE_NOTE_SPACING_GAPS = 1.7;

    /**
     * Extra weight added to a note's width per grace note it carries, in
     * "quarter-note width" units, mirroring {@link #ACCIDENTAL_RESERVE_WEIGHT}.
     */
    private static final double GRACE_NOTE_RESERVE_WEIGHT = 0.25;

    /**
     * Maximum ratio, within a single weighting context (a measure's
     * elements, or a row's measures), between the largest and smallest
     * width weight. See {@link #capWeightRatio}.
     */
    private static final double MAX_WEIGHT_RATIO = 4.0;

    private HorizontalSpacing() {
    }

    /**
     * Compute the horizontal space the painter needs at the head of a staff
     * for the given clef + optional key signature + optional time signature.
     *
     * <p>The value must match the advances used by
     * {@code StaffRowEngraver#placeKeySignature} and
     * {@code StaffRowEngraver#placeTimeSignature} plus the clef reservation
     * applied in {@code StaffRowEngraver}. Callers use it to (a) size the
     * header block, and (b) determine how much space to reserve before the
     * row's first measure.
     */
    public static double headerAdvance(Clef clef, KeySignature key, TimeSignature time, LayoutOptions options) {
        double gap = options.staffLineGap();
        // Left padding + clef block. gap*0.5 opening pad + gap*3.5 clef space.
        double advance = 0.0;
        if (options.showClef()) {
            advance += gap * 4;
        } else {
            // Still keep a small opening pad so the first note isn't glued
            // to the system barline when the clef is suppressed.
            advance += gap;
        }
        if (key != null && options.showKeySignature() && (key.sharps() != 0 || key.flats() != 0)) {
            int count = Math.min(7, Math.max(key.sharps(), key.flats()));
            double accAdvance = gap * 1.1;
            advance += count * accAdvance + gap * 0.5;
        }
        if (time != null && options.showTimeSignature()) {
            double digitWidth = gap * 1.4;
            int beatsLen = Integer.toString(time.beats()).length();
            int beatTypeLen = Integer.toString(time.beatType()).length();
            int maxLen = Math.max(beatsLen, beatTypeLen);
            advance += maxLen * digitWidth + gap;
        }
        return advance;
    }

    /**
     * Maximum header advance width across every part at the beginning of the
     * given row (i.e. at {@code measureIndex}), using the effective clef of
     * staff 0 for each part.
     */
    public static double maxHeaderAdvanceAtRowStart(List<PartInfo> parts, int measureIndex,
                                             LayoutOptions options) {
        double max = 0.0;
        for (PartInfo p : parts) {
            if (p.measures().isEmpty()) {
                continue;
            }
            int idx = Math.min(measureIndex, p.measures().size() - 1);
            Clef clef = p.clefAt(idx, 0);
            KeySignature key = p.keyAt(idx);
            TimeSignature time = p.timeAt(idx);
            // Row-start header always shows clef + key; time only when it changed.
            TimeSignature timeToShow = (measureIndex == 0 || p.timeChangedAt(measureIndex))
                    ? time
                    : null;
            max = Math.max(max, headerAdvance(clef, key, timeToShow, options));
        }
        return max;
    }

    /**
     * Shared per-measure weights across all parts: for each measure index we
     * take the maximum weight seen in any part. This is what drives the
     * cross-part measure width alignment.
     */
    public static List<Double> sharedMeasureWeights(List<PartInfo> parts, int measureCount) {
        List<Double> weights = new ArrayList<>(measureCount);
        for (int idx = 0; idx < measureCount; idx++) {
            double max = 0.0;
            for (PartInfo p : parts) {
                if (idx < p.measures().size()) {
                    max = Math.max(max, measureWeight(p.measures().get(idx)));
                }
            }
            weights.add(max > 0 ? max : 1.0);
        }
        return weights;
    }

    /**
     * Shared per-measure content-aware minimum widths across all parts: for
     * each measure index we take the maximum {@link #measureContentMinWidth}
     * seen in any part, so a measure is never squeezed below what its
     * busiest part needs.
     */
    public static List<Double> sharedMeasureMinWidths(List<PartInfo> parts, int measureCount,
                                               LayoutOptions options) {
        List<Double> mins = new ArrayList<>(measureCount);
        for (int idx = 0; idx < measureCount; idx++) {
            double max = options.measureMinWidth();
            for (PartInfo p : parts) {
                if (idx < p.measures().size()) {
                    max = Math.max(max, measureContentMinWidth(p.measures().get(idx), options));
                }
            }
            mins.add(max);
        }
        return mins;
    }

    /**
     * Distribute {@code totalWidth} across a list of weight-sized measures,
     * clamping each measure {@code i} to at least {@code mins.get(i)}.
     */
    public static List<Double> distributeWidths(List<Double> weights, double totalWidth, List<Double> mins) {
        List<Double> widths = new ArrayList<>(weights.size());
        if (weights.isEmpty()) {
            return widths;
        }
        double sumWeights = 0.0;
        double sumMins = 0.0;
        for (int i = 0; i < weights.size(); i++) {
            sumWeights += weights.get(i);
            sumMins += mins.get(i);
        }
        double effective = Math.max(totalWidth, sumMins);
        double sum = 0.0;
        for (int i = 0; i < weights.size(); i++) {
            double w = weights.get(i);
            double min = mins.get(i);
            double raw = sumWeights > 0 ? effective * (w / sumWeights) : effective / weights.size();
            double width = Math.max(min, raw);
            widths.add(width);
            sum += width;
        }
        if (sum < effective) {
            double leftover = effective - sum;
            for (int i = 0; i < widths.size(); i++) {
                widths.set(i, widths.get(i) + leftover * (weights.get(i) / Math.max(sumWeights, 1e-9)));
            }
        }
        return widths;
    }

    /**
     * Stretch the base widths of measures {@code [start, endExclusive)} so
     * they fill the given content width exactly, then absorb the row
     * {@code header} into the first measure's width.
     *
     * <p>Consequences:
     * <ul>
     *   <li>The first measure's {@link MeasureLayout} spans from the staff's
     *       left edge (including the clef / key / time-signature block) to
     *       {@code header + contentTarget[0]} — so barlines and measure
     *       diagnostics see the full visual measure, not just its
     *       post-header content.</li>
     *   <li>All measures together fill exactly {@code contentTarget +
     *       header = staffWidth} so no trailing gap remains at the right
     *       side of the staff.</li>
     *   <li>Note x-positions inside the first measure are still packed from
     *       {@code contentStart = leftMargin + header} onwards, so the
     *       post-header content of measure 1 has the same visual density
     *       as any other measure in the row.</li>
     * </ul>
     */
    public static List<Double> stretchRowWidths(List<Double> baseWidths, int start, int endExclusive,
                                         double contentTarget, double header) {
        List<Double> widths = new ArrayList<>(endExclusive - start);
        if (endExclusive <= start) {
            return widths;
        }
        double sum = 0.0;
        for (int i = start; i < endExclusive; i++) {
            sum += baseWidths.get(i);
        }
        double scale = (sum > 0 && contentTarget > 0) ? contentTarget / sum : 1.0;
        for (int i = start; i < endExclusive; i++) {
            widths.add(baseWidths.get(i) * scale);
        }
        if (header > 0) {
            widths.set(0, widths.get(0) + header);
        }
        return widths;
    }

    /**
     * The global pixels-per-quarter scale for STRIP (time-proportional) layout:
     * the largest ratio of any element's minimum slot to its duration, so even
     * the shortest / widest-glyph note still gets at least its minimum advance
     * while every note's width stays strictly proportional to its duration.
     */
    public static double stripPixelsPerQuarter(List<PartInfo> parts, LayoutOptions options) {
        double gap = options.staffLineGap();
        double ppq = MIN_NOTE_ADVANCE_GAPS * gap;
        for (PartInfo p : parts) {
            for (Measure measure : p.measures()) {
                for (MusicElement element : measure.elements()) {
                    double durQ = element.duration().inQuarters();
                    if (durQ > 0) {
                        ppq = Math.max(ppq, minSlotFor(element, gap) / durQ);
                    }
                }
            }
        }
        // The bare minimum-slot scale packs notes tightly; the configurable
        // spacing factor gives the strip more breathing room. Purely a uniform
        // scale - it does not affect proportionality.
        return ppq * options.stripSpacingFactor();
    }

    /**
     * Duration (in quarters) of the last time-consuming element on the given
     * staff of a measure - i.e. the note/rest whose trailing whitespace runs up
     * to the next downbeat. Zero when the measure has no timed content.
     */
    public static double lastTimedDurationQuarters(Measure measure, int staffNumber, int staveCount) {
        double lastDur = 0.0;
        for (MusicElement element : ScoreAnalysis.filterElementsForStaff(measure.elements(), staffNumber, staveCount)) {
            if (!ScoreAnalysis.isTimed(element)) {
                continue;
            }
            double d = element.duration().inQuarters();
            if (d > 0) {
                lastDur = d;
            }
        }
        return lastDur;
    }

    /**
     * Time-proportional (STRIP) placement: x is a global linear function of
     * absolute musical onset, so equal durations get equal width and the
     * cursor moves at constant speed across the whole score (including over
     * barlines). The single per-note offset from the staff's time-zero keeps
     * every downbeat the same small distance past its barline.
     */
    public static double[] stripPositions(List<MusicElement> timedElements, double timeZeroX,
                                   double measureStartQ, double pixelsPerQuarter) {
        double[] timedX = new double[timedElements.size()];
        double onsetInMeasure = 0.0;
        for (int i = 0; i < timedElements.size(); i++) {
            timedX[i] = timeZeroX + (measureStartQ + onsetInMeasure) * pixelsPerQuarter;
            onsetInMeasure += Math.max(0.0, timedElements.get(i).duration().inQuarters());
        }
        return timedX;
    }

    /**
     * Notation (PAGE) placement: share {@code available} between the
     * elements by their {@link #noteWidthWeight}, never giving any element
     * less than its {@link #minSlotFor minimum slot}, then shift each note
     * right within its slot by the room its accidental and grace notes need
     * on the left.
     */
    public static double[] pagePositions(List<MusicElement> timedElements, double contentStart,
                                  double available, double gap) {
        double[] timedX = new double[timedElements.size()];
        double[] weights = new double[timedElements.size()];
        for (int i = 0; i < timedElements.size(); i++) {
            double w = noteWidthWeight(timedElements.get(i));
            if (w <= 0) {
                w = 1.0;
            }
            weights[i] = w;
        }
        capWeightRatio(weights);
        double sumWeights = 0.0;
        for (double w : weights) {
            sumWeights += w;
        }
        double startCursor = contentStart;
        for (int i = 0; i < timedElements.size(); i++) {
            double slotWidth = sumWeights > 0
                    ? available * (weights[i] / sumWeights)
                    : available / timedElements.size();
            double slot = minSlotFor(timedElements.get(i), gap);
            if (slotWidth < slot) {
                slotWidth = slot;
            }
            timedX[i] = startCursor + leftShift(timedElements.get(i), gap);
            startCursor += slotWidth;
        }
        return timedX;
    }

    /**
     * Room a note needs to the left of its notehead, inside its own slot,
     * for its accidental and its grace-note run.
     */
    private static double leftShift(MusicElement element, double gap) {
        double leftShift = ScoreAnalysis.hasAccidental(element)
                ? (ScoreAnalysis.isDoubleAccidental(element)
                    ? DOUBLE_ACCIDENTAL_RESERVE_GAPS : ACCIDENTAL_RESERVE_GAPS) * gap
                : 0.0;
        int graceNotes = ScoreAnalysis.graceNoteCount(element);
        if (graceNotes > 0) {
            leftShift += GRACE_NOTE_SPACING_GAPS * graceNotes * gap;
        }
        return leftShift;
    }

    private static double measureWeight(Measure measure) {
        List<MusicElement> elements = measure.elements();
        double[] weights = new double[elements.size()];
        for (int i = 0; i < elements.size(); i++) {
            weights[i] = noteWidthWeight(elements.get(i));
        }
        capWeightRatio(weights);
        double sum = 0.0;
        for (double w : weights) {
            sum += w;
        }
        return sum > 0 ? sum : 1.0;
    }

    /**
     * Clamp every positive value in {@code weights} to at most {@link
     * #MAX_WEIGHT_RATIO} times the smallest positive value present.
     *
     * <p>Note width weight grows with duration ({@code d^0.6}), which is
     * correct engraving practice — but within one measure this can swing
     * far enough that a single very long note (e.g. a triple-dotted quarter)
     * claims several times the horizontal room a neighbouring very short
     * note (e.g. a 32nd) gets, reading as a wide empty gap next to a
     * cramped one rather than a smooth "longer note, more room" gradient.
     * Capping the ratio keeps the direction of the effect (longer still
     * gets more space) while bounding how extreme the spread can get.
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
     * Absolute floor for a measure's nominal width, informed by its note
     * density rather than a single flat constant. A measure packs
     * {@code n} time-consuming elements, each of which needs at least
     * {@code MIN_NOTE_ADVANCE_GAPS * gap} of horizontal room (mirroring the
     * per-note clamp in {@link #pagePositions}) plus the two side gaps
     * ({@code contentStart}/right padding) every measure reserves.
     *
     * <p>Without this, a system full of equally dense measures (e.g. a
     * continuous tuplet accompaniment) would each get an equal — but for
     * their content, far too small — proportional share of the system
     * width, and the per-note safety rail would then force massive overflow
     * past the barlines instead of the row simply breaking sooner.
     */
    private static double measureContentMinWidth(Measure measure, LayoutOptions options) {
        double gap = options.staffLineGap();
        int count = 0;
        double contentFloor = 2 * gap;
        for (MusicElement element : measure.elements()) {
            if (ScoreAnalysis.isTimed(element)) {
                count++;
                contentFloor += minSlotFor(element, gap);
            }
        }
        if (count == 0) {
            return options.measureMinWidth();
        }
        return Math.max(options.measureMinWidth(), contentFloor);
    }

    /**
     * Minimum horizontal slot an element needs, in pixels: normally just
     * {@code MIN_NOTE_ADVANCE_GAPS * gap}, but widened for augmentation dots
     * so the dot(s) — drawn starting at {@code noteX + 1.2 * gap} and spaced
     * {@code 0.6 * gap} apart, see {@code StaffRowEngraver} — have room
     * before the next element's slot begins, and for a grace-note run so it
     * fits within the note's own slot.
     */
    private static double minSlotFor(MusicElement element, double gap) {
        double slot = MIN_NOTE_ADVANCE_GAPS * gap;
        int dots = ScoreAnalysis.dotCount(element);
        if (dots > 0) {
            double dotsExtent = gap * (1.2 + (dots - 1) * 0.6 + DOT_TRAILING_PAD_GAPS);
            slot = Math.max(slot, dotsExtent);
        }
        int graceNotes = ScoreAnalysis.graceNoteCount(element);
        if (graceNotes > 0) {
            double graceExtent = gap * (GRACE_NOTE_SPACING_GAPS * graceNotes + 1.0);
            slot = Math.max(slot, graceExtent);
        }
        return slot;
    }

    /**
     * Horizontal width weight for a single measure element, following the
     * traditional sub-linear duration curve used by OSMD and printed
     * engraving practice: {@code width(d) ∝ d^α} with {@code α ≈ 0.6}.
     * Additional weight is reserved for elements carrying an accidental or
     * augmentation dots so the extra glyphs don't collide with neighbours.
     *
     * <p>Zero-duration elements (directions, harmony) return {@code 0} —
     * they don't contribute to intra-measure stepping and are anchored to
     * the x of the next time-consuming element.
     */
    private static double noteWidthWeight(MusicElement e) {
        double d = e.duration().inQuarters();
        double base = d <= 0 ? 0.0 : Math.pow(d, NOTE_WIDTH_ALPHA);
        double extras = 0.0;
        if (ScoreAnalysis.hasAccidental(e)) {
            extras += ACCIDENTAL_RESERVE_WEIGHT;
        }
        int dots = ScoreAnalysis.dotCount(e);
        if (dots > 0) {
            extras += DOT_RESERVE_WEIGHT * dots;
        }
        int graceNotes = ScoreAnalysis.graceNoteCount(e);
        if (graceNotes > 0) {
            extras += GRACE_NOTE_RESERVE_WEIGHT * graceNotes;
        }
        return base + extras;
    }
}
