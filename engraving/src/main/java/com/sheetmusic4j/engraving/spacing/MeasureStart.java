package com.sheetmusic4j.engraving.spacing;

import com.sheetmusic4j.core.model.Barline;
import com.sheetmusic4j.core.model.Clef;
import com.sheetmusic4j.core.model.KeySignature;
import com.sheetmusic4j.core.model.Measure;
import com.sheetmusic4j.core.model.TimeSignature;
import com.sheetmusic4j.engraving.analysis.PartInfo;
import com.sheetmusic4j.engraving.layout.KeySignatureLayout;
import com.sheetmusic4j.engraving.layout.LayoutOptions;

/**
 * Where things go at the start of one staff's measure, as offsets from the
 * measure's left edge: the clef, key signature and time signature on a
 * row's first measure, and where the measure's notes may start.
 *
 * <p>Pure geometry, so the system can take the latest content start across
 * all staves (every staff then places its notes from there, keeping onsets
 * aligned) while each staff still draws its own header glyphs at these
 * offsets.
 *
 * @param clefOffset    x of the clef, or {@code NaN} when none is drawn
 * @param keyOffset     x of the first key-signature accidental, or {@code NaN}
 * @param timeOffset    x of the time signature, or {@code NaN}
 * @param contentOffset x from which the measure's notes are placed
 */
public record MeasureStart(double clefOffset, double keyOffset, double timeOffset, double contentOffset) {

    /**
     * Width (in staff-line gaps) of the clearance before content following
     * repeat dots.
     */
    private static final double REPEAT_CLEARANCE_GAPS = 1.6;

    public static MeasureStart of(PartInfo part, int staffIdx, int measureIdx,
                                  boolean firstMeasureInRow, boolean firstRow, LayoutOptions options) {
        double gap = options.staffLineGap();
        Measure measure = part.measures().get(measureIdx);
        double content = gap;
        // A repeat-start mark (bare "|:") or the right-hand dots of a
        // combined end+start repeat ("::") on the *previous* measure both
        // draw repeat dots just past this measure's left edge (see
        // ScorePainter#drawBarline); reserve the same clearance the
        // first-measure-in-row branch below gives its own leading repeat
        // mark, or the first note collides with the dots.
        boolean midRowRepeatClearance = !firstMeasureInRow && measure.leadingRepeatStart();
        if (!midRowRepeatClearance && !firstMeasureInRow && measureIdx > 0) {
            Barline previousBarline = part.measures().get(measureIdx - 1).barline().orElse(null);
            midRowRepeatClearance = previousBarline != null
                    && (previousBarline.repeat() == Barline.Repeat.FORWARD
                        || previousBarline.repeat() == Barline.Repeat.BOTH);
        }
        if (midRowRepeatClearance) {
            content = gap * REPEAT_CLEARANCE_GAPS;
        }
        double clefOffset = Double.NaN;
        double keyOffset = Double.NaN;
        double timeOffset = Double.NaN;
        if (firstMeasureInRow) {
            Clef clef = part.clefAt(measureIdx, staffIdx);
            KeySignature key = part.keyAt(measureIdx);
            TimeSignature time = part.timeAt(measureIdx);
            // A leading repeat-start mark (thick line + dots) is drawn at the
            // staff's left edge (see ScorePainter#drawStaff); leave room for
            // it before the clef instead of drawing on top of it.
            double leadingRepeatClearance = measure.leadingRepeatStart() ? gap * REPEAT_CLEARANCE_GAPS : 0.0;
            if (options.showClef()) {
                clefOffset = leadingRepeatClearance + gap * 0.5;
                content = leadingRepeatClearance + gap * 4;
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
        return new MeasureStart(clefOffset, keyOffset, timeOffset, content);
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
