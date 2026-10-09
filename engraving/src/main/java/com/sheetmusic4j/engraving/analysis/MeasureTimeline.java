package com.sheetmusic4j.engraving.analysis;

import java.util.List;

import com.sheetmusic4j.core.model.Measure;
import com.sheetmusic4j.core.model.MusicElement;
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
     * <p>For each measure we take the maximum sum-of-durations found in any
     * part's single staff — filtering by staff so a grand-staff part
     * doesn't double-count its two staves' elements — then fall back to
     * the effective time signature when the source measure carries no
     * time-consuming elements at all (a rare case, e.g. a measure that only
     * contains a tempo direction).
     */
    public static MeasureTimeline of(List<PartInfo> parts, int measureCount) {
        double[] start = new double[measureCount];
        double[] duration = new double[measureCount];
        double cursor = 0.0;
        for (int idx = 0; idx < measureCount; idx++) {
            double dur = 0.0;
            for (PartInfo p : parts) {
                if (idx >= p.measures().size()) {
                    continue;
                }
                Measure m = p.measures().get(idx);
                int staves = p.staveCount();
                for (int staffIdx = 0; staffIdx < staves; staffIdx++) {
                    int staffNumber = staffIdx + 1;
                    double staffDur = 0.0;
                    for (MusicElement e : m.elements()) {
                        if (!ScoreAnalysis.isTimed(e)) {
                            continue;
                        }
                        if (staves > 1 && ScoreAnalysis.elementStaff(e) != staffNumber) {
                            continue;
                        }
                        staffDur += e.duration().inQuarters();
                    }
                    dur = Math.max(dur, staffDur);
                }
            }
            if (dur == 0.0) {
                for (PartInfo p : parts) {
                    TimeSignature ts = p.timeAt(idx);
                    if (ts != null) {
                        dur = ts.beats() * 4.0 / ts.beatType();
                        break;
                    }
                }
            }
            start[idx] = cursor;
            duration[idx] = dur;
            cursor += dur;
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
