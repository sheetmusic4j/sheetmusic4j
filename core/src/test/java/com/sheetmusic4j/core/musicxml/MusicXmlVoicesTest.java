package com.sheetmusic4j.core.musicxml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.sheetmusic4j.core.model.Fraction;
import com.sheetmusic4j.core.model.Measure;
import com.sheetmusic4j.core.model.MeasureTiming;
import com.sheetmusic4j.core.model.Note;
import com.sheetmusic4j.core.model.Rest;
import com.sheetmusic4j.core.model.Score;

class MusicXmlVoicesTest {

    /**
     * One 4/4 measure: voice 1 has two half notes; after a full backup,
     * voice 2 rests for a quarter (via forward) and then has a dotted half.
     */
    private static final String TWO_VOICES = """
            <?xml version="1.0" encoding="UTF-8"?>
            <score-partwise version="4.0">
              <part-list><score-part id="P1"><part-name>Voice</part-name></score-part></part-list>
              <part id="P1">
                <measure number="1">
                  <attributes><divisions>2</divisions><time><beats>4</beats><beat-type>4</beat-type></time></attributes>
                  <note><pitch><step>E</step><octave>5</octave></pitch><duration>4</duration><voice>1</voice><type>half</type></note>
                  <note><pitch><step>D</step><octave>5</octave></pitch><duration>4</duration><voice>1</voice><type>half</type></note>
                  <backup><duration>8</duration></backup>
                  <forward><duration>2</duration><voice>2</voice></forward>
                  <note><pitch><step>G</step><octave>4</octave></pitch><duration>6</duration><voice>2</voice><type>half</type><dot/></note>
                </measure>
              </part>
            </score-partwise>
            """;

    private static Score read(String xml) {
        return new MusicXmlReader().read(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void secondVoiceStartsAfterTheForwardGap() {
        Measure measure = read(TWO_VOICES).parts().get(0).measures().get(0);
        MeasureTiming timing = MeasureTiming.of(measure);
        assertEquals(Fraction.of(4), timing.duration(), "voices overlap instead of following each other");
        List<MeasureTiming.Timed> elements = timing.elements();
        MeasureTiming.Timed last = elements.get(elements.size() - 1);
        Note g = (Note) last.element();
        assertEquals(2, g.voice());
        assertEquals(Fraction.of(1), last.onset());
        Rest spacer = (Rest) elements.get(elements.size() - 2).element();
        assertFalse(spacer.visible());
    }

    @Test
    void voicesSurviveAWriteReadRoundTrip() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new MusicXmlWriter().write(read(TWO_VOICES), out);
        String xml = out.toString(StandardCharsets.UTF_8);
        assertTrue(xml.contains("<backup>"), xml);
        assertTrue(xml.contains("<voice>2</voice>"), xml);

        MeasureTiming timing = MeasureTiming.of(read(xml).parts().get(0).measures().get(0));
        assertEquals(Fraction.of(4), timing.duration());
        MeasureTiming.Timed last = timing.elements().get(timing.elements().size() - 1);
        assertEquals(Fraction.of(1), last.onset());
    }

    @Test
    void graceNotesAttachToTheFollowingNoteAndRoundTrip() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <score-partwise version="4.0">
                  <part-list><score-part id="P1"><part-name>Flute</part-name></score-part></part-list>
                  <part id="P1">
                    <measure number="1">
                      <attributes><divisions>1</divisions></attributes>
                      <note><grace/><pitch><step>D</step><octave>5</octave></pitch><type>eighth</type></note>
                      <note><pitch><step>C</step><octave>5</octave></pitch><duration>4</duration><type>whole</type></note>
                    </measure>
                  </part>
                </score-partwise>
                """;
        Measure measure = read(xml).parts().get(0).measures().get(0);
        assertEquals(1, measure.elements().size(), "grace note must not become an element of its own");
        Note c = (Note) measure.elements().get(0);
        assertEquals(1, c.graceNotes().size());

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new MusicXmlWriter().write(read(xml), out);
        Note again = (Note) read(out.toString(StandardCharsets.UTF_8)).parts().get(0).measures().get(0)
                .elements().get(0);
        assertEquals(c.graceNotes(), again.graceNotes());
    }
}
