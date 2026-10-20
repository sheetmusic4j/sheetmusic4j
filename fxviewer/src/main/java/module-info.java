/**
 * Sheetmusic4J JavaFX controls: {@code SheetView}, {@code StripSheetView},
 * {@code NodeSheetView} and {@code FlyOverSheetView}, drawing through the
 * render module's {@code ScorePainter} onto JavaFX surfaces.
 */
module com.sheetmusic4j.fxviewer {
    requires transitive com.sheetmusic4j.render;
    requires transitive javafx.graphics;

    exports com.sheetmusic4j.fxviewer;
}
