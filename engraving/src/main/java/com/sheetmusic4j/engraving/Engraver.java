package com.sheetmusic4j.engraving;

import com.sheetmusic4j.core.model.Attributes;
import com.sheetmusic4j.core.model.Part;
import com.sheetmusic4j.core.model.Score;
import com.sheetmusic4j.engraving.analysis.MeasureTimeline;
import com.sheetmusic4j.engraving.analysis.PartInfo;
import com.sheetmusic4j.engraving.analysis.ScoreAnalysis;
import com.sheetmusic4j.engraving.glyph.Glyph;
import com.sheetmusic4j.engraving.layout.LayoutOptions;
import com.sheetmusic4j.engraving.layout.LayoutResult;
import com.sheetmusic4j.engraving.layout.NoteAnchor;
import com.sheetmusic4j.engraving.layout.SystemLayout;
import com.sheetmusic4j.engraving.page.PartGroupLayout;
import com.sheetmusic4j.engraving.page.SystemEngraver;
import com.sheetmusic4j.engraving.page.TextBlockLayout;
import com.sheetmusic4j.engraving.placement.TextPlacement;
import com.sheetmusic4j.engraving.spacing.HorizontalSpacing;
import com.sheetmusic4j.engraving.spacing.RowPlan;
import com.sheetmusic4j.engraving.staff.NoteAnchors;
import com.sheetmusic4j.engraving.staff.StaffRowEngraver;

import java.util.ArrayList;
import java.util.List;


/**
 * Turns a {@link Score} into a framework-agnostic {@link LayoutResult}.
 *
 * <p>Layout pipeline:
 * <ul>
 *   <li>lays every part out as one or more staves (multi-staff piano grand
 *       staff supported),</li>
 *   <li>packs measures into one or more {@link SystemLayout systems}, breaking
 *       the row whenever the next measure would overflow the available
 *       {@link LayoutOptions#systemWidth() staff width},</li>
 *   <li>aligns measure boundaries across parts by computing a shared
 *       per-measure width from the widest weight seen across all parts,</li>
 *   <li>re-emits the clef (and current key signature, and time signature only
 *       when it just changed) at the start of every new system,</li>
 *   <li>emits real per-digit time signature glyphs based on
 *       {@link Attributes#timeSignature()},</li>
 *   <li>emits key-signature accidentals after the clef,</li>
 *   <li>emits {@link Glyph#STEM_UP}/{@link Glyph#STEM_DOWN} placements for
 *       notes whose duration is shorter than a whole note, with direction
 *       chosen by staff position (down when above the middle line, up
 *       otherwise),</li>
 *   <li>emits flags for unbeamed short notes and beam segments for beamed
 *       groups,</li>
 *   <li>emits accidentals, augmentation dots, and tie arcs when the note
 *       carries them,</li>
 *   <li>sizes measures proportionally to the sum of their note/rest durations,
 *       clamped by {@link LayoutOptions#measureMinWidth()}.</li>
 * </ul>
 *
 * <p>This class only orchestrates the pipeline; the work is done by:
 * <ul>
 *   <li>{@code analysis} - geometry-free score queries ({@link PartInfo},
 *       {@link MeasureTimeline}, {@link ScoreAnalysis}),</li>
 *   <li>{@code spacing} - measure widths and row breaking
 *       ({@link HorizontalSpacing}, {@link RowPlan}),</li>
 *   <li>{@code page} - assembling each row into a system, titles, labels
 *       and part-group brackets ({@link SystemEngraver},
 *       {@link TextBlockLayout}, {@link PartGroupLayout}),</li>
 *   <li>{@code staff} - everything drawn on one staff of one row
 *       ({@link StaffRowEngraver} and its helpers).</li>
 * </ul>
 */
public final class Engraver {

    public LayoutResult layout(Score score, LayoutOptions options) {
        // 1. Title, subtitle and creators at the top of the page.
        List<TextPlacement> texts = new ArrayList<>();
        List<NoteAnchor> anchors = new ArrayList<>();
        double titleBlockHeight = TextBlockLayout.layoutTitleBlock(score, options, texts);

        // 2. Collect per-part clef/key/time state; nothing to lay out without measures.
        List<PartInfo> parts = new ArrayList<>(score.parts().size());
        int measureCount = 0;
        for (Part part : score.parts()) {
            parts.add(PartInfo.of(part));
            measureCount = Math.max(measureCount, part.measures().size());
        }
        if (measureCount == 0) {
            SystemLayout empty = new SystemLayout(0, 0, options.systemWidth(), List.of());
            return new LayoutResult(List.of(empty), texts, List.of(), options.systemWidth(),
                    options.topMargin() + titleBlockHeight + options.staffHeight());
        }

        // 3. Reserve room left of the staves for part labels and group brackets.
        PartGroupLayout partGroups = new PartGroupLayout(score);
        double labelReserve = TextBlockLayout.computeLabelReserve(score, options, partGroups.maxNestingDepth());
        double contentLeft = options.leftMargin() + labelReserve;

        // 4. Musical timing per measure, then measure widths and the split into rows.
        MeasureTimeline timeline = MeasureTimeline.of(parts, measureCount);
        RowPlan plan = RowPlan.of(parts, timeline, measureCount, contentLeft, options);
        SystemEngraver systemEngraver = new SystemEngraver(options, parts, partGroups, plan, timeline,
                contentLeft, texts, anchors);

        // 5. Lay out each row as a system, stacked top to bottom.
        double y = options.topMargin() + titleBlockHeight;
        for (int rowIdx = 0; rowIdx < plan.rows().size(); rowIdx++) {
            y = systemEngraver.layoutRow(rowIdx, y);
        }
        List<SystemLayout> systems = systemEngraver.systems();

        // 6. Words after the tune (ABC W:) below the last system.
        double postTuneBottom = TextBlockLayout.layoutPostTuneText(score, options, y, texts);

        // 7. Total height: the staves and post-tune text, or lower content if that reaches further.
        double staffOnlyBase = y - options.staffSpacing();
        double staffOnlyHeight = (postTuneBottom > y ? postTuneBottom : staffOnlyBase) + options.rightMargin();
        // Expand the layout's reported height so it also covers any
        // glyph, stem, beam or note anchor that sits below the last
        // staff (e.g. ledger lines for notes well below the bottom
        // staff line, like C2 on a bass staff). Without this,
        // downstream renderers that size their surface to
        // layout.height() clip low ledger lines at the canvas edge.
        double contentBottom = NoteAnchors.contentBottom(systems, anchors);
        double height = Math.max(staffOnlyHeight,
                contentBottom + options.rightMargin());

        // 8. Anchors in playback order, for cursor/highlight lookups.
        anchors.sort((a, b) -> Double.compare(a.onsetQuarters(), b.onsetQuarters()));
        return new LayoutResult(systems, texts, anchors, plan.systemWidth(), height);
    }
}
