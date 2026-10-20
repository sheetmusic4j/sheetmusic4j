package com.sheetmusic4j.engraving.layout;

/**
 * Where a playback cursor line goes on a (possibly multi-system) layout: its
 * x and the vertical extent of the system it is on, in layout units.
 *
 * @param x           cursor x
 * @param top         top of the line: the top staff line of the system
 * @param bottom      bottom of the line: the bottom staff line of the system
 * @param systemIndex index into {@link LayoutResult#systems()}
 */
public record CursorPlacement(double x, double top, double bottom, int systemIndex) {
}
