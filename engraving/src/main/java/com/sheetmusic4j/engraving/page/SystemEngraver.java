package com.sheetmusic4j.engraving.page;

import java.util.ArrayList;
import java.util.List;

import com.sheetmusic4j.engraving.analysis.MeasureTimeline;
import com.sheetmusic4j.engraving.analysis.PartInfo;
import com.sheetmusic4j.engraving.layout.LayoutOptions;
import com.sheetmusic4j.engraving.layout.NoteAnchor;
import com.sheetmusic4j.engraving.layout.StaffLayout;
import com.sheetmusic4j.engraving.layout.SystemBarline;
import com.sheetmusic4j.engraving.layout.SystemLayout;
import com.sheetmusic4j.engraving.placement.BracketPlacement;
import com.sheetmusic4j.engraving.placement.TextPlacement;
import com.sheetmusic4j.engraving.spacing.HorizontalSpacing;
import com.sheetmusic4j.engraving.spacing.RowPlan;
import com.sheetmusic4j.engraving.staff.RowContext;
import com.sheetmusic4j.engraving.staff.StaffGeometry;
import com.sheetmusic4j.engraving.staff.StaffRowEngraver;

/**
 * Lays out each row of a {@link RowPlan} as one {@link SystemLayout}:
 * stacks every part's staves vertically (with room above and below for
 * markings), then adds part labels, the grand-staff brace, the system
 * barline and the part-group brackets.
 */
public final class SystemEngraver {

    /**
     * Additional vertical space (in staff-line gaps) reserved below the last
     * staff of a part when that part carries at least one verse-1 lyric.
     */
    private static final double LYRIC_RESERVE_GAPS = 3.2;

    /**
     * Additional vertical space (in staff-line gaps) reserved below the last
     * staff of a part when that part carries at least one direction placed
     * below the staff (e.g. a dynamic). Sized to match {@link
     * #LYRIC_RESERVE_GAPS} so lyrics and dynamics share the same slot for
     * the MVP; a fine-grained collision policy is a follow-up.
     */
    private static final double DIRECTION_BELOW_RESERVE_GAPS = 3.2;

    /**
     * Additional vertical space (in staff-line gaps) reserved above the top
     * staff of a system when at least one part in that system carries a
     * direction placed above the staff.
     */
    private static final double DIRECTION_ABOVE_RESERVE_GAPS = 3.0;

    /**
     * Additional vertical space (in staff-line gaps) reserved above the top
     * staff of a system when at least one part in that system carries a
     * chord symbol. When both {@link #DIRECTION_ABOVE_RESERVE_GAPS above
     * directions} and chord symbols are present in the same row we sum the
     * two reserves so the two categories don't visually overlap.
     */
    private static final double HARMONY_ABOVE_RESERVE_GAPS = 2.0;

    private final LayoutOptions options;
    private final List<PartInfo> parts;
    private final PartGroupLayout partGroups;
    private final RowPlan plan;
    private final MeasureTimeline timeline;
    private final double contentLeft;
    private final List<TextPlacement> texts;
    private final List<NoteAnchor> anchors;
    private final List<SystemLayout> systems = new ArrayList<>();

    /**
     * @param options     layout options
     * @param parts       per-part metadata, in score order
     * @param partGroups  the score's part groups
     * @param plan        row ranges and measure widths
     * @param timeline    musical onset and duration per measure index
     * @param contentLeft x of the staff content edge / system barline
     * @param texts       page-level text collector
     * @param anchors     note-anchor collector
     */
    public SystemEngraver(LayoutOptions options, List<PartInfo> parts, PartGroupLayout partGroups,
                          RowPlan plan, MeasureTimeline timeline, double contentLeft,
                          List<TextPlacement> texts, List<NoteAnchor> anchors) {
        this.options = options;
        this.parts = parts;
        this.partGroups = partGroups;
        this.plan = plan;
        this.timeline = timeline;
        this.contentLeft = contentLeft;
        this.texts = texts;
        this.anchors = anchors;
    }

    /**
     * The systems laid out so far, in row order.
     */
    public List<SystemLayout> systems() {
        return systems;
    }

    /**
     * Lay out row {@code rowIdx} as one system whose content starts at
     * {@code y}, append it to {@link #systems()}, and return the y where the
     * next system starts.
     */
    public double layoutRow(int rowIdx, double y) {
        int[] range = plan.rows().get(rowIdx);
        int start = range[0];
        int endExclusive = range[1];

        double rowHeader = HorizontalSpacing.maxHeaderAdvanceAtRowStart(parts, start, options);
        List<Double> rowWidths = HorizontalSpacing.stretchRowWidths(plan.baseWidths(), start, endExclusive,
                Math.max(0, plan.staffWidth() - rowHeader), rowHeader);
        RowContext row = new RowContext(options, rowIdx == 0, start, endExclusive, rowWidths, rowHeader,
                timeline, plan.pixelsPerQuarter());

        List<StaffLayout> stavesForRow = new ArrayList<>();
        List<BracketPlacement> bracketsForRow = new ArrayList<>();
        // Per-part first/last staff layouts for this row, indexed by
        // score.parts() position. Used to resolve per-group bracket
        // spans after all parts have been laid out.
        StaffLayout[] partFirstStaves = new StaffLayout[parts.size()];
        StaffLayout[] partLastStaves = new StaffLayout[parts.size()];
        double staffTop = TextBlockLayout.layoutSectionTitle(parts, start, y, options, texts);
        staffTop = reserveAboveStaves(start, endExclusive, staffTop);
        int systemStaffIndex = 0;
        for (int partIdx = 0; partIdx < parts.size(); partIdx++) {
            PartInfo p = parts.get(partIdx);
            StaffLayout firstStaffOfPart = null;
            StaffLayout lastStaffOfPart = null;
            for (int staffIdx = 0; staffIdx < p.staveCount(); staffIdx++) {
                StaffLayout sl = new StaffRowEngraver(row, p, staffIdx, partIdx, systemStaffIndex,
                        contentLeft, staffTop, texts, anchors).layout();
                systemStaffIndex++;
                stavesForRow.add(sl);
                if (firstStaffOfPart == null) {
                    firstStaffOfPart = sl;
                }
                lastStaffOfPart = sl;
                staffTop += options.staffHeight() + options.staffSpacing();
            }
            if (firstStaffOfPart != null) {
                TextBlockLayout.emitPartLabel(texts, p.part(), rowIdx,
                        firstStaffOfPart, lastStaffOfPart, options);
                if (p.staveCount() > 1) {
                    bracketsForRow.add(new BracketPlacement(
                            contentLeft - options.staffLineGap() * 0.8,
                            firstStaffOfPart.lineY(0),
                            lastStaffOfPart.lineY(StaffGeometry.STAFF_LINES - 1),
                            BracketPlacement.BracketShape.BRACE));
                }
                partFirstStaves[partIdx] = firstStaffOfPart;
                partLastStaves[partIdx] = lastStaffOfPart;
            }
            staffTop += belowStaffReserve(p);
        }
        List<SystemBarline> barlinesForRow = new ArrayList<>();
        if (!stavesForRow.isEmpty()) {
            StaffLayout firstStaff = stavesForRow.get(0);
            StaffLayout lastStaff = stavesForRow.get(stavesForRow.size() - 1);
            barlinesForRow.add(new SystemBarline(
                    contentLeft,
                    firstStaff.lineY(0),
                    lastStaff.lineY(StaffGeometry.STAFF_LINES - 1),
                    SystemBarline.LineStyle.THIN));
        }
        partGroups.emit(partFirstStaves, partLastStaves, contentLeft, options, rowIdx,
                bracketsForRow, barlinesForRow, texts);
        systems.add(new SystemLayout(0, y, plan.systemWidth(),
                stavesForRow, barlinesForRow, bracketsForRow));
        return staffTop;
    }

    /**
     * Push the row's first staff down to make room for directions and chord
     * symbols drawn above it, when any part carries them within the row.
     */
    private double reserveAboveStaves(int start, int endExclusive, double staffTop) {
        boolean rowHasAboveDirections = false;
        boolean rowHasHarmony = false;
        for (PartInfo p : parts) {
            if (!rowHasAboveDirections && p.hasDirectionsAboveInRange(start, endExclusive)) {
                rowHasAboveDirections = true;
            }
            if (!rowHasHarmony && p.hasHarmonyInRange(start, endExclusive)) {
                rowHasHarmony = true;
            }
            if (rowHasAboveDirections && rowHasHarmony) {
                break;
            }
        }
        if (rowHasAboveDirections) {
            staffTop += options.staffLineGap() * DIRECTION_ABOVE_RESERVE_GAPS;
        }
        if (rowHasHarmony) {
            staffTop += options.staffLineGap() * HARMONY_ABOVE_RESERVE_GAPS;
        }
        return staffTop;
    }

    /**
     * Extra space below a part's last staff for its lyrics and below-staff
     * directions, which share one slot.
     */
    private double belowStaffReserve(PartInfo p) {
        double belowReserve = 0.0;
        if (p.hasLyrics()) {
            belowReserve = Math.max(belowReserve,
                    options.staffLineGap() * LYRIC_RESERVE_GAPS);
        }
        if (p.hasDirectionsBelow()) {
            belowReserve = Math.max(belowReserve,
                    options.staffLineGap() * DIRECTION_BELOW_RESERVE_GAPS);
        }
        return belowReserve;
    }
}
