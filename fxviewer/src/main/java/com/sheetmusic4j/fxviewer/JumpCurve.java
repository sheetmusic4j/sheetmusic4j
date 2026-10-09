package com.sheetmusic4j.fxviewer;

/**
 * Timing of the bouncing balls and lit notes in {@link FlyOverSheetView}, as
 * pure functions of musical time so playback can be scrubbed backwards and
 * forwards and the curves can be tested without a display.
 *
 * <p>Each voice has a ball that lands on every note exactly at its onset and
 * hops along a parabola to the next note of the voice. A note glows while it
 * sounds and fades out over {@link #FADE_QUARTERS} after it ends.
 */
final class JumpCurve {

    /** Hops shorter than this (in quarter notes) are flattened, so fast runs don't look frantic. */
    static final double FULL_HEIGHT_QUARTERS = 1.0;

    /** Time a note keeps glowing after it ends, in quarter notes. */
    static final double FADE_QUARTERS = 0.25;

    private JumpCurve() {
    }

    /**
     * How far along the hop from one landing to the next the ball is.
     *
     * @param time playhead in quarter notes
     * @param from onset of the note the hop starts on, in quarter notes
     * @param to   onset of the note the hop lands on, in quarter notes
     * @return {@code 0} on the first note, {@code 1} on the next
     */
    static double progress(double time, double from, double to) {
        if (to <= from) {
            return 1;
        }
        return Math.clamp((time - from) / (to - from), 0, 1);
    }

    /**
     * Height of the ball on a hop, as a fraction of the full jump height: a
     * parabola through both landings, lower for short hops.
     *
     * @param time playhead in quarter notes
     * @param from onset of the note the hop starts on, in quarter notes
     * @param to   onset of the note the hop lands on, in quarter notes
     * @return {@code 0} on a note, at most {@code 1} halfway
     */
    static double height(double time, double from, double to) {
        double s = progress(time, from, to);
        double scale = Math.sqrt(Math.clamp((to - from) / FULL_HEIGHT_QUARTERS, 0, 1));
        return 4 * s * (1 - s) * scale;
    }

    /**
     * How long after its onset a note can still glow.
     *
     * @param durationQuarters the note's duration in quarter notes
     * @return the glowing time in quarter notes
     */
    static double afterglow(double durationQuarters) {
        return Math.max(durationQuarters, 0) + FADE_QUARTERS;
    }

    /**
     * Glow of a note at playhead {@code time}.
     *
     * @param time             playhead in quarter notes
     * @param onsetQuarters    the note's onset in quarter notes
     * @param durationQuarters the note's duration in quarter notes
     * @return {@code 1} while the note sounds (from the moment the ball lands),
     *         fading to {@code 0} after it ends; {@code 0} before the onset
     */
    static double glow(double time, double onsetQuarters, double durationQuarters) {
        double sinceOnset = time - onsetQuarters;
        if (sinceOnset < 0 || durationQuarters <= 0) {
            return 0;
        }
        double sinceEnd = sinceOnset - durationQuarters;
        if (sinceEnd <= 0) {
            return 1;
        }
        if (sinceEnd < FADE_QUARTERS) {
            return Math.cos(Math.PI / 2 * sinceEnd / FADE_QUARTERS);
        }
        return 0;
    }
}
