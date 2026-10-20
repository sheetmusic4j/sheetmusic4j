package com.sheetmusic4j.engraving.layout;

/**
 * How the engraver should distribute a score's measures into systems.
 */
public enum LayoutMode {

    /**
     * Traditional page layout: measures are packed left-to-right into a
     * system until the next one would overflow
     * {@link LayoutOptions#systemWidth()}, at which point a new system is
     * started below. Multiple systems are produced for longer scores.
     */
    PAGE,

    /**
     * Single-line "strip" layout: every measure is placed on one endless
     * system with no row breaks. {@link LayoutOptions#systemWidth()} is
     * ignored; the resulting {@link LayoutResult#width()} equals the natural
     * width of the entire score. Suited for scrolling playback views (see
     * {@code StripSheetView} and {@code FlyOverSheetView} in the fxviewer
     * module).
     */
    STRIP
}
