package com.sheetmusic4j.engraving.layout;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.sheetmusic4j.engraving.placement.BeamPlacement;
import com.sheetmusic4j.engraving.placement.GlyphPlacement;
import com.sheetmusic4j.engraving.placement.StemPlacement;
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

    /**
     * Finds the note, rest or chord drawn at the given point, for mouse
     * picking. The point is tested against each {@link NoteAnchor} bounding
     * box grown by {@code tolerance} on every side; when several boxes match
     * (for example a chord's neighbours), the one whose centre is nearest wins.
     *
     * @param x         x in layout units
     * @param y         y in layout units
     * @param tolerance extra pick distance in layout units, {@code 0} for an exact hit
     * @return the matching anchor, or empty when nothing is there
     */
    public Optional<NoteAnchor> hitTest(double x, double y, double tolerance) {
        NoteAnchor best = null;
        double bestDistance = Double.MAX_VALUE;
        for (NoteAnchor a : noteAnchors) {
            double halfW = a.width() / 2.0 + tolerance;
            double halfH = a.height() / 2.0 + tolerance;
            double dx = x - a.x();
            double dy = y - a.y();
            if (Math.abs(dx) <= halfW && Math.abs(dy) <= halfH) {
                double distance = dx * dx + dy * dy;
                if (distance < bestDistance) {
                    best = a;
                    bestDistance = distance;
                }
            }
        }
        return Optional.ofNullable(best);
    }

    /** Same as {@link #hitTest(double, double, double)} with no tolerance. */
    public Optional<NoteAnchor> hitTest(double x, double y) {
        return hitTest(x, y, 0.0);
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
     * The maximum y coordinate reached by any glyph, stem, beam or note
     * anchor in the layout, i.e. the true bottom edge of the drawn
     * content. Notes with several ledger lines below the last staff line
     * (e.g. C2 on a bass staff) can extend below the pure staff area, and
     * this method reports how far. Callers that translate the drawing
     * origin (rather than relying on the pre-expanded
     * {@link #height()}) can use this to size their surface exactly.
     */
    public double contentBottom() {
        double bottom = 0.0;
        for (SystemLayout system : systems) {
            for (StaffLayout staff : system.staves()) {
                double gap = staff.lineGap();
                for (GlyphPlacement gp : staff.glyphs()) {
                    bottom = Math.max(bottom, gp.y() + gap);
                }
                for (StemPlacement sp : staff.stems()) {
                    bottom = Math.max(bottom, Math.max(sp.y1(), sp.y2()));
                }
                for (BeamPlacement bp : staff.beams()) {
                    bottom = Math.max(bottom, Math.max(bp.y1(), bp.y2()));
                }
            }
        }
        for (NoteAnchor a : noteAnchors) {
            bottom = Math.max(bottom, a.y() + a.height() / 2.0);
        }
        return bottom;
    }

    /**
     * The minimum y coordinate reached by any glyph, stem, beam or note
     * anchor. Negative when the drawn content extends above the layout's
     * nominal top edge (e.g. high ledger lines above a treble staff);
     * {@code 0} otherwise. Renderers that want to translate the drawing
     * origin instead of relying on {@link #height()}'s expansion can use
     * this alongside {@link #contentBottom()} to compute the exact drawn
     * bounds.
     */
    public double contentTop() {
        double top = Double.POSITIVE_INFINITY;
        boolean any = false;
        for (SystemLayout system : systems) {
            for (StaffLayout staff : system.staves()) {
                double gap = staff.lineGap();
                for (GlyphPlacement gp : staff.glyphs()) {
                    top = Math.min(top, gp.y() - gap);
                    any = true;
                }
                for (StemPlacement sp : staff.stems()) {
                    top = Math.min(top, Math.min(sp.y1(), sp.y2()));
                    any = true;
                }
                for (BeamPlacement bp : staff.beams()) {
                    top = Math.min(top, Math.min(bp.y1(), bp.y2()));
                    any = true;
                }
            }
        }
        for (NoteAnchor a : noteAnchors) {
            top = Math.min(top, a.y() - a.height() / 2.0);
            any = true;
        }
        return any ? top : 0.0;
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
