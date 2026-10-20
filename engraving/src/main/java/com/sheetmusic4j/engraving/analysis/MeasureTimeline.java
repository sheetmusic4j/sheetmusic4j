package com.sheetmusic4j.engraving.analysis;

import java.util.List;

import com.sheetmusic4j.core.model.Fraction;
import com.sheetmusic4j.core.model.TimeSignature;

/**
 * Musical onset and duration (both in quarter notes) of every measure index
 * across the whole score, shared by all parts.
 */
public final class MeasureTimeline {

    private final double[] startQuarters;
    private final double[] durationQuarters;

    private MeasureTimeline(double[] startQuarters, double[] durationQuarters) {
        this.startQuarters = startQuarters;
        this.durationQuarters = durationQuarters;
    }

    /**
     * Compute the per-measure musical onset and duration across the whole
     * score.
     *
     * <p>A measure lasts as long as its longest voice in any part (see
     * {@link com.sheetmusic4j.core.model.MeasureTiming}), falling back to
     * the effective time signature when the source measure carries no
     * time-consuming elements at all (a rare case, e.g. a measure that only
     * contains a tempo direction). Onsets are summed exactly, so long scores
     * full of tuplets don't drift.
     */
    public static MeasureTimeline of(List<PartInfo> parts, int measureCount) {
        double[] start = new double[measureCount];
        double[] duration = new double[measureCount];
        Fraction cursor = Fraction.ZERO;
        for (int idx = 0; idx < measureCount; idx++) {
            Fraction dur = Fraction.ZERO;
            for (PartInfo p : parts) {
                if (idx < p.measures().size()) {
                    dur = Fraction.max(dur, p.timing(idx).duration());
                }
            }
            if (dur.isZero()) {
                for (PartInfo p : parts) {
                    TimeSignature ts = p.timeAt(idx);
                    if (ts != null) {
                        dur = Fraction.of(ts.beats() * 4L, ts.beatType());
                        break;
                    }
                }
            }
            start[idx] = cursor.toDouble();
            duration[idx] = dur.toDouble();
            cursor = cursor.plus(dur);
        }
        return new MeasureTimeline(start, duration);
    }

    /**
     * Onset of measure {@code idx} in quarter notes from the start of the
     * score, or {@code 0} when out of range.
     */
    public double start(int idx) {
        return idx < startQuarters.length ? startQuarters[idx] : 0.0;
    }

    /**
     * Duration of measure {@code idx} in quarter notes, or {@code 0} when
     * out of range.
     */
    public double duration(int idx) {
        return idx < durationQuarters.length ? durationQuarters[idx] : 0.0;
    }
}
