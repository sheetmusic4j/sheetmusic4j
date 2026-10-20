package com.sheetmusic4j.engraving.page;

import java.util.List;

import com.sheetmusic4j.core.model.GroupSymbol;
import com.sheetmusic4j.core.model.PartGroup;
import com.sheetmusic4j.core.model.Score;
import com.sheetmusic4j.engraving.glyph.MarkingCategory;
import com.sheetmusic4j.engraving.layout.LayoutOptions;
import com.sheetmusic4j.engraving.layout.StaffLayout;
import com.sheetmusic4j.engraving.layout.SystemBarline;
import com.sheetmusic4j.engraving.placement.BracketPlacement;
import com.sheetmusic4j.engraving.placement.TextPlacement;
import com.sheetmusic4j.engraving.staff.StaffGeometry;

/**
 * Brackets, group barlines and group labels for the score's
 * {@link PartGroup part groups}. Nesting is computed once per score; each
 * row then emits its placements via {@link #emit}.
 */
public final class PartGroupLayout {

    /**
     * Horizontal spacing (in staff-line gaps) between adjacent bracket
     * columns emitted by {@code <part-group>}. Each column sits
     * {@code (depthFromInside + 1) * gap * BRACKET_COLUMN_STEP_GAPS} to the
     * left of {@code contentLeft}.
     */
    public static final double BRACKET_COLUMN_STEP_GAPS = 1.2;

    private final List<PartGroup> groups;
    private final int[] nestingDepths;
    private final int maxNestingDepth;

    public PartGroupLayout(Score score) {
        this.groups = score.partGroups();
        this.nestingDepths = computeGroupNestingDepths(groups);
        this.maxNestingDepth = maxOrMinusOne(nestingDepths);
    }

    /**
     * Largest group nesting depth, or {@code -1} when the score declares no
     * groups.
     */
    public int maxNestingDepth() {
        return maxNestingDepth;
    }

    /**
     * Compute the nesting depth of every group. A group's depth equals the
     * number of other groups whose part range fully contains it — outermost
     * groups have depth 0 and innermost groups have the maximum depth.
     *
     * @return array parallel to {@code groups}; empty when there are none
     */
    private static int[] computeGroupNestingDepths(List<PartGroup> groups) {
        int[] depths = new int[groups.size()];
        for (int i = 0; i < groups.size(); i++) {
            int count = 0;
            for (int j = 0; j < groups.size(); j++) {
                if (i != j && groups.get(j).contains(groups.get(i))) {
                    count++;
                }
            }
            depths[i] = count;
        }
        return depths;
    }

    /**
     * Maximum entry of the array, or {@code -1} when the array is empty.
     */
    private static int maxOrMinusOne(int[] values) {
        int max = -1;
        for (int v : values) {
            if (v > max) {
                max = v;
            }
        }
        return max;
    }

    /**
     * Emit bracket placements, optional group barlines, and group-name
     * labels for every {@link PartGroup} that intersects the current row.
     * Nesting: outer groups (fewest containers) sit furthest to the left,
     * inner groups sit closer to the staff. A group barline coinciding
     * exactly with the previously-emitted system left barline is skipped
     * to avoid drawing the same line twice.
     *
     * @param partFirstStaves    first staff of each part in this row
     *                           (null when the part has no staves in the row)
     * @param partLastStaves     last staff of each part in this row
     * @param contentLeft        x of the staff content edge / system barline
     * @param options            layout options (for the staff-line gap)
     * @param rowIdx             0-based row index (row 0 uses full names)
     * @param brackets           output list of bracket placements
     * @param barlines           output list of system barlines
     * @param texts              output list of text placements
     */
    public void emit(StaffLayout[] partFirstStaves, StaffLayout[] partLastStaves,
              double contentLeft, LayoutOptions options, int rowIdx,
              List<BracketPlacement> brackets, List<SystemBarline> barlines,
              List<TextPlacement> texts) {
        if (groups.isEmpty()) {
            return;
        }
        double gap = options.staffLineGap();
        double fontSize = gap * TextBlockLayout.PART_LABEL_FONT_SIZE_GAPS;
        // Pre-capture the system left barline (if any) so we can skip a
        // group barline that would draw exactly the same segment.
        SystemBarline systemLeftBarline = barlines.isEmpty() ? null : barlines.get(0);

        for (int i = 0; i < groups.size(); i++) {
            PartGroup group = groups.get(i);
            StaffLayout firstStaff = firstStaffInGroup(group, partFirstStaves);
            StaffLayout lastStaff = lastStaffInGroup(group, partLastStaves);
            if (firstStaff == null || lastStaff == null) {
                continue;
            }
            double topY = firstStaff.lineY(0);
            double bottomY = lastStaff.lineY(StaffGeometry.STAFF_LINES - 1);
            // Depth from the innermost group inwards: innermost = 0.
            int depthFromInside = maxNestingDepth - nestingDepths[i];
            double columnX = contentLeft - (depthFromInside + 1) * gap * BRACKET_COLUMN_STEP_GAPS;

            BracketPlacement.BracketShape shape = shapeFor(group.symbol());
            if (shape != null) {
                brackets.add(new BracketPlacement(columnX, topY, bottomY, shape));
            }
            if (group.groupBarline()) {
                boolean overlapsSystemBarline = systemLeftBarline != null
                        && Math.abs(systemLeftBarline.x() - contentLeft) < 1e-6
                        && Math.abs(systemLeftBarline.topY() - topY) < 1e-6
                        && Math.abs(systemLeftBarline.bottomY() - bottomY) < 1e-6;
                if (!overlapsSystemBarline) {
                    barlines.add(new SystemBarline(contentLeft, topY, bottomY,
                            SystemBarline.LineStyle.THIN));
                }
            }
            String labelText;
            if (rowIdx == 0) {
                labelText = group.name();
            } else {
                labelText = group.abbreviation() != null ? group.abbreviation() : group.name();
            }
            if (labelText != null && !labelText.isBlank() && options.isVisible(MarkingCategory.PART_LABEL)) {
                double labelY = (topY + bottomY) / 2.0 + fontSize / 2.0;
                texts.add(new TextPlacement(labelText, columnX, labelY,
                        fontSize, TextPlacement.Align.RIGHT, MarkingCategory.PART_LABEL));
            }
        }
    }

    /**
     * Map a MusicXML {@link GroupSymbol} to an engraved
     * {@link BracketPlacement.BracketShape}. Returns {@code null} when the
     * source requested {@link GroupSymbol#NONE}, which suppresses bracket
     * rendering entirely for that group.
     */
    private static BracketPlacement.BracketShape shapeFor(GroupSymbol symbol) {
        return switch (symbol) {
            case BRACKET -> BracketPlacement.BracketShape.BRACKET;
            case BRACE -> BracketPlacement.BracketShape.BRACE;
            case SQUARE -> BracketPlacement.BracketShape.SQUARE;
            case LINE -> BracketPlacement.BracketShape.LINE;
            case NONE -> null;
        };
    }

    /**
     * First staff belonging to any part inside the group's inclusive part
     * range that has a staff in this row.
     */
    private static StaffLayout firstStaffInGroup(PartGroup group, StaffLayout[] partFirstStaves) {
        int end = Math.min(group.endPartIndex(), partFirstStaves.length - 1);
        for (int idx = Math.max(0, group.startPartIndex()); idx <= end; idx++) {
            if (partFirstStaves[idx] != null) {
                return partFirstStaves[idx];
            }
        }
        return null;
    }

    /**
     * Last staff belonging to any part inside the group's inclusive part
     * range that has a staff in this row.
     */
    private static StaffLayout lastStaffInGroup(PartGroup group, StaffLayout[] partLastStaves) {
        int end = Math.min(group.endPartIndex(), partLastStaves.length - 1);
        for (int idx = end; idx >= Math.max(0, group.startPartIndex()); idx--) {
            if (partLastStaves[idx] != null) {
                return partLastStaves[idx];
            }
        }
        return null;
    }
}
