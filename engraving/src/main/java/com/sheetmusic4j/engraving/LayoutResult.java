package com.sheetmusic4j.engraving;

import java.util.List;

/**
 * The complete positioned layout of a score, ready for a renderer to draw.
 *
 * @param systems the systems making up the page
 * @param texts   page-level text placements (title, subtitle, composer, ...)
 *                that live outside any specific staff
 * @param width   total layout width
 * @param height  total layout height
 */
public record LayoutResult(List<SystemLayout> systems, List<TextPlacement> texts,
                           double width, double height) {

    public LayoutResult {
        systems = List.copyOf(systems);
        texts = List.copyOf(texts);
    }

    /**
     * Backwards-compatible constructor for callers that pre-date page-level
     * text placements.
     */
    public LayoutResult(List<SystemLayout> systems, double width, double height) {
        this(systems, List.of(), width, height);
    }

    public List<StaffLayout> staves() {
        return systems.stream().flatMap(s -> s.staves().stream()).toList();
    }
}
