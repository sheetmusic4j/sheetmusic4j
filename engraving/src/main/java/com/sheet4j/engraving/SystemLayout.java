package com.sheet4j.engraving;

import java.util.List;

/**
 * A system: a horizontal band containing one staff per part. This first
 * iteration places every part's whole content on a single system.
 *
 * @param x      left edge
 * @param y      top edge
 * @param width  system width
 * @param staves the staves (one per part)
 */
public record SystemLayout(double x, double y, double width, List<StaffLayout> staves) {

    public SystemLayout {
        staves = List.copyOf(staves);
    }
}
