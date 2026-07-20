package com.sheetmusic4j.engraving;

import java.util.List;

/**
 * The complete positioned layout of a score, ready for a renderer to draw.
 *
 * @param systems the systems making up the page
 * @param width   total layout width
 * @param height  total layout height
 */
public record LayoutResult(List<SystemLayout> systems, double width, double height) {

    public LayoutResult {
        systems = List.copyOf(systems);
    }

    public List<StaffLayout> staves() {
        return systems.stream().flatMap(s -> s.staves().stream()).toList();
    }
}
