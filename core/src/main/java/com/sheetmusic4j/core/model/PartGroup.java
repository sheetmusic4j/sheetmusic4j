package com.sheetmusic4j.core.model;

/**
 * A logical grouping of consecutive {@link Part parts} within a {@link Score}.
 *
 * <p>MusicXML represents groups with paired
 * {@code <part-group number="N" type="start"/>} /
 * {@code <part-group number="N" type="stop"/>} sentinels inside
 * {@code <part-list>}. Groups may nest.
 *
 * <p>The {@link #startPartIndex} and {@link #endPartIndex} are inclusive
 * indices into {@link Score#parts()}. Groups keep their document order in
 * {@link Score#partGroups()} so that outer groups appear before nested
 * inner groups — the engraver relies on this to lay out multiple bracket
 * columns side-by-side.
 *
 * @param number          the MusicXML group number (used only to pair
 *                        start/stop sentinels — has no rendering meaning)
 * @param startPartIndex  inclusive index of the first grouped part
 * @param endPartIndex    inclusive index of the last grouped part
 * @param symbol          visual bracket style
 * @param groupBarline    whether the source requested a barline shared
 *                        across all grouped staves
 * @param name            optional {@code <group-name>} (may be {@code null})
 * @param abbreviation    optional {@code <group-abbreviation>} (may be
 *                        {@code null})
 */
public record PartGroup(int number, int startPartIndex, int endPartIndex,
                        GroupSymbol symbol, boolean groupBarline,
                        String name, String abbreviation) {

    /**
     * Canonical constructor validating the part-index range and defaulting
     * a {@code null} {@link GroupSymbol} to {@link GroupSymbol#NONE}.
     */
    public PartGroup {
        if (endPartIndex < startPartIndex) {
            throw new IllegalArgumentException(
                    "endPartIndex (" + endPartIndex + ") must be >= startPartIndex (" + startPartIndex + ")");
        }
        if (symbol == null) {
            symbol = GroupSymbol.NONE;
        }
    }

    /**
     * Whether this group's part range fully contains the given other
     * group's range. A group is not considered to contain itself.
     *
     * @param other the candidate inner group
     * @return {@code true} when this group's span strictly contains
     *         {@code other}'s span
     */
    public boolean contains(PartGroup other) {
        if (other == this) {
            return false;
        }
        return startPartIndex <= other.startPartIndex
                && endPartIndex >= other.endPartIndex
                && (startPartIndex < other.startPartIndex || endPartIndex > other.endPartIndex);
    }
}
