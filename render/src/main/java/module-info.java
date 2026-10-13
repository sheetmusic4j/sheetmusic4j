/**
 * Sheetmusic4J toolkit-neutral rendering: {@code ScorePainter} draws a layout
 * through the {@code RenderSurface} abstraction. Toolkit bindings (AWT in
 * {@code render-awt}, JavaFX in {@code fxviewer}) implement the surface.
 */
module com.sheetmusic4j.render {
    requires transitive com.sheetmusic4j.engraving;

    exports com.sheetmusic4j.render;
}
