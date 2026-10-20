package com.sheetmusic4j.fxviewer;

import java.util.List;

import com.sheetmusic4j.engraving.layout.NoteAnchor;
import com.sheetmusic4j.engraving.layout.PlaybackTimeline;
import com.sheetmusic4j.render.ScorePainter;

/**
 * Feeds a view's reveal time into its {@link ScorePainter} and works out
 * what has to be repainted: nothing while no element starts or stops being
 * revealed (the reveal time usually moves every frame), otherwise just the
 * range of the elements that changed. FX thread only.
 */
final class RevealTracker {

    /** Returned by {@link #apply} when everything must be repainted. */
    static final double[] ALL = new double[0];

    /** Room around changed elements for their accidentals, flags and the spanners ending on them. */
    private static final double MARGIN = 80;

    private final ScorePainter painter;
    private double applied = Double.POSITIVE_INFINITY;

    RevealTracker(ScorePainter painter) {
        this.painter = painter;
    }

    /**
     * Use {@code time} as the reveal time from now on.
     *
     * @param timeline the timeline of the painted layout, or {@code null} when there is none
     * @param time     the new reveal time; {@code NaN} or positive infinity reveals everything
     * @return {@code null} when nothing changed on screen, {@link #ALL} for a full
     *         repaint, else the {@code {x0, x1}} range (layout units) to repaint
     */
    double[] apply(PlaybackTimeline timeline, double time) {
        double next = Double.isNaN(time) ? Double.POSITIVE_INFINITY : time;
        double previous = applied;
        applied = next;
        painter.setRevealTime(next);
        if (timeline == null || previous == next) {
            return null;
        }
        boolean wasAll = previous == Double.POSITIVE_INFINITY;
        boolean isAll = next == Double.POSITIVE_INFINITY;
        if (wasAll || isAll) {
            return wasAll && isAll ? null : ALL;
        }
        if (timeline.revealStep(previous) == timeline.revealStep(next)) {
            return null;
        }
        List<NoteAnchor> changed = timeline.anchorsBetween(previous, next);
        if (changed.isEmpty()) {
            return null;
        }
        double x0 = Double.POSITIVE_INFINITY;
        double x1 = Double.NEGATIVE_INFINITY;
        for (NoteAnchor anchor : changed) {
            x0 = Math.min(x0, anchor.x() - anchor.width());
            x1 = Math.max(x1, anchor.x() + anchor.width());
        }
        return new double[] {x0 - MARGIN, x1 + MARGIN};
    }
}
