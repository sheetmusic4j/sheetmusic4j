/**
 * Sheetmusic4J domain model plus MusicXML, MIDI, ABC and GuitarPro import/export.
 */
module com.sheetmusic4j.core {
    requires java.xml;
    requires java.desktop;
    requires org.slf4j;

    exports com.sheetmusic4j.core.model;
    exports com.sheetmusic4j.core.io;
    exports com.sheetmusic4j.core.musicxml;
    exports com.sheetmusic4j.core.midi;
    exports com.sheetmusic4j.core.abc;
    exports com.sheetmusic4j.core.guitarpro;

    uses com.sheetmusic4j.core.io.ScoreFormat;
}
