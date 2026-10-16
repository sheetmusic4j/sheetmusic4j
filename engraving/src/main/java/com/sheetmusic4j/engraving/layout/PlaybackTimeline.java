package com.sheetmusic4j.engraving.layout;

import java.util.ArrayList;
import java.util.Arrays;
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
    /** System index of each sample. */
    private final int[] sampleSystem;
    /** Every anchor (rests included), sorted by onset. */
    private final List<NoteAnchor> allAnchors;
    /** Strictly increasing distinct onsets of {@link #allAnchors}. */
    private final double[] revealOnsets;
    /** Fallback when there are no anchors to interpolate between. */
    private final LayoutResult layout;

    private PlaybackTimeline(LayoutResult layout) {
        this.layout = layout;
        this.totalDurationQuarters = layout.totalDurationQuarters();

        List<NoteAnchor> sorted = new ArrayList<>(layout.noteAnchors());
        sorted.sort(Comparator.comparingDouble(NoteAnchor::onsetQuarters));
        this.soundingAnchors = sorted.stream().filter(PlaybackTimeline::isSounding).toList();
        this.allAnchors = List.copyOf(sorted);

        List<Double> onsets = new ArrayList<>();
        List<Double> xs = new ArrayList<>();
        List<Integer> systems = new ArrayList<>();
        if (!sorted.isEmpty()) {
            // Leading sample so the cursor eases in from the staff start.
            if (sorted.get(0).onsetQuarters() > 0) {
                onsets.add(0.0);
                xs.add(layout.xAtTime(0));
                systems.add(0);
            }
            for (NoteAnchor a : sorted) {
                if (!onsets.isEmpty() && a.onsetQuarters() == onsets.get(onsets.size() - 1)) {
                    continue; // same time column (e.g. chord members) -> one sample
                }
                onsets.add(a.onsetQuarters());
                xs.add(a.x());
                systems.add(systemOf(layout, a.y()));
            }
            // Trailing sample at the end of the piece so motion continues through
            // the final note's duration to the closing barline.
            if (totalDurationQuarters > onsets.get(onsets.size() - 1)) {
                onsets.add(totalDurationQuarters);
                xs.add(layout.xAtTime(totalDurationQuarters));
                systems.add(Math.max(layout.systems().size() - 1, 0));
            }
        }
        this.sampleOnset = onsets.stream().mapToDouble(Double::doubleValue).toArray();
        this.sampleX = xs.stream().mapToDouble(Double::doubleValue).toArray();
        this.sampleSystem = systems.stream().mapToInt(Integer::intValue).toArray();
        this.revealOnsets = sorted.stream().mapToDouble(NoteAnchor::onsetQuarters).distinct().toArray();
    }

    /** The system whose staves are vertically closest to {@code y}. */
    private static int systemOf(LayoutResult layout, double y) {
        int best = 0;
        double bestDistance = Double.MAX_VALUE;
        List<SystemLayout> systems = layout.systems();
        for (int i = 0; i < systems.size(); i++) {
            double[] band = staffBand(systems.get(i));
            double distance = y < band[0] ? band[0] - y : Math.max(y - band[1], 0);
            if (distance < bestDistance) {
                best = i;
                bestDistance = distance;
            }
        }
        return best;
    }

    /** Top staff line of the first staff and bottom staff line of the last one. */
    private static double[] staffBand(SystemLayout system) {
        if (system.staves().isEmpty()) {
            return new double[] {system.y(), system.y()};
        }
        StaffLayout first = system.staves().get(0);
        StaffLayout last = system.staves().get(system.staves().size() - 1);
        return new double[] {first.y(), last.y() + last.lineGap() * 4};
    }

    /** Right end of the music on a system. */
    private static double systemEnd(SystemLayout system) {
        if (system.staves().isEmpty()) {
            return system.x() + system.width();
        }
        StaffLayout staff = system.staves().get(0);
        return staff.x() + staff.width();
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

    /**
     * The playback cursor at the given musical time on a layout of any
     * number of systems: like {@link #cursorX}, but when the next note is on
     * the next system the cursor glides to the end of the current one
     * instead of back across it, and the result tells which system to draw
     * the line on. Empty layouts give a zero-height cursor at x 0.
     */
    public CursorPlacement cursorAt(double quarters) {
        List<SystemLayout> systems = layout.systems();
        if (systems.isEmpty()) {
            return new CursorPlacement(0, 0, 0, 0);
        }
        int n = sampleOnset.length;
        double x;
        int system;
        if (n == 0) {
            x = layout.xAtTime(quarters);
            system = 0;
        } else if (quarters <= sampleOnset[0]) {
            x = sampleX[0];
            system = sampleSystem[0];
        } else if (quarters >= sampleOnset[n - 1]) {
            x = sampleX[n - 1];
            system = sampleSystem[n - 1];
        } else {
            int i = Arrays.binarySearch(sampleOnset, quarters);
            i = i >= 0 ? Math.max(i, 1) : -i - 1;
            double span = sampleOnset[i] - sampleOnset[i - 1];
            double f = span <= 0 ? 0 : (quarters - sampleOnset[i - 1]) / span;
            system = sampleSystem[i - 1];
            double target = sampleSystem[i] == system ? sampleX[i] : systemEnd(systems.get(system));
            x = sampleX[i - 1] + f * (target - sampleX[i - 1]);
        }
        system = Math.clamp(system, 0, systems.size() - 1);
        double[] band = staffBand(systems.get(system));
        return new CursorPlacement(x, band[0], band[1], system);
    }

    /**
     * How many distinct onsets of the layout's elements (notes, chords and
     * rests) lie at or before the given time. Views that only paint the
     * elements that have started (see {@code ScorePainter#setRevealTime})
     * need to repaint only when this changes.
     */
    public int revealStep(double quarters) {
        int i = Arrays.binarySearch(revealOnsets, quarters);
        if (i >= 0) {
            while (i + 1 < revealOnsets.length && revealOnsets[i + 1] <= quarters) {
                i++;
            }
            return i + 1;
        }
        return -i - 1;
    }

    /**
     * Every anchor (rests included) starting after {@code fromExclusive} and
     * at or before {@code toInclusive}, sorted by onset: the elements a
     * reveal from one time to the other uncovers.
     */
    public List<NoteAnchor> anchorsBetween(double fromExclusive, double toInclusive) {
        double lo = Math.min(fromExclusive, toInclusive);
        double hi = Math.max(fromExclusive, toInclusive);
        List<NoteAnchor> out = new ArrayList<>();
        for (NoteAnchor anchor : allAnchors) {
            if (anchor.onsetQuarters() > hi) {
                break;
            }
            if (anchor.onsetQuarters() > lo) {
                out.add(anchor);
            }
        }
        return out;
    }
}
