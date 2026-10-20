package com.sheetmusic4j.engraving.spacing;

import com.sheetmusic4j.core.model.Barline;
import com.sheetmusic4j.core.model.Clef;
import com.sheetmusic4j.core.model.KeySignature;
import com.sheetmusic4j.core.model.Measure;
import com.sheetmusic4j.core.model.TimeSignature;
import com.sheetmusic4j.engraving.analysis.PartInfo;
import com.sheetmusic4j.engraving.glyph.BarlineGeometry;
import com.sheetmusic4j.engraving.layout.KeySignatureLayout;
import com.sheetmusic4j.engraving.layout.LayoutOptions;

/**
 * Where things go at the start and end of one staff's measure, as offsets
 * from the measure's left edge (and back from its right edge): the clef,
 * key signature and time signature on a row's first measure, a barline
 * opening the measure, and the span in which the measure's notes may be
 * placed.
 *
 * <p>Pure geometry, so the system can take the latest content start across
 * all staves (every staff then places its notes from there, keeping onsets
 * aligned) while each staff still draws its own header glyphs at these
 * offsets. Barline room comes from {@link BarlineGeometry}, the same
 * numbers the painter draws with.
 *
 * @param clefOffset           x of the clef, or {@code NaN} when none is drawn
 * @param keyOffset            x of the first key-signature accidental, or {@code NaN}
 * @param timeOffset           x of the time signature, or {@code NaN}
 * @param leadingBarlineOffset x at which the measure's opening barline starts
 *                             (after the header on a row's first measure), or
 *                             {@code NaN} when it has none
 * @param contentOffset        x from which the measure's notes are placed
 * @param trailingOffset       distance before the measure's right edge that
 *                             notes must keep clear of, for the closing barline
 */
public record MeasureStart(double clefOffset, double keyOffset, double timeOffset, double leadingBarlineOffset,
                           double contentOffset, double trailingOffset) {

    /**
     * Room (in staff-line gaps) between a barline's last mark and the center
     * of the first note after it: half a notehead plus clear space.
     */
    private static final double NOTE_CLEARANCE_GAPS = 1.4;

    /**
     * Clear space (in staff-line gaps) between a note's edge and repeat dots
     * that follow it.
     */
    private static final double DOT_CLEARANCE_GAPS = 0.4;

    /**
     * Distance (in staff-line gaps) between the right edge of a measure's
     * last notehead and the end of the measure's content: the minimum slot
     * {@link MeasureColumns} reserves after a note (1.2) minus half a
     * notehead (0.6).
     */
    private static final double LAST_NOTE_TAIL_GAPS = 0.6;

    public static MeasureStart of(PartInfo part, int staffIdx, int measureIdx,
                                  boolean firstMeasureInRow, boolean lastMeasureInRow, boolean firstRow,
                                  LayoutOptions options) {
        double gap = options.staffLineGap();
        Measure measure = part.measures().get(measureIdx);
        double content = gap;
        double clefOffset = Double.NaN;
        double keyOffset = Double.NaN;
        double timeOffset = Double.NaN;
        if (firstMeasureInRow) {
            Clef clef = part.clefAt(measureIdx, staffIdx);
            KeySignature key = part.keyAt(measureIdx);
            TimeSignature time = part.timeAt(measureIdx);
            if (options.showClef()) {
                clefOffset = gap * 0.5;
                content = gap * 4;
            }
            if (options.showKeySignature() && key.fifths() != 0) {
                keyOffset = content;
                content += KeySignatureLayout.positions(clef, key).length * keySignatureAdvance(options)
                        + gap * 0.5;
            }
            boolean showTime = (measureIdx == 0 && firstRow) || part.timeChangedAt(measureIdx);
            if (options.showTimeSignature() && time != null && showTime) {
                timeOffset = content;
                content += timeSignatureWidth(time, options) + gap;
            }
        }
        double leadingBarlineOffset = Double.NaN;
        Barline leading = openingBarline(part, measureIdx, firstMeasureInRow);
        if (leading != null) {
            // On a row's first measure the opening barline follows the
            // header (as in printed music); mid-row it sits on the boundary.
            leadingBarlineOffset = firstMeasureInRow ? content : 0.0;
            content = leadingBarlineOffset + gap * (BarlineGeometry.extentAfter(leading, true) + NOTE_CLEARANCE_GAPS);
        } else if (!firstMeasureInRow && measureIdx > 0) {
            // Repeat dots of the previous measure's "::" (or a stray forward
            // repeat closing it) reach past the boundary into this measure.
            Barline previous = part.measures().get(measureIdx - 1).barline().orElse(null);
            double reach = BarlineGeometry.extentAfter(previous, false);
            if (reach > 0) {
                content = gap * (reach + NOTE_CLEARANCE_GAPS);
            }
        }
        // Closing repeat dots reach back into the measure. The last note
        // already ends at least LAST_NOTE_TAIL_GAPS before the content end
        // (its minimum slot minus half a notehead), so the content must end
        // early enough for that edge to clear the dots.
        Barline closing = closingBarline(part, measureIdx, lastMeasureInRow);
        double trailing = Math.max(gap,
                gap * (BarlineGeometry.extentBefore(closing) + DOT_CLEARANCE_GAPS - LAST_NOTE_TAIL_GAPS));
        return new MeasureStart(clefOffset, keyOffset, timeOffset, leadingBarlineOffset, content, trailing);
    }

    /**
     * The barline opening measure {@code measureIdx} as drawn: its own
     * leading barline, or - when the row starts here right after a combined
     * end/start repeat ({@code ::}) - the repeat-start half of that repeat,
     * which moves to the beginning of the new system.
     */
    public static Barline openingBarline(PartInfo part, int measureIdx, boolean firstMeasureInRow) {
        Barline own = part.measures().get(measureIdx).leadingBarline().orElse(null);
        if (own != null || !firstMeasureInRow || measureIdx == 0) {
            return own;
        }
        Barline previous = part.measures().get(measureIdx - 1).barline().orElse(null);
        return previous != null && previous.repeat() == Barline.Repeat.BOTH
                ? new Barline(Barline.Style.HEAVY_LIGHT, Barline.Repeat.FORWARD)
                : null;
    }

    /**
     * The barline closing measure {@code measureIdx} as drawn: a combined
     * end/start repeat ({@code ::}) ending a system shows only its
     * repeat-end half there (see {@link #openingBarline}).
     */
    public static Barline closingBarline(PartInfo part, int measureIdx, boolean lastMeasureInRow) {
        Barline own = part.measures().get(measureIdx).barline().orElse(null);
        if (own != null && lastMeasureInRow && own.repeat() == Barline.Repeat.BOTH) {
            return new Barline(own.style(), Barline.Repeat.BACKWARD);
        }
        return own;
    }

    /**
     * Horizontal advance from one key-signature accidental to the next.
     */
    public static double keySignatureAdvance(LayoutOptions options) {
        return options.staffLineGap() * 1.1;
    }

    /**
     * Width of one time-signature digit.
     */
    public static double timeDigitWidth(LayoutOptions options) {
        return options.staffLineGap() * 1.4;
    }

    private static double timeSignatureWidth(TimeSignature time, LayoutOptions options) {
        int maxLen = Math.max(Integer.toString(time.beats()).length(), Integer.toString(time.beatType()).length());
        return maxLen * timeDigitWidth(options);
    }
}
