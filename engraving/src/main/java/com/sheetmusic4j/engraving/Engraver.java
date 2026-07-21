package com.sheetmusic4j.engraving;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.sheetmusic4j.core.model.Accidental;
import com.sheetmusic4j.core.model.Attributes;
import com.sheetmusic4j.core.model.Beam;
import com.sheetmusic4j.core.model.Chord;
import com.sheetmusic4j.core.model.Clef;
import com.sheetmusic4j.core.model.ClefSign;
import com.sheetmusic4j.core.model.Creator;
import com.sheetmusic4j.core.model.KeySignature;
import com.sheetmusic4j.core.model.Measure;
import com.sheetmusic4j.core.model.MusicElement;
import com.sheetmusic4j.core.model.Note;
import com.sheetmusic4j.core.model.NoteType;
import com.sheetmusic4j.core.model.Part;
import com.sheetmusic4j.core.model.Pitch;
import com.sheetmusic4j.core.model.Rest;
import com.sheetmusic4j.core.model.Score;
import com.sheetmusic4j.core.model.TimeSignature;

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
    private static final double STEM_LENGTH_GAPS = 3.5;

    public LayoutResult layout(Score score, LayoutOptions options) {
        List<TextPlacement> texts = new ArrayList<>();
        double titleBlockHeight = layoutTitleBlock(score, options, texts);

        if (score.parts().isEmpty()) {
            SystemLayout empty = new SystemLayout(0, 0, options.systemWidth(), List.of());
            return new LayoutResult(List.of(empty), texts, options.systemWidth(),
                    options.topMargin() + titleBlockHeight + options.staffHeight());
        }

        double staffWidth = options.systemWidth() - options.leftMargin() - options.rightMargin();

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
        List<Double> baseWidths = distributeWidths(
                sharedWeights, Math.max(1.0, staffWidth - firstSystemHeader), options.measureMinWidth());
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
            double staffTop = y;
            for (PartInfo p : parts) {
                for (int staffIdx = 0; staffIdx < p.staveCount(); staffIdx++) {
                    StaffLayout sl = layoutStaffRow(
                            p, staffIdx, options.leftMargin(), staffTop,
                            options, start, endExclusive, rowWidths, rowIdx == 0);
                    stavesForRow.add(sl);
                    staffTop += options.staffHeight() + options.staffSpacing();
                }
            }
            systems.add(new SystemLayout(0, y, options.systemWidth(), stavesForRow));
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
                    fontSize, TextPlacement.Align.CENTER, TextPlacement.Category.TITLE));
            double advance = fontSize * 1.2;
            y += advance;
            consumed += advance;
        }
        String movement = score.movementTitle().orElse(null);
        if (movement != null && !movement.isBlank() && !movement.equals(workTitle)) {
            double fontSize = gap * 1.6;
            texts.add(new TextPlacement(movement, centerX, y + fontSize,
                    fontSize, TextPlacement.Align.CENTER, TextPlacement.Category.SUBTITLE));
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
                            fontSize, TextPlacement.Align.RIGHT, TextPlacement.Category.CREATOR));
                }
                if (i < leftColumn.size()) {
                    texts.add(new TextPlacement(leftColumn.get(i).name(), leftX, baselineY,
                            fontSize, TextPlacement.Align.LEFT, TextPlacement.Category.CREATOR));
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

        private PartInfo(Part part, int staveCount,
                         Clef[][] clefs, KeySignature[] keys, TimeSignature[] times) {
            this.part = part;
            this.staveCount = staveCount;
            this.clefsPerMeasure = clefs;
            this.keyPerMeasure = keys;
            this.timePerMeasure = times;
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
            return new PartInfo(part, staveCount, clefs, keys, times);
        }
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
     * Distribute {@code totalWidth} across a list of weight-sized measures,
     * clamping each measure width to at least {@code min}.
     */
    private static List<Double> distributeWidths(List<Double> weights, double totalWidth, double min) {
        List<Double> widths = new ArrayList<>(weights.size());
        if (weights.isEmpty()) {
            return widths;
        }
        double sumWeights = 0.0;
        for (double w : weights) {
            sumWeights += w;
        }
        double effective = Math.max(totalWidth, min * weights.size());
        double sum = 0.0;
        for (double w : weights) {
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
                                       boolean firstRow) {
        Part part = partInfo.part();
        int staveCount = partInfo.staveCount();
        int staffNumber = staffIdx + 1;

        List<MeasureLayout> measureLayouts = new ArrayList<>();
        List<GlyphPlacement> glyphs = new ArrayList<>();
        List<BeamPlacement> beams = new ArrayList<>();
        List<TiePlacement> ties = new ArrayList<>();

        Map<Integer, BeamRun> openBeams = new HashMap<>();
        Map<String, PlacedNote> tieCandidates = new HashMap<>();

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
            double available = (cursorX + measureWidth) - contentStart - options.staffLineGap();
            double step = elements.isEmpty() ? available : available / elements.size();
            double noteX = contentStart;
            for (MusicElement element : elements) {
                placeElement(glyphs, beams, ties, openBeams, tieCandidates,
                        element, noteX, y, currentClef, options);
                noteX += step;
            }

            cursorX += measureWidth;
            firstMeasureInRow = false;
        }

        return new StaffLayout(x, y, cursorX - x, options.staffLineGap(),
                measureLayouts, glyphs, beams, ties);
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
        // Rests inherit the previous staff assignment; default to staff 1.
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
            sum += element.duration().inQuarters();
        }
        return sum > 0 ? sum : 1.0;
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
                              Map<Integer, BeamRun> openBeams, Map<String, PlacedNote> tieCandidates,
                              MusicElement element, double noteX, double staffY,
                              Clef clef, LayoutOptions options) {
        if (element instanceof Note note) {
            placeNote(glyphs, beams, ties, openBeams, tieCandidates, note, noteX, staffY, clef, options);
        } else if (element instanceof Chord chord) {
            for (Note note : chord.notes()) {
                placeNote(glyphs, beams, ties, openBeams, tieCandidates, note, noteX, staffY, clef, options);
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
        }
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
                           Map<Integer, BeamRun> openBeams, Map<String, PlacedNote> tieCandidates,
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
}
