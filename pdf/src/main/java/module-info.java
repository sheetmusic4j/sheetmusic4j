/**
 * Sheetmusic4J engraved-PDF import: reads the font glyphs and vector paths of a notation-program PDF into a score.
 */
module com.sheetmusic4j.pdf {
    requires transitive com.sheetmusic4j.core;
    requires org.apache.pdfbox;
    requires java.desktop;
    requires org.slf4j;

    exports com.sheetmusic4j.pdf;

    provides com.sheetmusic4j.core.io.ScoreFormat with
            com.sheetmusic4j.pdf.PdfFormat;
}
