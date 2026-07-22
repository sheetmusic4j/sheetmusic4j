package com.sheetmusic4j.engraving.layout;

import java.util.ArrayList;
import java.util.List;

import com.sheetmusic4j.engraving.placement.TextPlacement;

/**
 * The complete positioned layout of a score, ready for a renderer to draw.
 *
 * @param systems      the systems making up the page
 * @param texts        page-level text placements (title, subtitle, composer,
 *                     ...) that live outside any specific staff
 * @param noteAnchors  identity + musical time + bounding box for every
 *                     rendered note/rest/chord, sorted by onset quarters;
 *                     enables per-note highlighting and cursor positioning
 * @param width        total layout width
 * @param height       total layout height
 */
public record LayoutResult(List<SystemLayout> systems, List<TextPlacement> texts,
                           List<NoteAnchor> noteAnchors,
                           double width, double height) {

    public LayoutResult {
        systems = List.copyOf(systems);
        texts = List.copyOf(texts);
        noteAnchors = List.copyOf(noteAnchors);
    }

    /**
     * Backwards-compatible constructor for callers that pre-date note
     * anchors.
     */
    public LayoutResult(List<SystemLayout> systems, List<TextPlacement> texts,
                        double width, double height) {
        this(systems, texts, List.of(), width, height);
    }

    /**
     * Backwards-compatible constructor for callers that pre-date page-level
     * text placements.
     */
    public LayoutResult(List<SystemLayout> systems, double width, double height) {
        this(systems, List.of(), List.of(), width, height);
    }

    public List<StaffLayout> staves() {
        return systems.stream().flatMap(s -> s.staves().stream()).toList();
    }

    /**
     * Total musical duration of the layout, in quarter notes. Equal to the
     * end-quarters of the last measure across all parts; zero when the
     * layout carries no note anchors.
     */
    public double totalDurationQuarters() {
        double max = 0.0;
        for (NoteAnchor anchor : noteAnchors) {
            max = Math.max(max, anchor.endQuarters());
        }
        for (SystemLayout system : systems) {
            for (StaffLayout staff : system.staves()) {
                for (MeasureLayout measure : staff.measures()) {
                    max = Math.max(max, measure.endQuarters());
                }
            }
        }
        return max;
    }

    /**
     * Piecewise-linear x-position for a playback cursor at the given musical
     * time. The time is clamped to {@code [0, totalDurationQuarters()]}. For
     * each measure we linearly interpolate between its left edge and right
     * edge based on where the cursor time falls inside the measure's
     * musical range; measures without a musical range fall back to the
     * layout's overall time range.
     *
     * <p>Only measures from the first staff of the first system are
     * consulted, since a multi-part score aligns measures across staves on
     * the same x by construction.
     *
     * @param quarters musical time in quarter notes
     * @return the x coordinate in layout units, or {@code 0} when the
     *         layout is empty
     */
    public double xAtTime(double quarters) {
        List<MeasureLayout> primary = primaryMeasures();
        if (primary.isEmpty()) {
            return 0.0;
        }
        double total = totalDurationQuarters();
        if (total <= 0) {
            return primary.get(0).x();
        }
        double clamped = Math.max(0.0, Math.min(quarters, total));
        for (MeasureLayout m : primary) {
            double dur = m.durationQuarters();
            if (dur <= 0) {
                continue;
            }
            if (clamped >= m.startQuarters() && clamped <= m.endQuarters()) {
                double t = (clamped - m.startQuarters()) / dur;
                return m.x() + t * m.width();
            }
        }
        // Fall through: past the end of the last measure with time info.
        MeasureLayout last = primary.get(primary.size() - 1);
        return last.right();
    }

    /**
     * The measures of the first staff of the first system, in document
     * order. Used by {@link #xAtTime(double)} as the reference timeline;
     * across-part alignment means all staves share these x positions.
     */
    private List<MeasureLayout> primaryMeasures() {
        List<MeasureLayout> out = new ArrayList<>();
        if (systems.isEmpty()) {
            return out;
        }
        for (SystemLayout system : systems) {
            if (system.staves().isEmpty()) {
                continue;
            }
            out.addAll(system.staves().get(0).measures());
        }
        return out;
    }
}
