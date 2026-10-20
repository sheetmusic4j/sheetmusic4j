/**
 * Sheetmusic4J layout engine: turns a score into a renderer-agnostic layout.
 *
 * <p>Only the {@code Engraver} entry point and the layout result types are
 * exported; the {@code analysis}, {@code spacing}, {@code page} and
 * {@code staff} packages are implementation details.
 */
module com.sheetmusic4j.engraving {
    requires transitive com.sheetmusic4j.core;

    exports com.sheetmusic4j.engraving;
    exports com.sheetmusic4j.engraving.glyph;
    exports com.sheetmusic4j.engraving.layout;
    exports com.sheetmusic4j.engraving.placement;
}
