package com.sheetmusic4j.engraving;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.sheetmusic4j.core.model.Accidental;
import com.sheetmusic4j.core.model.Articulation;
import com.sheetmusic4j.core.model.Attributes;
import com.sheetmusic4j.core.model.Beam;
import com.sheetmusic4j.core.model.Chord;
import com.sheetmusic4j.core.model.Clef;
import com.sheetmusic4j.core.model.ClefSign;
import com.sheetmusic4j.core.model.Creator;
import com.sheetmusic4j.core.model.Direction;
import com.sheetmusic4j.core.model.DirectionType;
import com.sheetmusic4j.core.model.DynamicMark;
import com.sheetmusic4j.core.model.GroupSymbol;
import com.sheetmusic4j.core.model.Harmony;
import com.sheetmusic4j.core.model.KeySignature;
import com.sheetmusic4j.core.model.Lyric;
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
import com.sheetmusic4j.core.model.TimeModification;
import com.sheetmusic4j.core.model.TimeSignature;
import com.sheetmusic4j.core.model.Tuplet;
import com.sheetmusic4j.engraving.glyph.Glyph;
import com.sheetmusic4j.engraving.glyph.MarkingCategory;
import com.sheetmusic4j.engraving.layout.*;
import com.sheetmusic4j.engraving.placement.*;

/**
 * Turns a {@link Score} into a framework-agnostic {@link LayoutResult}.
 *
 * <p>Layout pipeline:
 * <ul>
 *   <li>lays every part out as one or more staves (multi-staff piano grand
 *       staff supported),</li>
 *   <li>packs measures into one or more {@link SystemLayout systems}, breaking
 *       the row whenever the next measure would overflow the available
 *       {@link LayoutOptions#systemWidth() staff width},</li>
 *   <li>aligns measure boundaries across parts by computing a shared
 *       per-measure width from the widest weight seen across all parts,</li>
 *   <li>re-emits the clef (and current key signature, and time signature only
 *       when it just changed) at the start of every new system,</li>
 *   <li>emits real per-digit time signature glyphs based on
 *       {@link Attributes#timeSignature()},</li>
 *   <li>emits key-signature accidentals after the clef,</li>
 *   <li>emits {@link Glyph#STEM_UP}/{@link Glyph#STEM_DOWN} placements for
 *       notes whose duration is shorter than a whole note, with direction
 *       chosen by staff position (down when above the middle line, up
 *       otherwise),</li>
 *   <li>emits flags for unbeamed short notes and beam segments for beamed
 *       groups,</li>
 *   <li>emits accidentals, augmentation dots, and tie arcs when the note
 *       carries them,</li>
 *   <li>sizes measures proportionally to the sum of their note/rest durations,
 *       clamped by {@link LayoutOptions#measureMinWidth()}.</li>
 * </ul>
 */
public final class Engraver {

    /**
     * Half-staff-step position of the middle staff line for a five-line staff.
     */
    private static final int MIDDLE_LINE_STAFF_STEP = 4;

    /**
     * Number of horizontal lines on a standard staff.
     */
    private static final int STAFF_LINES = 5;
    private static final double STEM_LENGTH_GAPS = 3.5;

    /**
     * Additional vertical space (in staff-line gaps) reserved below the last
     * staff of a part when that part carries at least one verse-1 lyric.
     */
    private static final double LYRIC_RESERVE_GAPS = 3.2;

    /**
     * Additional vertical space (in staff-line gaps) reserved below the last
     * staff of a part when that part carries at least one direction placed
     * below the staff (e.g. a dynamic). Sized to match {@link
     * #LYRIC_RESERVE_GAPS} so lyrics and dynamics share the same slot for
     * the MVP; a fine-grained collision policy is a follow-up.
     */
    private static final double DIRECTION_BELOW_RESERVE_GAPS = 3.2;

    /**
     * Additional vertical space (in staff-line gaps) reserved above the top
     * staff of a system when at least one part in that system carries a
     * direction placed above the staff.
     */
    private static final double DIRECTION_ABOVE_RESERVE_GAPS = 3.0;

    /**
     * Additional vertical space (in staff-line gaps) reserved above the top
     * staff of a system when at least one part in that system carries a
     * chord symbol. When both {@link #DIRECTION_ABOVE_RESERVE_GAPS above
     * directions} and chord symbols are present in the same row we sum the
     * two reserves so the two categories don't visually overlap.
     */
    private static final double HARMONY_ABOVE_RESERVE_GAPS = 2.0;

    /**
     * Distance (in staff-line gaps) from the top staff line to the baseline
     * of a chord-symbol label. Chord symbols conventionally sit closer to the
     * staff than tempo/words directions so performers can read them alongside
     * the notes.
     */
    private static final double HARMONY_OFFSET_GAPS = 3.5;

    /**
     * Multiplier applied to the staff-line gap to derive the chord-symbol
     * font size.
     */
    private static final double HARMONY_FONT_SIZE_GAPS = 1.6;

    /**
     * Distance (in staff-line gaps) from a staff to a direction's baseline
     * anchor. Words/metronome sit above; dynamics sit below.
     */
    private static final double DIRECTION_OFFSET_GAPS = 1.5;

    /**
     * Half the maximum opening height (in staff-line gaps) of a crescendo/
     * diminuendo hairpin.
     */
    private static final double HAIRPIN_HALF_HEIGHT_GAPS = 0.8;

    /**
     * Extra clearance (in staff-line gaps) above the highest notehead in a
     * tuplet run at which its number/bracket is drawn.
     */
    private static final double TUPLET_ABOVE_GAP = 2.0;

    /**
     * Multiplier applied to the staff-line gap to derive the direction text
     * font size (words / metronome).
     */
    private static final double DIRECTION_FONT_SIZE_GAPS = 1.6;

    /**
     * Multiplier applied to the staff-line gap to derive the rehearsal-mark
     * font size. Slightly larger than words to stand out visually alongside
     * the box outline.
     */
    private static final double REHEARSAL_FONT_SIZE_GAPS = 1.8;

    /**
     * Multiplier applied to the staff-line gap to derive the lyric font size.
     */
    private static final double LYRIC_FONT_SIZE_GAPS = 1.4;

    /**
     * Multiplier applied to the staff-line gap to derive the part-label font
     * size. Matches {@link #LYRIC_FONT_SIZE_GAPS} so both share a visual
     * weight class.
     */
    private static final double PART_LABEL_FONT_SIZE_GAPS = 1.4;

    /**
     * Horizontal spacing (in staff-line gaps) between adjacent bracket
     * columns emitted by {@code <part-group>}. Each column sits
     * {@code (depthFromInside + 1) * gap * BRACKET_COLUMN_STEP_GAPS} to the
     * left of {@code contentLeft}.
     */
    private static final double BRACKET_COLUMN_STEP_GAPS = 1.2;

    /**
     * Character-to-em ratio used by both {@code ScorePainter}'s alignment
     * fallback and {@link #computeLabelReserve}. Keep in sync with the value
     * in {@code ScorePainter.drawText}.
     */
    private static final double LABEL_CHAR_WIDTH_RATIO = 0.55;

    /**
     * Small trailing gap (in staff-line gaps) added after the longest label
     * so labels never abut the system left barline.
     */
    private static final double PART_LABEL_TRAILING_PAD_GAPS = 1.2;

    /**
     * Distance (in staff-line gaps) from the bottom staff line to the baseline
     * of the verse-1 lyric row.
     */
    private static final double LYRIC_BASELINE_OFFSET_GAPS = 2.2;

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
     */
    private static final double ACCIDENTAL_RESERVE_GAPS = 0.9;

    public LayoutResult layout(Score score, LayoutOptions options) {
        List<TextPlacement> texts = new ArrayList<>();
        double titleBlockHeight = layoutTitleBlock(score, options, texts);

        if (score.parts().isEmpty()) {
            SystemLayout empty = new SystemLayout(0, 0, options.systemWidth(), List.of());
            return new LayoutResult(List.of(empty), texts, options.systemWidth(),
                    options.topMargin() + titleBlockHeight + options.staffHeight());
        }

        int[] groupNestingDepths = computeGroupNestingDepths(score);
        int maxGroupNestingDepth = maxOrMinusOne(groupNestingDepths);
        double labelReserve = computeLabelReserve(score, options, maxGroupNestingDepth);
        double contentLeft = options.leftMargin() + labelReserve;
        double staffWidth = options.systemWidth() - contentLeft - options.rightMargin();

        List<PartInfo> parts = new ArrayList<>(score.parts().size());
        int measureCount = 0;
        for (Part part : score.parts()) {
            PartInfo info = PartInfo.of(part);
            parts.add(info);
            measureCount = Math.max(measureCount, part.measures().size());
        }
        if (measureCount == 0) {
            SystemLayout empty = new SystemLayout(0, 0, options.systemWidth(), List.of());
            return new LayoutResult(List.of(empty), texts, options.systemWidth(),
                    options.topMargin() + titleBlockHeight + options.staffHeight());
        }

        double firstSystemHeader = maxHeaderAdvanceAtRowStart(parts, 0, options);
        List<Double> sharedWeights = sharedMeasureWeights(parts, measureCount);
        List<Double> sharedMinWidths = sharedMeasureMinWidths(parts, measureCount, options);
        List<Double> baseWidths = distributeWidths(
                sharedWeights, Math.max(1.0, staffWidth - firstSystemHeader), sharedMinWidths);
        List<int[]> rows = computeRowRanges(baseWidths, staffWidth, firstSystemHeader, parts, options);

        List<SystemLayout> systems = new ArrayList<>(rows.size());
        double y = options.topMargin() + titleBlockHeight;
        for (int rowIdx = 0; rowIdx < rows.size(); rowIdx++) {
            int[] range = rows.get(rowIdx);
            int start = range[0];
            int endExclusive = range[1];

            double rowHeader = maxHeaderAdvanceAtRowStart(parts, start, options);
            List<Double> rowWidths = stretchRowWidths(baseWidths, start, endExclusive,
                    Math.max(0, staffWidth - rowHeader), rowHeader);

            List<StaffLayout> stavesForRow = new ArrayList<>();
            List<BracketPlacement> bracketsForRow = new ArrayList<>();
            // Per-part first/last staff layouts for this row, indexed by
            // score.parts() position. Used to resolve per-group bracket
            // spans after all parts have been laid out.
            StaffLayout[] partFirstStaves = new StaffLayout[score.parts().size()];
            StaffLayout[] partLastStaves = new StaffLayout[score.parts().size()];
            double staffTop = y;
            boolean rowHasAboveDirections = false;
            boolean rowHasHarmony = false;
            for (PartInfo p : parts) {
                if (!rowHasAboveDirections && p.hasDirectionsAboveInRange(start, endExclusive)) {
                    rowHasAboveDirections = true;
                }
                if (!rowHasHarmony && p.hasHarmonyInRange(start, endExclusive)) {
                    rowHasHarmony = true;
                }
                if (rowHasAboveDirections && rowHasHarmony) {
                    break;
                }
            }
            if (rowHasAboveDirections) {
                staffTop += options.staffLineGap() * DIRECTION_ABOVE_RESERVE_GAPS;
            }
            if (rowHasHarmony) {
                staffTop += options.staffLineGap() * HARMONY_ABOVE_RESERVE_GAPS;
            }
            for (int partIdx = 0; partIdx < parts.size(); partIdx++) {
                PartInfo p = parts.get(partIdx);
                StaffLayout firstStaffOfPart = null;
                StaffLayout lastStaffOfPart = null;
                for (int staffIdx = 0; staffIdx < p.staveCount(); staffIdx++) {
                    StaffLayout sl = layoutStaffRow(
                            p, staffIdx, contentLeft, staffTop,
                            options, start, endExclusive, rowWidths, rowIdx == 0, texts);
                    stavesForRow.add(sl);
                    if (firstStaffOfPart == null) {
                        firstStaffOfPart = sl;
                    }
                    lastStaffOfPart = sl;
                    staffTop += options.staffHeight() + options.staffSpacing();
                }
                if (firstStaffOfPart != null) {
                    emitPartLabel(texts, p.part(), rowIdx,
                            firstStaffOfPart, lastStaffOfPart, options);
                    if (p.staveCount() > 1) {
                        bracketsForRow.add(new BracketPlacement(
                                contentLeft - options.staffLineGap() * 0.8,
                                firstStaffOfPart.lineY(0),
                                lastStaffOfPart.lineY(STAFF_LINES - 1),
                                BracketPlacement.BracketShape.BRACE));
                    }
                    partFirstStaves[partIdx] = firstStaffOfPart;
                    partLastStaves[partIdx] = lastStaffOfPart;
                }
                double belowReserve = 0.0;
                if (p.hasLyrics()) {
                    belowReserve = Math.max(belowReserve,
                            options.staffLineGap() * LYRIC_RESERVE_GAPS);
                }
                if (p.hasDirectionsBelow()) {
                    belowReserve = Math.max(belowReserve,
                            options.staffLineGap() * DIRECTION_BELOW_RESERVE_GAPS);
                }
                staffTop += belowReserve;
            }
            List<SystemBarline> barlinesForRow = new ArrayList<>();
            if (!stavesForRow.isEmpty()) {
                StaffLayout firstStaff = stavesForRow.get(0);
                StaffLayout lastStaff = stavesForRow.get(stavesForRow.size() - 1);
                barlinesForRow.add(new SystemBarline(
                        contentLeft,
                        firstStaff.lineY(0),
                        lastStaff.lineY(STAFF_LINES - 1),
                        SystemBarline.LineStyle.THIN));
            }
            emitGroupBracketsAndBarlines(score.partGroups(), groupNestingDepths,
                    maxGroupNestingDepth, partFirstStaves, partLastStaves,
                    contentLeft, options, rowIdx, bracketsForRow, barlinesForRow, texts);
            systems.add(new SystemLayout(0, y, options.systemWidth(),
                    stavesForRow, barlinesForRow, bracketsForRow));
            y = staffTop;
            }

        double height = y - options.staffSpacing() + options.rightMargin();
        return new LayoutResult(systems, texts, options.systemWidth(), height);
        }

        /**
        * Emit the score-level title / subtitle text placements at the top of the
        * page and return the total vertical space they consume (0 when the
        * score carries no metadata).
        *
        * <p>Titles are centered horizontally on the system. The block sits above
        * the first system's staves and pushes them down by the returned height.
        */
        private static double layoutTitleBlock(Score score, LayoutOptions options, List<TextPlacement> texts) {
        double gap = options.staffLineGap();
        double centerX = options.systemWidth() / 2.0;
        double y = options.topMargin();
        double consumed = 0.0;

        String workTitle = score.workTitle().orElse(null);
        if (workTitle != null && !workTitle.isBlank()) {
            double fontSize = gap * 2.4;
            texts.add(new TextPlacement(workTitle, centerX, y + fontSize,
                    fontSize, TextPlacement.Align.CENTER, MarkingCategory.TITLE));
            double advance = fontSize * 1.2;
            y += advance;
            consumed += advance;
        }
        String movement = score.movementTitle().orElse(null);
        if (movement != null && !movement.isBlank() && !movement.equals(workTitle)) {
            double fontSize = gap * 1.6;
            texts.add(new TextPlacement(movement, centerX, y + fontSize,
                    fontSize, TextPlacement.Align.CENTER, MarkingCategory.SUBTITLE));
            double advance = fontSize * 1.4;
            y += advance;
            consumed += advance;
        }

        // Creator rows: composer/arranger/transcriber to the right, lyricist/
        // poet/translator to the left; any other role stacks left below.
        List<Creator> creators = score.creators();
        if (!creators.isEmpty()) {
            List<Creator> rightColumn = new ArrayList<>();
            List<Creator> leftColumn = new ArrayList<>();
            for (Creator creator : creators) {
                switch (creator.role()) {
                    case "composer", "arranger", "transcriber" -> rightColumn.add(creator);
                    case "lyricist", "poet", "translator" -> leftColumn.add(creator);
                    default -> leftColumn.add(creator);
                }
            }
            double fontSize = gap * 1.1;
            double leftX = options.leftMargin();
            double rightX = options.systemWidth() - options.rightMargin();
            int rowCount = Math.max(rightColumn.size(), leftColumn.size());
            for (int i = 0; i < rowCount; i++) {
                double baselineY = y + fontSize;
                if (i < rightColumn.size()) {
                    texts.add(new TextPlacement(rightColumn.get(i).name(), rightX, baselineY,
                            fontSize, TextPlacement.Align.RIGHT, MarkingCategory.CREATOR));
                }
                if (i < leftColumn.size()) {
                    texts.add(new TextPlacement(leftColumn.get(i).name(), leftX, baselineY,
                            fontSize, TextPlacement.Align.LEFT, MarkingCategory.CREATOR));
                }
                double advance = fontSize * 1.4;
                y += advance;
                consumed += advance;
            }
        }

        if (consumed > 0) {
            // A little breathing space between the title block and the first staff.
            consumed += gap;
        }
        return consumed;
        }

    /**
     * Per-part immutable metadata plus a per-measure trace of the clef/key/time
     * signature state (the state effective from that measure onwards).
     */
    private static final class PartInfo {
        private final Part part;
        private final int staveCount;
        private final Clef[][] clefsPerMeasure; // [measureIndex][staffIndex]
        private final KeySignature[] keyPerMeasure;
        private final TimeSignature[] timePerMeasure;
        private final boolean hasLyrics;
        private final boolean[] hasDirectionsAbovePerMeasure;
        private final boolean hasDirectionsBelow;
        private final boolean[] hasHarmonyPerMeasure;

        private PartInfo(Part part, int staveCount,
                         Clef[][] clefs, KeySignature[] keys, TimeSignature[] times,
                         boolean hasLyrics,
                         boolean[] hasDirectionsAbovePerMeasure,
                         boolean hasDirectionsBelow,
                         boolean[] hasHarmonyPerMeasure) {
            this.part = part;
            this.staveCount = staveCount;
            this.clefsPerMeasure = clefs;
            this.keyPerMeasure = keys;
            this.timePerMeasure = times;
            this.hasLyrics = hasLyrics;
            this.hasDirectionsAbovePerMeasure = hasDirectionsAbovePerMeasure;
            this.hasDirectionsBelow = hasDirectionsBelow;
            this.hasHarmonyPerMeasure = hasHarmonyPerMeasure;
        }

        boolean hasLyrics() {
            return hasLyrics;
        }

        boolean hasDirectionsBelow() {
            return hasDirectionsBelow;
        }

        boolean hasDirectionsAboveInRange(int start, int endExclusive) {
            int upper = Math.min(endExclusive, hasDirectionsAbovePerMeasure.length);
            for (int i = Math.max(0, start); i < upper; i++) {
                if (hasDirectionsAbovePerMeasure[i]) {
                    return true;
                }
            }
            return false;
        }

        boolean hasHarmonyInRange(int start, int endExclusive) {
            int upper = Math.min(endExclusive, hasHarmonyPerMeasure.length);
            for (int i = Math.max(0, start); i < upper; i++) {
                if (hasHarmonyPerMeasure[i]) {
                    return true;
                }
            }
            return false;
        }

        Part part() {
            return part;
        }

        int staveCount() {
            return staveCount;
        }

        Clef clefAt(int measureIdx, int staffIdx) {
            if (clefsPerMeasure.length == 0) {
                return Clef.treble();
            }
            int m = Math.min(measureIdx, clefsPerMeasure.length - 1);
            return clefsPerMeasure[m][Math.min(staffIdx, staveCount - 1)];
        }

        KeySignature keyAt(int measureIdx) {
            if (keyPerMeasure.length == 0) {
                return KeySignature.cMajor();
            }
            return keyPerMeasure[Math.min(measureIdx, keyPerMeasure.length - 1)];
        }

        TimeSignature timeAt(int measureIdx) {
            if (timePerMeasure.length == 0) {
                return null;
            }
            return timePerMeasure[Math.min(measureIdx, timePerMeasure.length - 1)];
        }

        /**
         * Whether the time signature effective at {@code measureIdx} differs
         * from the one effective at the previous measure. Used to decide
         * whether to re-emit the time signature at the start of a new system.
         */
        boolean timeChangedAt(int measureIdx) {
            if (measureIdx <= 0 || timePerMeasure.length == 0) {
                return timeAt(measureIdx) != null;
            }
            TimeSignature previous = timePerMeasure[Math.min(measureIdx - 1, timePerMeasure.length - 1)];
            TimeSignature current = timeAt(measureIdx);
            if (previous == null) {
                return current != null;
            }
            if (current == null) {
                return false;
            }
            return previous.beats() != current.beats() || previous.beatType() != current.beatType();
        }

        static PartInfo of(Part part) {
            int staveCount = determineStaveCount(part);
            List<Clef> defaults = defaultClefsFor(part, staveCount);

            int n = part.measures().size();
            Clef[][] clefs = new Clef[n][staveCount];
            KeySignature[] keys = new KeySignature[n];
            TimeSignature[] times = new TimeSignature[n];

            Clef[] currentClefs = new Clef[staveCount];
            for (int i = 0; i < staveCount; i++) {
                currentClefs[i] = defaults.get(i);
            }
            KeySignature currentKey = KeySignature.cMajor();
            TimeSignature currentTime = null;

            for (int idx = 0; idx < n; idx++) {
                Measure measure = part.measures().get(idx);
                if (measure.attributes().isPresent()) {
                    Attributes a = measure.attributes().get();
                    if (!a.clefs().isEmpty()) {
                        for (int i = 0; i < staveCount; i++) {
                            if (i < a.clefs().size()) {
                                currentClefs[i] = a.clefs().get(i);
                            } else if (a.clef().isPresent()) {
                                currentClefs[i] = a.clef().get();
                            }
                        }
                    } else if (a.clef().isPresent()) {
                        for (int i = 0; i < staveCount; i++) {
                            currentClefs[i] = a.clef().get();
                        }
                    }
                    if (a.keySignature().isPresent()) {
                        currentKey = a.keySignature().get();
                    }
                    if (a.timeSignature().isPresent()) {
                        currentTime = a.timeSignature().get();
                    }
                }
                for (int i = 0; i < staveCount; i++) {
                    clefs[idx][i] = currentClefs[i];
                }
                keys[idx] = currentKey;
                times[idx] = currentTime;
                }
                boolean[] aboveMask = new boolean[n];
                boolean belowAny = false;
                boolean[] harmonyMask = new boolean[n];
                for (int idx = 0; idx < n; idx++) {
                Measure measure = part.measures().get(idx);
                for (MusicElement element : measure.elements()) {
                    if (element instanceof Direction direction) {
                        Placement resolved = resolvedPlacement(direction);
                        if (resolved == Placement.ABOVE) {
                            aboveMask[idx] = true;
                        } else if (resolved == Placement.BELOW) {
                            belowAny = true;
                        }
                    } else if (element instanceof Harmony) {
                        harmonyMask[idx] = true;
                    }
                }
                }
                return new PartInfo(part, staveCount, clefs, keys, times, scanHasLyrics(part),
                    aboveMask, belowAny, harmonyMask);
                }

            /**
            * Whether the part contains at least one verse-1 lyric with non-empty
            * text, on any note (including the primary member of a chord).
            */
            private static boolean scanHasLyrics(Part part) {
            for (Measure measure : part.measures()) {
                for (MusicElement element : measure.elements()) {
                    Note note = lyricCarrier(element);
                    if (note == null) {
                        continue;
                    }
                    for (Lyric lyric : note.lyrics()) {
                        if (lyric.verse() == 1 && !lyric.text().isEmpty()) {
                            return true;
                        }
                    }
                }
            }
            return false;
            }
            }

            /**
            * Extract the lyric-carrying note from a measure element. For a plain
            * {@link Note} that is the note itself; for a {@link Chord} MusicXML
            * convention puts lyrics on the primary (first) note; rests do not
            * carry lyrics.
            */
            private static Note lyricCarrier(MusicElement element) {
            if (element instanceof Note note) {
            return note;
            }
            if (element instanceof Chord chord && !chord.notes().isEmpty()) {
            return chord.notes().get(0);
            }
            return null;
            }

    /**
     * Number of staves declared by the first measure of the part that carries an
     * {@code Attributes#staves()} value.
     */
    private static int determineStaveCount(Part part) {
        for (Measure measure : part.measures()) {
            if (measure.attributes().isPresent()) {
                Attributes attr = measure.attributes().get();
                if (attr.staves().isPresent()) {
                    return Math.max(1, attr.staves().get());
                }
            }
        }
        return 1;
    }

    /**
     * Initial clef per staff, taken from the first measure's clef list when
     * available and falling back to standard treble/bass grand-staff defaults.
     */
    private static List<Clef> defaultClefsFor(Part part, int staveCount) {
        for (Measure measure : part.measures()) {
            if (measure.attributes().isPresent()) {
                Attributes attr = measure.attributes().get();
                if (!attr.clefs().isEmpty()) {
                    List<Clef> clefs = new ArrayList<>(staveCount);
                    for (int i = 0; i < staveCount; i++) {
                        if (i < attr.clefs().size()) {
                            clefs.add(attr.clefs().get(i));
                        } else {
                            clefs.add(i == 0 ? Clef.treble() : Clef.bass());
                        }
                    }
                    return clefs;
                }
            }
        }
        List<Clef> defaults = new ArrayList<>(staveCount);
        for (int i = 0; i < staveCount; i++) {
            defaults.add(i == 0 ? Clef.treble() : Clef.bass());
        }
        return defaults;
    }

    /**
     * Compute the horizontal space the painter needs at the head of a staff
     * for the given clef + optional key signature + optional time signature.
     *
     * <p>The value must match the advances used by
     * {@link #placeKeySignature} and {@link #placeTimeSignature} plus the
     * clef reservation applied in {@link #layoutStaffRow}. Callers use it to
     * (a) size the header block, and (b) determine how much space to reserve
     * before the row's first measure.
     */
    static double headerAdvance(Clef clef, KeySignature key, TimeSignature time, LayoutOptions options) {
        double gap = options.staffLineGap();
        // Left padding + clef block. gap*0.5 opening pad + gap*3.5 clef space.
        double advance = gap * 4;
        if (key != null && (key.sharps() != 0 || key.flats() != 0)) {
            int count = Math.min(7, Math.max(key.sharps(), key.flats()));
            double accAdvance = gap * 1.1;
            advance += count * accAdvance + gap * 0.5;
        }
        if (time != null) {
            double digitWidth = gap * 1.4;
            int beatsLen = Integer.toString(time.beats()).length();
            int beatTypeLen = Integer.toString(time.beatType()).length();
            int maxLen = Math.max(beatsLen, beatTypeLen);
            advance += maxLen * digitWidth + gap;
        }
        return advance;
    }

    /**
     * Compute the horizontal space to reserve to the left of every system
     * for part-name / part-abbreviation labels and the bracket column.
     * Returns {@code 0} when no part carries a non-blank name or
     * abbreviation and there are no {@link PartGroup part groups}, so
     * single-part unlabeled scores keep their pre-existing layout.
     *
     * <p>Width estimate for labels matches the
     * {@code 0.55 * fontSize * length} heuristic used by
     * {@code ScorePainter} for right/center alignment, then adds a small
     * padding so labels never abut the system barline. Group-name /
     * abbreviation lengths participate in the same longest-label search.
     * When any group is declared we additionally reserve room for the
     * bracket columns themselves, sized so the outermost column sits
     * {@code (maxGroupNestingDepth + 1) * gap * BRACKET_COLUMN_STEP_GAPS}
     * to the left of {@code contentLeft}.
     *
     * @param score                  the score to inspect
     * @param options                layout options; used for staff-line gap only
     * @param maxGroupNestingDepth   max container count across all groups
     *                               ({@code -1} when the score has none)
     * @return non-negative reserve in layout units
     */
    private static double computeLabelReserve(Score score, LayoutOptions options,
                                              int maxGroupNestingDepth) {
        double gap = options.staffLineGap();
        double fontSize = gap * PART_LABEL_FONT_SIZE_GAPS;
        int longest = 0;
        for (Part part : score.parts()) {
            longest = Math.max(longest, labelLength(part.name()));
            longest = Math.max(longest, labelLength(part.abbreviation()));
        }
        for (PartGroup group : score.partGroups()) {
            longest = Math.max(longest, labelLength(group.name()));
            longest = Math.max(longest, labelLength(group.abbreviation()));
        }
        double labelReserve = 0.0;
        if (longest > 0) {
            labelReserve = longest * LABEL_CHAR_WIDTH_RATIO * fontSize
                    + gap * PART_LABEL_TRAILING_PAD_GAPS;
        }
        double bracketReserve = 0.0;
        if (maxGroupNestingDepth >= 0) {
            bracketReserve = (maxGroupNestingDepth + 1) * gap * BRACKET_COLUMN_STEP_GAPS;
        }
        return labelReserve + bracketReserve;
    }

    /**
     * Compute the nesting depth of every {@link PartGroup} in
     * {@link Score#partGroups()}. A group's depth equals the number of
     * other groups whose part range fully contains it — outermost groups
     * have depth 0 and innermost groups have the maximum depth.
     *
     * @param score the score to inspect
     * @return array parallel to {@code score.partGroups()}; empty when the
     *         score carries no groups
     */
    private static int[] computeGroupNestingDepths(Score score) {
        List<PartGroup> groups = score.partGroups();
        int[] depths = new int[groups.size()];
        for (int i = 0; i < groups.size(); i++) {
            int count = 0;
            for (int j = 0; j < groups.size(); j++) {
                if (i != j && groups.get(j).contains(groups.get(i))) {
                    count++;
                }
            }
            depths[i] = count;
        }
        return depths;
    }

    /**
     * Maximum entry of the array, or {@code -1} when the array is empty.
     */
    private static int maxOrMinusOne(int[] values) {
        int max = -1;
        for (int v : values) {
            if (v > max) {
                max = v;
            }
        }
        return max;
    }

    /**
     * Emit bracket placements, optional group barlines, and group-name
     * labels for every {@link PartGroup} that intersects the current row.
     * Nesting: outer groups (fewest containers) sit furthest to the left,
     * inner groups sit closer to the staff. A group barline coinciding
     * exactly with the previously-emitted system left barline is skipped
     * to avoid drawing the same line twice.
     *
     * @param groups             all part groups declared on the score
     * @param nestingDepths      per-group container count (see
     *                           {@link #computeGroupNestingDepths(Score)})
     * @param maxNestingDepth    largest value in {@code nestingDepths}
     * @param partFirstStaves    first staff of each part in this row
     *                           (null when the part has no staves in the row)
     * @param partLastStaves     last staff of each part in this row
     * @param contentLeft        x of the staff content edge / system barline
     * @param options            layout options (for the staff-line gap)
     * @param rowIdx             0-based row index (row 0 uses full names)
     * @param brackets           output list of bracket placements
     * @param barlines           output list of system barlines
     * @param texts              output list of text placements
     */
    private static void emitGroupBracketsAndBarlines(List<PartGroup> groups, int[] nestingDepths,
                                                     int maxNestingDepth,
                                                     StaffLayout[] partFirstStaves,
                                                     StaffLayout[] partLastStaves,
                                                     double contentLeft, LayoutOptions options,
                                                     int rowIdx,
                                                     List<BracketPlacement> brackets,
                                                     List<SystemBarline> barlines,
                                                     List<TextPlacement> texts) {
        if (groups.isEmpty()) {
            return;
        }
        double gap = options.staffLineGap();
        double fontSize = gap * PART_LABEL_FONT_SIZE_GAPS;
        // Pre-capture the system left barline (if any) so we can skip a
        // group barline that would draw exactly the same segment.
        SystemBarline systemLeftBarline = barlines.isEmpty() ? null : barlines.get(0);

        for (int i = 0; i < groups.size(); i++) {
            PartGroup group = groups.get(i);
            StaffLayout firstStaff = firstStaffInGroup(group, partFirstStaves);
            StaffLayout lastStaff = lastStaffInGroup(group, partLastStaves);
            if (firstStaff == null || lastStaff == null) {
                continue;
            }
            double topY = firstStaff.lineY(0);
            double bottomY = lastStaff.lineY(STAFF_LINES - 1);
            // Depth from the innermost group inwards: innermost = 0.
            int depthFromInside = maxNestingDepth - nestingDepths[i];
            double columnX = contentLeft - (depthFromInside + 1) * gap * BRACKET_COLUMN_STEP_GAPS;

            BracketPlacement.BracketShape shape = shapeFor(group.symbol());
            if (shape != null) {
                brackets.add(new BracketPlacement(columnX, topY, bottomY, shape));
            }
            if (group.groupBarline()) {
                boolean overlapsSystemBarline = systemLeftBarline != null
                        && Math.abs(systemLeftBarline.x() - contentLeft) < 1e-6
                        && Math.abs(systemLeftBarline.topY() - topY) < 1e-6
                        && Math.abs(systemLeftBarline.bottomY() - bottomY) < 1e-6;
                if (!overlapsSystemBarline) {
                    barlines.add(new SystemBarline(contentLeft, topY, bottomY,
                            SystemBarline.LineStyle.THIN));
                }
            }
            String labelText;
            if (rowIdx == 0) {
                labelText = group.name();
            } else {
                labelText = group.abbreviation() != null ? group.abbreviation() : group.name();
            }
            if (labelText != null && !labelText.isBlank()) {
                double labelY = (topY + bottomY) / 2.0 + fontSize / 2.0;
                texts.add(new TextPlacement(labelText, columnX, labelY,
                        fontSize, TextPlacement.Align.RIGHT, MarkingCategory.PART_LABEL));
            }
        }
    }

    /**
     * Map a MusicXML {@link GroupSymbol} to an engraved
     * {@link BracketPlacement.BracketShape}. Returns {@code null} when the
     * source requested {@link GroupSymbol#NONE}, which suppresses bracket
     * rendering entirely for that group.
     */
    private static BracketPlacement.BracketShape shapeFor(GroupSymbol symbol) {
        return switch (symbol) {
            case BRACKET -> BracketPlacement.BracketShape.BRACKET;
            case BRACE -> BracketPlacement.BracketShape.BRACE;
            case SQUARE -> BracketPlacement.BracketShape.SQUARE;
            case LINE -> BracketPlacement.BracketShape.LINE;
            case NONE -> null;
        };
    }

    /**
     * First staff belonging to any part inside the group's inclusive part
     * range that has a staff in this row.
     */
    private static StaffLayout firstStaffInGroup(PartGroup group, StaffLayout[] partFirstStaves) {
        int end = Math.min(group.endPartIndex(), partFirstStaves.length - 1);
        for (int idx = Math.max(0, group.startPartIndex()); idx <= end; idx++) {
            if (partFirstStaves[idx] != null) {
                return partFirstStaves[idx];
            }
        }
        return null;
    }

    /**
     * Last staff belonging to any part inside the group's inclusive part
     * range that has a staff in this row.
     */
    private static StaffLayout lastStaffInGroup(PartGroup group, StaffLayout[] partLastStaves) {
        int end = Math.min(group.endPartIndex(), partLastStaves.length - 1);
        for (int idx = end; idx >= Math.max(0, group.startPartIndex()); idx--) {
            if (partLastStaves[idx] != null) {
                return partLastStaves[idx];
            }
        }
        return null;
    }

    /**
     * Length of a label for the purposes of {@link #computeLabelReserve}
     * width estimation. Returns {@code 0} for {@code null} or blank strings.
     */
    private static int labelLength(String label) {
        return label == null || label.isBlank() ? 0 : label.length();
    }

    /**
     * Emit the instrument-label {@link TextPlacement} for a part at the
     * given row. Skipped silently when the part carries neither a name nor
     * an abbreviation. On the first row of the layout the full
     * {@code <part-name>} is used; on subsequent rows we prefer
     * {@code <part-abbreviation>} and fall back to the full name when the
     * source did not supply one.
     *
     * @param texts             collector for page-level text placements
     * @param part              the part being labeled
     * @param rowIdx            0-based row index within the layout
     * @param firstStaffOfPart  first staff belonging to the part in this row
     * @param lastStaffOfPart   last staff belonging to the part in this row
     * @param options           layout options; used for font sizing only
     */
    private static void emitPartLabel(List<TextPlacement> texts, Part part, int rowIdx,
                                      StaffLayout firstStaffOfPart, StaffLayout lastStaffOfPart,
                                      LayoutOptions options) {
        String labelText;
        if (rowIdx == 0) {
            labelText = part.name();
        } else {
            labelText = part.abbreviation() != null ? part.abbreviation() : part.name();
        }
        if (labelText == null || labelText.isBlank()) {
            return;
        }
        double gap = options.staffLineGap();
        double fontSize = gap * PART_LABEL_FONT_SIZE_GAPS;
        double top = firstStaffOfPart.lineY(0);
        double bottom = lastStaffOfPart.lineY(STAFF_LINES - 1);
        double labelY = (top + bottom) / 2.0 + fontSize / 2.0;
        texts.add(new TextPlacement(labelText, options.leftMargin(), labelY,
                fontSize, TextPlacement.Align.LEFT, MarkingCategory.PART_LABEL));
    }

    /**
     * Maximum header advance width across every part at the beginning of the
     * given row (i.e. at {@code measureIndex}), using the effective clef of
     * staff 0 for each part.
     */
    private static double maxHeaderAdvanceAtRowStart(List<PartInfo> parts, int measureIndex,
                                                     LayoutOptions options) {
        double max = 0.0;
        for (PartInfo p : parts) {
            if (p.part().measures().isEmpty()) {
                continue;
            }
            int idx = Math.min(measureIndex, p.part().measures().size() - 1);
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
    private static List<Double> sharedMeasureWeights(List<PartInfo> parts, int measureCount) {
        List<Double> weights = new ArrayList<>(measureCount);
        for (int idx = 0; idx < measureCount; idx++) {
            double max = 0.0;
            for (PartInfo p : parts) {
                if (idx < p.part().measures().size()) {
                    max = Math.max(max, measureWeight(p.part().measures().get(idx)));
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
    private static List<Double> sharedMeasureMinWidths(List<PartInfo> parts, int measureCount,
                                                       LayoutOptions options) {
        List<Double> mins = new ArrayList<>(measureCount);
        for (int idx = 0; idx < measureCount; idx++) {
            double max = options.measureMinWidth();
            for (PartInfo p : parts) {
                if (idx < p.part().measures().size()) {
                    max = Math.max(max, measureContentMinWidth(p.part().measures().get(idx), options));
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
    private static List<Double> distributeWidths(List<Double> weights, double totalWidth, List<Double> mins) {
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
     * Greedy row-break: pack measures into rows so the sum of their base
     * widths plus the row's header does not exceed the available staff
     * width. Each row contains at least one measure.
     */
    private static List<int[]> computeRowRanges(List<Double> baseWidths, double staffWidth,
                                                 double firstSystemHeader, List<PartInfo> parts,
                                                 LayoutOptions options) {
        List<int[]> rows = new ArrayList<>();
        int n = baseWidths.size();
        if (n == 0) {
            return rows;
        }
        int start = 0;
        double header = firstSystemHeader;
        double cursor = header;
        for (int i = 0; i < n; i++) {
            double w = baseWidths.get(i);
            if (i > start && cursor + w > staffWidth + 1e-6) {
                rows.add(new int[]{start, i});
                start = i;
                header = maxHeaderAdvanceAtRowStart(parts, start, options);
                cursor = header + w;
            } else {
                cursor += w;
            }
        }
        rows.add(new int[]{start, n});
        return rows;
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
    private static List<Double> stretchRowWidths(List<Double> baseWidths, int start, int endExclusive,
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

    private StaffLayout layoutStaffRow(PartInfo partInfo, int staffIdx, double x, double y,
                                       LayoutOptions options,
                                       int start, int endExclusive, List<Double> rowWidths,
                                       boolean firstRow, List<TextPlacement> texts) {
        Part part = partInfo.part();
        int staveCount = partInfo.staveCount();
        int staffNumber = staffIdx + 1;

        List<MeasureLayout> measureLayouts = new ArrayList<>();
        List<GlyphPlacement> glyphs = new ArrayList<>();
        List<BeamPlacement> beams = new ArrayList<>();
        List<TiePlacement> ties = new ArrayList<>();
        List<SlurPlacement> slurs = new ArrayList<>();
        List<TupletPlacement> tuplets = new ArrayList<>();
        List<HairpinPlacement> hairpins = new ArrayList<>();

        Map<Integer, BeamRun> openBeams = new HashMap<>();
        Map<String, PlacedNote> tieCandidates = new HashMap<>();
        Map<Integer, PlacedNote> slurCandidates = new HashMap<>();
        Map<Integer, TupletRun> tupletCandidates = new HashMap<>();
        Map<Integer, WedgeStart> wedgeCandidates = new HashMap<>();

        double cursorX = x;
        boolean firstMeasureInRow = true;

        for (int idx = start; idx < endExclusive; idx++) {
            if (idx >= part.measures().size()) {
                break;
            }
            Measure measure = part.measures().get(idx);
            Clef currentClef = partInfo.clefAt(idx, staffIdx);
            KeySignature currentKey = partInfo.keyAt(idx);
            TimeSignature currentTime = partInfo.timeAt(idx);

            double measureWidth = rowWidths.get(idx - start);
            measureLayouts.add(new MeasureLayout(measure.number(), cursorX, measureWidth));

            double contentStart = cursorX + options.staffLineGap();
            if (firstMeasureInRow) {
                double clefY = y + clefAnchorLineIndex(currentClef) * options.staffLineGap();
                glyphs.add(new GlyphPlacement(cursorX + options.staffLineGap() * 0.5,
                        clefY, clefGlyph(currentClef), 4));
                contentStart = cursorX + options.staffLineGap() * 4;
                if (currentKey.fifths() != 0) {
                    contentStart = placeKeySignature(glyphs, currentKey, currentClef, contentStart, y, options);
                }
                boolean showTime = (idx == 0 && firstRow) || partInfo.timeChangedAt(idx);
                if (currentTime != null && showTime) {
                    contentStart = placeTimeSignature(glyphs, currentTime, contentStart, y, options);
                }
            }

            List<MusicElement> elements = filterElementsForStaff(measure.elements(), staffNumber, staveCount);
            // Directions and harmony markers occupy zero time but appear in
            // document order among notes/rests/chords. We size the measure
            // only by the time-consuming elements and then map each zero-
            // duration element to the x of the *next* time-consuming element
            // (falling back to the last one, or to the content start).
            List<MusicElement> timedElements = new ArrayList<>(elements.size());
            for (MusicElement element : elements) {
                if (!(element instanceof Direction) && !(element instanceof Harmony)) {
                    timedElements.add(element);
                }
            }
            double available = (cursorX + measureWidth) - contentStart - options.staffLineGap();
            double gap = options.staffLineGap();
            double minSlot = MIN_NOTE_ADVANCE_GAPS * gap;
            double[] timedX = new double[timedElements.size()];
            if (!timedElements.isEmpty()) {
                double[] weights = new double[timedElements.size()];
                double sumWeights = 0.0;
                for (int i = 0; i < timedElements.size(); i++) {
                    double w = noteWidthWeight(timedElements.get(i));
                    if (w <= 0) {
                        w = 1.0;
                    }
                    weights[i] = w;
                    sumWeights += w;
                }
                double startCursor = contentStart;
                for (int i = 0; i < timedElements.size(); i++) {
                    double slotWidth = sumWeights > 0
                            ? available * (weights[i] / sumWeights)
                            : available / timedElements.size();
                    if (slotWidth < minSlot) {
                        slotWidth = minSlot;
                    }
                    double leftShift = hasAccidental(timedElements.get(i))
                            ? ACCIDENTAL_RESERVE_GAPS * gap
                            : 0.0;
                    timedX[i] = startCursor + leftShift;
                    startCursor += slotWidth;
                }
            }
            // Fallback x when the measure has no time-consuming elements at all
            // (rare, but possible for a measure that only carries directions).
            double fallbackX = contentStart;
            for (int i = 0, timedIdx = 0; i < elements.size(); i++) {
                MusicElement element = elements.get(i);
                if (element instanceof Direction direction) {
                    double dirX = anchorX(timedX, timedIdx, fallbackX);
                    if (staffIdx == 0) {
                        placeDirection(texts, glyphs, hairpins, wedgeCandidates, direction, dirX, y, options);
                    }
                } else if (element instanceof Harmony harmony) {
                    double harmonyX = anchorX(timedX, timedIdx, fallbackX);
                    if (staffIdx == 0) {
                        placeHarmony(texts, harmony, harmonyX, y, options);
                    }
                } else {
                    double noteX = timedX[timedIdx++];
                    placeElement(glyphs, beams, ties, slurs, tuplets, openBeams, tieCandidates,
                            slurCandidates, tupletCandidates, element, noteX, y, currentClef, options);
                    if (staffIdx == 0) {
                        placeLyrics(texts, element, noteX, y, options);
                    }
                }
            }

            cursorX += measureWidth;
            firstMeasureInRow = false;
        }

        return new StaffLayout(x, y, cursorX - x, options.staffLineGap(),
                measureLayouts, glyphs, beams, ties, slurs, tuplets, hairpins);
    }

    /**
     * For a multi-staff part, keep only elements that belong to the given
     * staff number. For a single-staff part (or elements with a default
     * staff assignment), all elements pass through.
     */
    private static List<MusicElement> filterElementsForStaff(List<MusicElement> elements, int staffNumber, int totalStaves) {
        if (totalStaves <= 1) {
            return elements;
        }
        List<MusicElement> filtered = new ArrayList<>(elements.size());
        for (MusicElement element : elements) {
            if (elementStaff(element) == staffNumber) {
                filtered.add(element);
            }
        }
        return filtered;
    }

    private static int elementStaff(MusicElement element) {
        if (element instanceof Note note) {
            return note.staff();
        }
        if (element instanceof Chord chord) {
            for (Note note : chord.notes()) {
                return note.staff();
            }
        }
        // Rests and Directions inherit the previous staff assignment; default to staff 1.
        return 1;
    }

    /**
     * Vertical line index (0 = top line, 4 = bottom line) the given clef is
     * anchored on. MusicXML {@code <line>} is counted from the bottom (1-based),
     * so top-index = 5 - line.
     */
    private static int clefAnchorLineIndex(Clef clef) {
        int line = clef.line();
        if (line < 1) {
            line = 1;
        } else if (line > 5) {
            line = 5;
        }
        return 5 - line;
    }

    private static double measureWeight(Measure measure) {
        double sum = 0.0;
        for (MusicElement element : measure.elements()) {
            sum += noteWidthWeight(element);
        }
        return sum > 0 ? sum : 1.0;
    }

    /**
     * Absolute floor for a measure's nominal width, informed by its note
     * density rather than a single flat constant. A measure packs
     * {@code n} time-consuming elements, each of which needs at least
     * {@code MIN_NOTE_ADVANCE_GAPS * gap} of horizontal room (mirroring the
     * per-note clamp in {@code layoutStaffRow}) plus the two side gaps
     * ({@code contentStart}/right padding) every measure reserves.
     *
     * <p>Without this, a system full of equally dense measures (e.g. a
     * continuous tuplet accompaniment) would each get an equal — but for
     * their content, far too small — proportional share of the system
     * width, and the per-note safety rail would then force massive overflow
     * past the barlines instead of the row simply breaking sooner.
     */
    private static double measureContentMinWidth(Measure measure, LayoutOptions options) {
        int count = 0;
        for (MusicElement element : measure.elements()) {
            if (!(element instanceof Direction) && !(element instanceof Harmony)) {
                count++;
            }
        }
        if (count == 0) {
            return options.measureMinWidth();
        }
        double gap = options.staffLineGap();
        double contentFloor = count * MIN_NOTE_ADVANCE_GAPS * gap + 2 * gap;
        return Math.max(options.measureMinWidth(), contentFloor);
    }

    /**
     * Horizontal width weight for a single measure element, following the
     * traditional sub-linear duration curve used by OSMD and printed
     * engraving practice: {@code width(d) ∝ d^α} with {@code α ≈ 0.6}.
     * Additional weight is reserved for elements carrying an accidental or
     * augmentation dots so the extra glyphs don't collide with neighbours.
     *
     * <p>Zero-duration elements ({@link Direction}, {@link Harmony}) return
     * {@code 0} — they don't contribute to intra-measure stepping and are
     * anchored to the x of the next time-consuming element.
     */
    private static double noteWidthWeight(MusicElement e) {
        double d = e.duration().inQuarters();
        double base = d <= 0 ? 0.0 : Math.pow(d, NOTE_WIDTH_ALPHA);
        double extras = 0.0;
        if (hasAccidental(e)) {
            extras += ACCIDENTAL_RESERVE_WEIGHT;
        }
        int dots = dotCount(e);
        if (dots > 0) {
            extras += DOT_RESERVE_WEIGHT * dots;
        }
        return base + extras;
    }

    /**
     * Whether the given element carries a displayed accidental. Mirrors the
     * detection logic in {@link #placeNote}: only an explicit
     * {@link Note#displayedAccidental()} counts. A non-zero chromatic
     * {@code alter} on the pitch is <em>not</em> by itself a signal to show
     * an accidental glyph - {@code alter} just encodes the sounding pitch
     * (e.g. every B-flat in a piece has {@code alter=-1} whether or not the
     * key signature already implies the flat), and well-formed MusicXML
     * always carries an explicit {@code <accidental>} element on notes that
     * should actually display one. For a {@link Chord} the check is true
     * when any member note qualifies; rests never carry accidentals.
     */
    private static boolean hasAccidental(MusicElement e) {
        if (e instanceof Note note) {
            return note.displayedAccidental().isPresent();
        }
        if (e instanceof Chord chord) {
            for (Note note : chord.notes()) {
                if (note.displayedAccidental().isPresent()) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Number of augmentation dots on the element. For a {@link Chord} the
     * maximum across all member notes is returned.
     */
    private static int dotCount(MusicElement e) {
        if (e instanceof Note note) {
            return note.dots();
        }
        if (e instanceof Rest rest) {
            return rest.dots();
        }
        if (e instanceof Chord chord) {
            int max = 0;
            for (Note note : chord.notes()) {
                if (note.dots() > max) {
                    max = note.dots();
                }
            }
            return max;
        }
        return 0;
    }

    private double placeKeySignature(List<GlyphPlacement> glyphs, KeySignature key, Clef clef,
                                     double startX, double staffY, LayoutOptions options) {
        int[] positions = KeySignatureLayout.positions(clef, key);
        Glyph glyph = KeySignatureLayout.glyphFor(key);
        double advance = options.staffLineGap() * 1.1;
        double x = startX;
        for (int step : positions) {
            double gy = staffY + step * (options.staffLineGap() / 2.0);
            glyphs.add(new GlyphPlacement(x, gy, glyph, step));
            x += advance;
        }
        return x + options.staffLineGap() * 0.5;
    }

    private double placeTimeSignature(List<GlyphPlacement> glyphs, TimeSignature timeSignature,
                                      double startX, double staffY, LayoutOptions options) {
        String beats = Integer.toString(timeSignature.beats());
        String beatType = Integer.toString(timeSignature.beatType());
        int maxLen = Math.max(beats.length(), beatType.length());
        double digitWidth = options.staffLineGap() * 1.4;
        double topY = staffY + options.staffLineGap();
        double bottomY = staffY + options.staffLineGap() * 3;

        double x = startX;
        int lenDiff = maxLen - beats.length();
        for (int i = 0; i < beats.length(); i++) {
            int digit = beats.charAt(i) - '0';
            glyphs.add(new GlyphPlacement(x + (i + lenDiff) * digitWidth, topY,
                    Glyph.timeDigit(digit), 2));
        }
        lenDiff = maxLen - beatType.length();
        for (int i = 0; i < beatType.length(); i++) {
            int digit = beatType.charAt(i) - '0';
            glyphs.add(new GlyphPlacement(x + (i + lenDiff) * digitWidth, bottomY,
                    Glyph.timeDigit(digit), 6));
        }
        return startX + maxLen * digitWidth + options.staffLineGap();
    }

    private void placeElement(List<GlyphPlacement> glyphs, List<BeamPlacement> beams, List<TiePlacement> ties,
                              List<SlurPlacement> slurs, List<TupletPlacement> tuplets,
                              Map<Integer, BeamRun> openBeams, Map<String, PlacedNote> tieCandidates,
                              Map<Integer, PlacedNote> slurCandidates, Map<Integer, TupletRun> tupletCandidates,
                              MusicElement element, double noteX, double staffY,
                              Clef clef, LayoutOptions options) {
        if (element instanceof Note note) {
            placeNote(glyphs, beams, ties, slurs, tuplets, openBeams, tieCandidates, slurCandidates,
                    tupletCandidates, note, noteX, staffY, clef, options);
        } else if (element instanceof Chord chord) {
            for (Note note : chord.notes()) {
                placeNote(glyphs, beams, ties, slurs, tuplets, openBeams, tieCandidates, slurCandidates,
                        tupletCandidates, note, noteX, staffY, clef, options);
            }
        } else if (element instanceof Rest rest) {
            double gap = options.staffLineGap();
            int restStep = restAnchorStaffStep(rest.type());
            double y = staffY + restStep * (gap / 2.0);
            glyphs.add(new GlyphPlacement(noteX, y, restGlyph(rest.type()), restStep));
            for (int i = 0; i < rest.dots(); i++) {
                double dx = noteX + gap * 1.2 + i * gap * 0.6;
                glyphs.add(new GlyphPlacement(dx, y, Glyph.AUG_DOT, restStep));
            }
            updateTupletCandidates(tupletCandidates, tuplets, rest.tuplets(), rest.timeModification(),
                    noteX, y, gap);
        }
    }

    /**
     * Emit direction placements at the given anchor x. Words / metronome go
     * above the staff (unless the placement attribute forces them below);
     * dynamics go below the staff (unless forced above). {@link
     * Placement#DEFAULT} follows the type-specific convention.
     */
    private void placeDirection(List<TextPlacement> texts, List<GlyphPlacement> glyphs,
                                List<HairpinPlacement> hairpins, Map<Integer, WedgeStart> wedgeCandidates,
                                Direction direction, double x, double staffY,
                                LayoutOptions options) {
        double gap = options.staffLineGap();
        Placement side = resolvedPlacement(direction);
        DirectionType type = direction.type();
        if (type instanceof DirectionType.Words words) {
            double fontSize = gap * DIRECTION_FONT_SIZE_GAPS;
            double y = side == Placement.BELOW
                    ? staffY + options.staffHeight() + gap * DIRECTION_OFFSET_GAPS + fontSize
                    : staffY - gap * DIRECTION_OFFSET_GAPS;
            texts.add(new TextPlacement(words.text(), x, y, fontSize,
                    TextPlacement.Align.LEFT, MarkingCategory.DIRECTION));
        } else if (type instanceof DirectionType.Metronome metronome) {
            double fontSize = gap * DIRECTION_FONT_SIZE_GAPS;
            double y = side == Placement.BELOW
                    ? staffY + options.staffHeight() + gap * DIRECTION_OFFSET_GAPS + fontSize
                    : staffY - gap * DIRECTION_OFFSET_GAPS;
            String text = metronomeText(metronome);
            texts.add(new TextPlacement(text, x, y, fontSize,
                    TextPlacement.Align.LEFT, MarkingCategory.TEMPO));
        } else if (type instanceof DirectionType.Dynamic dynamic) {
            Glyph glyph = dynamicGlyph(dynamic.mark());
            double y = side == Placement.ABOVE
                    ? staffY - gap * DIRECTION_OFFSET_GAPS - gap
                    : staffY + options.staffHeight() + gap * DIRECTION_OFFSET_GAPS + gap;
            // Use a staff step well outside the staff so ledger-line hinting
            // never engages for the dynamic glyph.
            int staffStep = side == Placement.ABOVE ? -6 : 12;
            glyphs.add(new GlyphPlacement(x, y, glyph, staffStep, MarkingCategory.DYNAMIC));
        } else if (type instanceof DirectionType.Rehearsal rehearsal) {
            double fontSize = gap * REHEARSAL_FONT_SIZE_GAPS;
            // Rehearsal marks conventionally sit above the staff. If the
            // source explicitly forces BELOW we still honour that, but the
            // default from {@link #resolvedPlacement} yields ABOVE.
            double y = side == Placement.BELOW
                    ? staffY + options.staffHeight() + gap * DIRECTION_OFFSET_GAPS + fontSize
                    : staffY - gap * DIRECTION_OFFSET_GAPS - fontSize;
            texts.add(new TextPlacement(rehearsal.label(), x, y, fontSize,
                    TextPlacement.Align.LEFT, MarkingCategory.REHEARSAL, true));
        } else if (type instanceof DirectionType.Wedge wedge) {
            double y = side == Placement.BELOW
                    ? staffY + options.staffHeight() + gap * DIRECTION_OFFSET_GAPS + gap
                    : staffY - gap * DIRECTION_OFFSET_GAPS - gap;
            if (wedge.type() == DirectionType.WedgeType.STOP) {
                WedgeStart start = wedgeCandidates.remove(wedge.number());
                if (start != null) {
                    hairpins.add(new HairpinPlacement(start.x(), x, start.y(),
                            gap * HAIRPIN_HALF_HEIGHT_GAPS, start.crescendo()));
                }
            } else {
                boolean crescendo = wedge.type() == DirectionType.WedgeType.CRESCENDO;
                wedgeCandidates.put(wedge.number(), new WedgeStart(x, y, crescendo));
            }
        }
        }

    /**
     * Resolve the x-anchor for a zero-duration element (direction or
     * harmony). Chord symbols and directions anchor to the next time-
     * consuming element's x; when the source places them after the last
     * note we fall back to that last x, and when the whole measure carries
     * no timed elements we fall back to {@code fallbackX}.
     */
    private static double anchorX(double[] timedX, int nextTimedIdx, double fallbackX) {
        if (nextTimedIdx < timedX.length) {
            return timedX[nextTimedIdx];
        }
        if (timedX.length > 0) {
            return timedX[timedX.length - 1];
        }
        return fallbackX;
    }

    /**
     * Emit the chord-symbol {@link TextPlacement} for a {@link Harmony} at
     * the given anchor x. Chord symbols sit above the staff, higher than
     * words/tempo directions so the two categories don't overlap when both
     * appear on the same measure.
     */
    private void placeHarmony(List<TextPlacement> texts, Harmony harmony,
                              double x, double staffY, LayoutOptions options) {
        double gap = options.staffLineGap();
        double fontSize = gap * HARMONY_FONT_SIZE_GAPS;
        double y = staffY - gap * HARMONY_OFFSET_GAPS;
        texts.add(new TextPlacement(harmony.displayLabel(), x, y, fontSize,
                TextPlacement.Align.LEFT, MarkingCategory.CHORD_SYMBOL, false));
    }

    /**
     * Resolve a {@link Direction}'s effective placement. Explicit ABOVE/BELOW
     * wins; DEFAULT follows the type convention (words/metronome above,
     * dynamics below).
     */
    private static Placement resolvedPlacement(Direction direction) {
        Placement raw = direction.placement();
        if (raw != null && raw != Placement.DEFAULT) {
            return raw;
        }
        return direction.type() instanceof DirectionType.Dynamic || direction.type() instanceof DirectionType.Wedge
                ? Placement.BELOW
                : Placement.ABOVE;
    }

    /**
     * Render a metronome mark as a plain unicode string, e.g. {@code ♩ = 60}.
     * A proper engraved metronome (SMuFL note glyph + digit glyphs) is a
     * follow-up.
     */
    private static String metronomeText(DirectionType.Metronome metronome) {
        String beatChar = switch (metronome.beatUnit()) {
            case WHOLE -> "𝅝";
            case HALF -> "♩";
            case QUARTER -> "♩";
            case EIGHTH -> "♪";
            case SIXTEENTH -> "♬";
            default -> "♩";
        };
        String dotSuffix = metronome.dotted() ? "." : "";
        return beatChar + dotSuffix + " = " + metronome.perMinute();
    }

    /**
     * Map a {@link DynamicMark} to the corresponding SMuFL dynamic glyph.
     */
    private static Glyph dynamicGlyph(DynamicMark mark) {
        return switch (mark) {
            case PPP -> Glyph.DYNAMIC_PPP;
            case PP -> Glyph.DYNAMIC_PP;
            case P -> Glyph.DYNAMIC_P;
            case MP -> Glyph.DYNAMIC_MP;
            case MF -> Glyph.DYNAMIC_MF;
            case F -> Glyph.DYNAMIC_F;
            case FF -> Glyph.DYNAMIC_FF;
            case FFF -> Glyph.DYNAMIC_FFF;
            case SF -> Glyph.DYNAMIC_SF;
            case SFZ -> Glyph.DYNAMIC_SFZ;
            case FZ -> Glyph.DYNAMIC_FZ;
            case FP -> Glyph.DYNAMIC_FP;
            case RF -> Glyph.DYNAMIC_RF;
            case RFZ -> Glyph.DYNAMIC_RFZ;
            case N -> Glyph.DYNAMIC_NIENTE;
        };
    }

    /**
     * Emit lyric {@link TextPlacement}s for the given measure element. Only
     * verse-1 lyrics are rendered in the current MVP scope; verses &gt;= 2 are
     * captured in the model but not drawn.
     */
    private static void placeLyrics(List<TextPlacement> texts, MusicElement element,
                                    double noteX, double staffY, LayoutOptions options) {
        Note note = lyricCarrier(element);
        if (note == null || note.lyrics().isEmpty()) {
            return;
        }
        double gap = options.staffLineGap();
        double fontSize = gap * LYRIC_FONT_SIZE_GAPS;
        double baselineY = staffY + options.staffHeight()
                + gap * LYRIC_BASELINE_OFFSET_GAPS + fontSize;
        for (Lyric lyric : note.lyrics()) {
            if (lyric.verse() != 1) {
                continue;
            }
            String text = lyric.text();
            if (text.isEmpty()) {
                continue;
            }
            String rendered = text + syllabicSuffix(lyric.syllabic());
            texts.add(new TextPlacement(rendered, noteX, baselineY, fontSize,
                    TextPlacement.Align.CENTER, MarkingCategory.LYRIC));
        }
    }

    /**
     * Trailing character appended to a syllable's rendered text to signal a
     * mid-word hyphenation. A proper centered hyphen glyph between adjacent
     * syllables is a follow-up refinement.
     */
    private static String syllabicSuffix(com.sheetmusic4j.core.model.Syllabic syllabic) {
        return switch (syllabic) {
            case BEGIN, MIDDLE -> "-";
            case SINGLE, END -> "";
        };
    }

    /**
     * Staff-step position at which a rest of the given type is anchored.
     */
    private static int restAnchorStaffStep(NoteType type) {
        return switch (type) {
            case WHOLE, BREVE, LONG, MAXIMA -> 2;
            case HALF -> 4;
            default -> 4;
        };
    }

    private void placeNote(List<GlyphPlacement> glyphs, List<BeamPlacement> beams, List<TiePlacement> ties,
                           List<SlurPlacement> slurs, List<TupletPlacement> tuplets,
                           Map<Integer, BeamRun> openBeams, Map<String, PlacedNote> tieCandidates,
                           Map<Integer, PlacedNote> slurCandidates, Map<Integer, TupletRun> tupletCandidates,
                           Note note, double noteX, double staffY, Clef clef, LayoutOptions options) {
        double gap = options.staffLineGap();
        int staffStep = staffStep(note.pitch(), clef);
        double y = staffY + staffStep * (gap / 2.0);

        // Accidental: prefer the explicit displayed accidental when present
        // (may be NATURAL), otherwise fall back to a chromatic alter on the pitch.
        Accidental acc = note.displayedAccidental().orElse(null);
        if (acc == null && note.pitch().alter() != 0) {
            acc = note.pitch().accidental();
        }
        if (acc != null) {
            Glyph accGlyph = accidentalGlyph(acc);
            if (accGlyph != null) {
                glyphs.add(new GlyphPlacement(noteX - gap * 1.5, y, accGlyph, staffStep));
            }
        }

        glyphs.add(new GlyphPlacement(noteX, y, noteheadGlyph(note.type()), staffStep));

        boolean stemUp = staffStep >= MIDDLE_LINE_STAFF_STEP;
        Glyph stem = stemGlyph(note.type(), staffStep);
        if (stem != null) {
            glyphs.add(new GlyphPlacement(noteX, y, stem, staffStep));
        }

        // Articulations sit on the side opposite the stem, clear of the notehead.
        for (Articulation articulation : note.articulations()) {
            Glyph articulationGlyph = articulation == Articulation.STACCATO
                    ? Glyph.ARTICULATION_STACCATO
                    : Glyph.ARTICULATION_ACCENT;
            double articulationY = stemUp ? y + gap * 1.8 : y - gap * 1.8;
            glyphs.add(new GlyphPlacement(noteX, articulationY, articulationGlyph, staffStep));
        }

        // Augmentation dots.
        for (int i = 0; i < note.dots(); i++) {
            int dotStep = staffStep % 2 == 0 ? staffStep - 1 : staffStep;
            double dx = noteX + gap * 1.2 + i * gap * 0.6;
            double dy = staffY + dotStep * (gap / 2.0);
            glyphs.add(new GlyphPlacement(dx, dy, Glyph.AUG_DOT, dotStep));
        }

        // Beam vs. flag handling.
        boolean beamed = note.isBeamed();
        if (!beamed && stem != null) {
            Glyph flag = flagGlyph(note.type(), stemUp);
            if (flag != null) {
                double stemTipY = stemUp ? y - gap * STEM_LENGTH_GAPS : y + gap * STEM_LENGTH_GAPS;
                double stemTipX = stemUp ? noteX + noteheadHalfWidth(note.type(), gap) : noteX - noteheadHalfWidth(note.type(), gap);
                glyphs.add(new GlyphPlacement(stemTipX, stemTipY, flag, staffStep));
            }
        } else if (beamed && stem != null) {
            double stemTipX = stemUp ? noteX + noteheadHalfWidth(note.type(), gap) : noteX - noteheadHalfWidth(note.type(), gap);
            double stemTipY = stemUp ? y - gap * STEM_LENGTH_GAPS : y + gap * STEM_LENGTH_GAPS;
            processBeams(beams, openBeams, note.beams(), stemTipX, stemTipY, stemUp);
        }

        // Ties: if this note carries tieStart, remember it; if it carries
        // tieStop, close any tie candidate at the same pitch.
        String key = pitchKey(note.pitch());
        if (note.tieStop()) {
            PlacedNote start = tieCandidates.remove(key);
            if (start != null) {
                boolean curveUp = staffStep >= MIDDLE_LINE_STAFF_STEP;
                double yShift = curveUp ? -gap * 0.8 : gap * 0.8;
                double startY = start.y() + yShift;
                double endY = y + yShift;
                ties.add(new TiePlacement(start.x() + gap * 0.5, startY, noteX - gap * 0.5, endY, curveUp));
            }
        }
        if (note.tieStart()) {
            tieCandidates.put(key, new PlacedNote(noteX, y));
        }

        // Slurs: matched by number rather than pitch, since a slur spans a
        // phrase rather than a single repeated pitch.
        for (Slur slur : note.slurs()) {
            if (slur.type() == Slur.Type.STOP) {
                PlacedNote start = slurCandidates.remove(slur.number());
                if (start != null) {
                    boolean curveUp = staffStep >= MIDDLE_LINE_STAFF_STEP;
                    double yShift = curveUp ? -gap * 0.8 : gap * 0.8;
                    slurs.add(new SlurPlacement(start.x() + gap * 0.5, start.y() + yShift,
                            noteX - gap * 0.5, y + yShift, curveUp));
                }
            } else {
                slurCandidates.put(slur.number(), new PlacedNote(noteX, y));
            }
        }

        updateTupletCandidates(tupletCandidates, tuplets, note.tuplets(), note.timeModification(),
                noteX, y, gap);
    }

    private static double noteheadHalfWidth(NoteType type, double gap) {
        return switch (type) {
            case WHOLE, BREVE, LONG, MAXIMA -> 0.75 * gap;
            default -> 0.59 * gap;
        };
    }

    private static String pitchKey(Pitch pitch) {
        return pitch.step().name() + pitch.octave() + "@" + pitch.alter();
    }

    private static Glyph accidentalGlyph(Accidental accidental) {
        if (accidental == null) {
            return null;
        }
        return switch (accidental) {
            case SHARP -> Glyph.ACCIDENTAL_SHARP;
            case FLAT -> Glyph.ACCIDENTAL_FLAT;
            case NATURAL -> Glyph.ACCIDENTAL_NATURAL;
            case DOUBLE_SHARP -> Glyph.ACCIDENTAL_DOUBLE_SHARP;
            case DOUBLE_FLAT -> Glyph.ACCIDENTAL_DOUBLE_FLAT;
        };
    }

    /**
     * Handle beam state transitions for a single note's beam entries.
     * Emits a {@link BeamPlacement} when a run of {@code BEGIN..CONTINUE*..END}
     * closes.
     */
    private static void processBeams(List<BeamPlacement> beams, Map<Integer, BeamRun> openBeams,
                                     List<Beam> noteBeams,
                                     double stemTipX, double stemTipY, boolean stemUp) {
        for (Beam beam : noteBeams) {
            int level = beam.number();
            switch (beam.state()) {
                case BEGIN -> openBeams.put(level, new BeamRun(stemTipX, stemTipY, stemUp));
                case CONTINUE -> {
                    // no-op: run stays open
                }
                case END -> {
                    BeamRun run = openBeams.remove(level);
                    if (run != null) {
                        beams.add(new BeamPlacement(run.startX, run.startY, stemTipX, stemTipY, level, run.stemUp));
                    }
                }
                case FORWARD_HOOK -> {
                    double hookX = stemTipX + 8.0;
                    beams.add(new BeamPlacement(stemTipX, stemTipY, hookX, stemTipY, level, stemUp));
                }
                case BACKWARD_HOOK -> {
                    double hookX = stemTipX - 8.0;
                    beams.add(new BeamPlacement(hookX, stemTipY, stemTipX, stemTipY, level, stemUp));
                }
            }
        }
    }

    /**
     * Choose the stem direction for a note based on its type and staff position.
     * Whole/breve/long/maxima notes have no stem. Notes on or below the middle
     * line get a stem up; notes above the middle line get a stem down.
     */
    static Glyph stemGlyph(NoteType type, int staffStep) {
        return switch (type) {
            case WHOLE, BREVE, LONG, MAXIMA -> null;
            default -> staffStep >= MIDDLE_LINE_STAFF_STEP ? Glyph.STEM_UP : Glyph.STEM_DOWN;
        };
    }

    /**
     * Choose the flag glyph for an unbeamed short note, or {@code null} if the
     * type does not use a flag.
     */
    static Glyph flagGlyph(NoteType type, boolean stemUp) {
        return switch (type) {
            case EIGHTH -> stemUp ? Glyph.FLAG_8TH_UP : Glyph.FLAG_8TH_DOWN;
            case SIXTEENTH, THIRTY_SECOND, SIXTY_FOURTH, HUNDRED_TWENTY_EIGHTH ->
                    stemUp ? Glyph.FLAG_16TH_UP : Glyph.FLAG_16TH_DOWN;
            default -> null;
        };
    }

    /**
     * Vertical position of a pitch expressed in half staff spaces below the top
     * staff line (0 = top line, positive downward).
     */
    static int staffStep(Pitch pitch, Clef clef) {
        int refStaffStep = 2 * (5 - clef.line());
        int refStepNumber = clefReferenceStepNumber(clef.sign());
        return refStaffStep + (refStepNumber - pitch.diatonicStepNumber());
    }

    private static int clefReferenceStepNumber(ClefSign sign) {
        return switch (sign) {
            case F -> new Pitch(com.sheetmusic4j.core.model.Step.F, 3).diatonicStepNumber();
            case C -> new Pitch(com.sheetmusic4j.core.model.Step.C, 4).diatonicStepNumber();
            default -> new Pitch(com.sheetmusic4j.core.model.Step.G, 4).diatonicStepNumber();
        };
    }

    private static Glyph clefGlyph(Clef clef) {
        return switch (clef.sign()) {
            case F -> Glyph.CLEF_F;
            case C -> Glyph.CLEF_C;
            default -> Glyph.CLEF_G;
        };
    }

    private static Glyph noteheadGlyph(NoteType type) {
        return switch (type) {
            case WHOLE, BREVE, LONG, MAXIMA -> Glyph.NOTEHEAD_WHOLE;
            case HALF -> Glyph.NOTEHEAD_HALF;
            default -> Glyph.NOTEHEAD_BLACK;
        };
    }

    private static Glyph restGlyph(NoteType type) {
        return switch (type) {
            case WHOLE, BREVE, LONG, MAXIMA -> Glyph.REST_WHOLE;
            case HALF -> Glyph.REST_HALF;
            case QUARTER -> Glyph.REST_QUARTER;
            default -> Glyph.REST_EIGHTH;
        };
    }

    private record BeamRun(double startX, double startY, boolean stemUp) {
    }

    private record PlacedNote(double x, double y) {
    }

    private record WedgeStart(double x, double y, boolean crescendo) {
    }

    /**
     * Mutable state for an open tuplet run: the x of its first element, the
     * displayed count captured at the start (falls back to the stop
     * element's own {@link TimeModification} if the start didn't carry
     * one), whether a bracket line should be drawn, and the running minimum
     * y seen across every element in the run so the number/bracket clears
     * the highest notehead.
     */
    private static final class TupletRun {
        final double startX;
        final int actualNotes;
        final boolean bracket;
        double minY;

        TupletRun(double startX, double minY, int actualNotes, boolean bracket) {
            this.startX = startX;
            this.minY = minY;
            this.actualNotes = actualNotes;
            this.bracket = bracket;
        }
    }

    /**
     * Track an open tuplet run across every element it spans (not just its
     * start/stop): updates the running minimum y for all currently-open
     * tuplets, then opens or closes a run per {@code elementTuplets}.
     */
    private static void updateTupletCandidates(Map<Integer, TupletRun> tupletCandidates,
                                               List<TupletPlacement> tuplets,
                                               List<Tuplet> elementTuplets,
                                               Optional<TimeModification> timeModification,
                                               double x, double y, double gap) {
        for (TupletRun run : tupletCandidates.values()) {
            run.minY = Math.min(run.minY, y);
        }
        for (Tuplet t : elementTuplets) {
            if (t.type() == Tuplet.Type.START) {
                int actualNotes = timeModification.map(TimeModification::actualNotes).orElse(0);
                tupletCandidates.put(t.number(), new TupletRun(x, y, actualNotes, t.bracket()));
            } else {
                TupletRun run = tupletCandidates.remove(t.number());
                if (run != null) {
                    int number = run.actualNotes > 0
                            ? run.actualNotes
                            : timeModification.map(TimeModification::actualNotes).orElse(3);
                    tuplets.add(new TupletPlacement(run.startX, x,
                            run.minY - gap * TUPLET_ABOVE_GAP, number, run.bracket));
                }
            }
        }
    }
}
