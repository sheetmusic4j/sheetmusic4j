package com.sheetmusic4j.engraving;

import java.util.List;

/**
 * A system: a horizontal band containing one staff per part (or multiple
 * staves for grand-staff parts). Beyond the staves themselves a system
 * carries cross-staff decorations that don't belong to any specific
 * {@link StaffLayout}:
 * <ul>
 *   <li>{@link #barlines()} — vertical barlines spanning the whole system
 *   (e.g. the single left barline that joins all staves of a multi-part
 *   score).</li>
 *   <li>{@link #brackets()} — vertical grouping marks (braces / brackets)
 *   drawn at the left edge to signal that a run of staves belongs to the
 *   same part or instrument family.</li>
 * </ul>
 *
 * @param x        left edge
 * @param y        top edge
 * @param width    system width
 * @param staves   the staves in top-to-bottom order
 * @param barlines system-wide barlines (see {@link SystemBarline})
 * @param brackets grouping marks (see {@link BracketPlacement})
 */
public record SystemLayout(double x, double y, double width,
                           List<StaffLayout> staves,
                           List<SystemBarline> barlines,
                           List<BracketPlacement> brackets) {

    public SystemLayout {
        staves = List.copyOf(staves);
        barlines = List.copyOf(barlines);
        brackets = List.copyOf(brackets);
    }

    /**
     * Backwards-compatible constructor for callers that pre-date system
     * barlines and brackets. Defaults both lists to empty.
     *
     * @param x      left edge
     * @param y      top edge
     * @param width  system width
     * @param staves the staves in top-to-bottom order
     */
    public SystemLayout(double x, double y, double width, List<StaffLayout> staves) {
        this(x, y, width, staves, List.of(), List.of());
    }
}
