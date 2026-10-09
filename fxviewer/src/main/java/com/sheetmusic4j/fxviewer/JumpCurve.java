package com.sheetmusic4j.fxviewer;

/**
 * How far a note floats above the paper in {@link FlyOverSheetView}, as a pure
 * function of musical time so playback can be scrubbed backwards and forwards
 * and the curve can be tested without a display.
 *
 * <p>A note pops up at its onset (eased over {@link #RISE_QUARTERS}), hovers
 * while it sounds and settles back onto the paper over
 * {@link #FALL_QUARTERS} after it ends.
 */
final class JumpCurve {

    /** Time to pop up to full height, in quarter notes (shorter for very short notes). */
    static final double RISE_QUARTERS = 0.15;

    /** Time to settle back onto the paper after the note ends, in quarter notes. */
    static final double FALL_QUARTERS = 0.25;

    private JumpCurve() {
    }

    /**
     * How long after its onset a note can still be off the paper.
     *
     * @param durationQuarters the note's duration in quarter notes
     * @return the airborne time in quarter notes
     */
    static double airborne(double durationQuarters) {
        return Math.max(durationQuarters, 0) + FALL_QUARTERS;
    }

    /**
     * Lift of a note at playhead {@code time}, as a fraction of the full jump
     * height.
     *
     * @param time             playhead in quarter notes
     * @param onsetQuarters    the note's onset in quarter notes
     * @param durationQuarters the note's duration in quarter notes
     * @return {@code 0} on the paper (before the onset and once settled),
     *         {@code 1} while the note sounds
     */
    static double lift(double time, double onsetQuarters, double durationQuarters) {
        double sinceOnset = time - onsetQuarters;
        if (sinceOnset <= 0 || durationQuarters <= 0) {
            return 0;
        }
        double rise = Math.min(RISE_QUARTERS, durationQuarters / 2);
        if (sinceOnset < rise) {
            return Math.sin(Math.PI / 2 * sinceOnset / rise);
        }
        double sinceEnd = sinceOnset - durationQuarters;
        if (sinceEnd <= 0) {
            return 1;
        }
        if (sinceEnd < FALL_QUARTERS) {
            return Math.cos(Math.PI / 2 * sinceEnd / FALL_QUARTERS);
        }
        return 0;
    }
}
