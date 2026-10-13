package com.sheetmusic4j.engraving.layout;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.sheetmusic4j.core.model.Chord;
import com.sheetmusic4j.core.model.Note;

/**
 * Musical time of a {@link LayoutResult} mapped onto its geometry, for
 * playback cursors and "now sounding" highlights.
 *
 * <p>The cursor x is interpolated between note onsets rather than across
 * whole measures (as {@link LayoutResult#xAtTime} does), so a cursor passes
 * exactly through each notehead at its onset, keeps gliding while a note is
 * held, and speeds up or slows with the note spacing.
 */
public final class PlaybackTimeline {

    private final List<NoteAnchor> soundingAnchors;
    private final double totalDurationQuarters;
    /** Strictly increasing onsets and the layout x of each. */
    private final double[] sampleOnset;
    private final double[] sampleX;
    /** Fallback when there are no anchors to interpolate between. */
    private final LayoutResult layout;

    private PlaybackTimeline(LayoutResult layout) {
        this.layout = layout;
        this.totalDurationQuarters = layout.totalDurationQuarters();

        List<NoteAnchor> sorted = new ArrayList<>(layout.noteAnchors());
        sorted.sort(Comparator.comparingDouble(NoteAnchor::onsetQuarters));
        this.soundingAnchors = sorted.stream().filter(PlaybackTimeline::isSounding).toList();

        List<Double> onsets = new ArrayList<>();
        List<Double> xs = new ArrayList<>();
        if (!sorted.isEmpty()) {
            // Leading sample so the cursor eases in from the staff start.
            if (sorted.get(0).onsetQuarters() > 0) {
                onsets.add(0.0);
                xs.add(layout.xAtTime(0));
            }
            for (NoteAnchor a : sorted) {
                if (!onsets.isEmpty() && a.onsetQuarters() == onsets.get(onsets.size() - 1)) {
                    continue; // same time column (e.g. chord members) -> one sample
                }
                onsets.add(a.onsetQuarters());
                xs.add(a.x());
            }
            // Trailing sample at the end of the piece so motion continues through
            // the final note's duration to the closing barline.
            if (totalDurationQuarters > onsets.get(onsets.size() - 1)) {
                onsets.add(totalDurationQuarters);
                xs.add(layout.xAtTime(totalDurationQuarters));
            }
        }
        this.sampleOnset = onsets.stream().mapToDouble(Double::doubleValue).toArray();
        this.sampleX = xs.stream().mapToDouble(Double::doubleValue).toArray();
    }

    /**
     * Builds the timeline of a layout.
     */
    public static PlaybackTimeline of(LayoutResult layout) {
        return new PlaybackTimeline(layout);
    }

    /**
     * Whether an anchor is a note or chord with a duration, as opposed to a
     * rest or a zero-length element.
     */
    public static boolean isSounding(NoteAnchor anchor) {
        return anchor.durationQuarters() > 0
                && (anchor.elementRef() instanceof Note || anchor.elementRef() instanceof Chord);
    }

    /**
     * Total musical duration, in quarter notes; see
     * {@link LayoutResult#totalDurationQuarters()}.
     */
    public double totalDurationQuarters() {
        return totalDurationQuarters;
    }

    /**
     * The {@link #isSounding sounding} anchors, sorted by onset.
     */
    public List<NoteAnchor> soundingAnchors() {
        return soundingAnchors;
    }

    /**
     * The sounding anchors whose window {@code [onset, onset + duration)}
     * contains the given time.
     */
    public List<NoteAnchor> soundingAt(double quarters) {
        List<NoteAnchor> out = new ArrayList<>();
        for (NoteAnchor anchor : soundingAnchors) {
            if (anchor.onsetQuarters() > quarters) {
                break;
            }
            if (quarters < anchor.endQuarters()) {
                out.add(anchor);
            }
        }
        return out;
    }

    /**
     * The x (layout units) of a playback cursor at the given musical time,
     * interpolated between note onsets and clamped to the timeline's ends.
     * Falls back to {@link LayoutResult#xAtTime} when the layout has no
     * note anchors.
     */
    public double cursorX(double quarters) {
        int n = sampleOnset.length;
        if (n == 0) {
            return layout.xAtTime(quarters);
        }
        if (quarters <= sampleOnset[0]) {
            return sampleX[0];
        }
        if (quarters >= sampleOnset[n - 1]) {
            return sampleX[n - 1];
        }
        int i = 1;
        while (i < n && sampleOnset[i] < quarters) {
            i++;
        }
        double span = sampleOnset[i] - sampleOnset[i - 1];
        double f = span <= 0 ? 0 : (quarters - sampleOnset[i - 1]) / span;
        return sampleX[i - 1] + f * (sampleX[i] - sampleX[i - 1]);
    }
}
