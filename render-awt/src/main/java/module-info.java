/**
 * Sheetmusic4J AWT binding: {@code AwtRenderSurface} draws a layout through
 * {@code Graphics2D}, and {@code HeadlessScoreImage} renders a score to a
 * {@code BufferedImage} without a display.
 */
module com.sheetmusic4j.render.awt {
    requires transitive com.sheetmusic4j.render;
    requires transitive java.desktop;

    exports com.sheetmusic4j.render.awt;
}
