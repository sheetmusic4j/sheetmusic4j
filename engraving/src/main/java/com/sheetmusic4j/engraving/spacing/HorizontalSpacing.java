package com.sheetmusic4j.engraving.spacing;

import java.util.ArrayList;
import java.util.List;

import com.sheetmusic4j.core.model.Clef;
import com.sheetmusic4j.core.model.KeySignature;
import com.sheetmusic4j.core.model.TimeSignature;
import com.sheetmusic4j.engraving.analysis.PartInfo;
import com.sheetmusic4j.engraving.layout.LayoutOptions;
import com.sheetmusic4j.engraving.layout.MeasureLayout;

/**
 * Row-level horizontal spacing: how wide the clef / key / time header is,
 * how a row's width is shared between its measures, and the global time
 * scale of STRIP layout. Spacing within a measure is {@link MeasureColumns}.
 */
public final class HorizontalSpacing {

    /**
     * Minimum horizontal advance per column (in staff-line gaps) for the
     * STRIP time scale; matches the per-column safety rail of
     * {@link MeasureColumns}.
     */
    private static final double MIN_NOTE_ADVANCE_GAPS = 1.2;

    private HorizontalSpacing() {
    }

    /**
     * Compute the horizontal space the painter needs at the head of a staff
     * for the given clef + optional key signature + optional time signature.
     *
     * <p>The value must match the offsets computed by {@link MeasureStart}.
     * Callers use it to (a) size the header block, and (b) determine how much
     * space to reserve before the row's first measure.
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
     * The global pixels-per-quarter scale for STRIP (time-proportional)
     * layout: the smallest scale at which every column of every measure still
     * gets its minimum slot before the next one, so even the shortest /
     * widest-glyph note gets its room while every width stays strictly
     * proportional to duration.
     */
    public static double stripPixelsPerQuarter(List<MeasureColumns> columns, LayoutOptions options) {
        double ppq = MIN_NOTE_ADVANCE_GAPS * options.staffLineGap();
        for (MeasureColumns measure : columns) {
            ppq = Math.max(ppq, measure.minPixelsPerQuarter());
        }
        // The bare minimum-slot scale packs notes tightly; the configurable
        // spacing factor gives the strip more breathing room. Purely a uniform
        // scale - it does not affect proportionality.
        return ppq * options.stripSpacingFactor();
    }
}
