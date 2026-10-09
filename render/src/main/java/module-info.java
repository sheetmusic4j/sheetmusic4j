/**
 * Sheetmusic4J toolkit-neutral rendering: {@code ScorePainter} draws a layout
 * through the {@code RenderSurface} abstraction; {@code render.awt} provides
 * an AWT surface and headless image rendering.
 */
module com.sheetmusic4j.render {
    requires transitive com.sheetmusic4j.engraving;
    requires transitive java.desktop;

    exports com.sheetmusic4j.render;
    exports com.sheetmusic4j.render.awt;
}
