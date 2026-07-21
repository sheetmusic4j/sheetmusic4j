package com.sheetmusic4j.core.musicxml;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.sheetmusic4j.core.model.Accidental;
import com.sheetmusic4j.core.model.Attributes;
import com.sheetmusic4j.core.model.Beam;
import com.sheetmusic4j.core.model.MusicElement;
import com.sheetmusic4j.core.model.Note;
import com.sheetmusic4j.core.model.Part;
import com.sheetmusic4j.core.model.Score;
import com.sheetmusic4j.core.model.Step;

class MusicXmlReaderTest {

    private Score readSample() {
        try (InputStream in = getClass().getResourceAsStream("/c-major-scale.musicxml")) {
            assertNotNull(in, "sample resource must exist");
            return new MusicXmlReader().read(in);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void readsTitleAndParts() {
        Score score = readSample();
        assertEquals("C Major Scale", score.workTitle().orElse(null));
        assertEquals(1, score.parts().size());
        assertEquals("Piano", score.parts().get(0).name());
    }

    @Test
    void readsMeasuresAndNotes() {
        Score score = readSample();
        Part part = score.parts().get(0);
        assertEquals(2, part.measures().size());
        assertEquals(4, part.measures().get(0).elements().size());

        MusicElement first = part.measures().get(0).elements().get(0);
        assertInstanceOf(Note.class, first);
        assertEquals(Step.C, ((Note) first).pitch().step());
        assertEquals(4, ((Note) first).pitch().octave());
    }

    @Test
    void readsAttributes() {
        Score score = readSample();
        Attributes attributes = score.parts().get(0).measures().get(0).attributes().orElseThrow();
        assertEquals(1, attributes.divisions().orElseThrow());
        assertEquals(0, attributes.keySignature().orElseThrow().fifths());
        assertEquals(4, attributes.timeSignature().orElseThrow().beats());
        assertTrue(attributes.clef().isPresent());
    }

    @Test
    void readsBeamsAndAccidentals() {
        String xml = """
                <?xml version=\"1.0\" encoding=\"UTF-8\"?>
                <score-partwise version=\"4.0\">
                  <part-list><score-part id=\"P1\"><part-name>Test</part-name></score-part></part-list>
                  <part id=\"P1\">
                    <measure number=\"1\">
                      <attributes>
                        <divisions>2</divisions>
                        <key><fifths>1</fifths></key>
                        <time><beats>4</beats><beat-type>4</beat-type></time>
                        <clef><sign>G</sign><line>2</line></clef>
                      </attributes>
                      <note>
                        <pitch><step>F</step><alter>1</alter><octave>5</octave></pitch>
                        <duration>1</duration>
                        <type>eighth</type>
                        <accidental>sharp</accidental>
                        <beam number=\"1\">begin</beam>
                      </note>
                      <note>
                        <pitch><step>G</step><octave>5</octave></pitch>
                        <duration>1</duration>
                        <type>eighth</type>
                        <beam number=\"1\">end</beam>
                      </note>
                    </measure>
                  </part>
                </score-partwise>
                """;
        Score score = new MusicXmlReader().read(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));

        Note first = (Note) score.parts().get(0).measures().get(0).elements().get(0);
        assertEquals(Accidental.SHARP, first.displayedAccidental().orElseThrow());
        assertEquals(1, first.beams().size());
        assertEquals(Beam.State.BEGIN, first.beams().get(0).state());

        Note second = (Note) score.parts().get(0).measures().get(0).elements().get(1);
        assertEquals(1, second.beams().size());
        assertEquals(Beam.State.END, second.beams().get(0).state());
    }

    @Test
    void readsMultiStaffPart() {
        String xml = """
                <?xml version=\"1.0\" encoding=\"UTF-8\"?>
                <score-partwise version=\"4.0\">
                  <part-list><score-part id=\"P1\"><part-name>Piano</part-name></score-part></part-list>
                  <part id=\"P1\">
                    <measure number=\"1\">
                      <attributes>
                        <divisions>1</divisions>
                        <staves>2</staves>
                        <clef number=\"1\"><sign>G</sign><line>2</line></clef>
                        <clef number=\"2\"><sign>F</sign><line>4</line></clef>
                      </attributes>
                      <note>
                        <pitch><step>C</step><octave>4</octave></pitch>
                        <duration>4</duration>
                        <type>whole</type>
                        <staff>2</staff>
                      </note>
                    </measure>
                  </part>
                </score-partwise>
                """;
        Score score = new MusicXmlReader().read(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        Attributes attributes = score.parts().get(0).measures().get(0).attributes().orElseThrow();
        assertEquals(2, attributes.staves().orElseThrow());
        assertEquals(2, attributes.clefs().size());
        Note note = (Note) score.parts().get(0).measures().get(0).elements().get(0);
        assertEquals(2, note.staff());
    }

    @Test
    void readsCompressedMxl() {
        Score score;
        try (InputStream in = getClass().getResourceAsStream("/do-re-mi.mxl")) {
            assertNotNull(in, "sample resource must exist");
            score = new MusicXmlReader().read(in);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        assertEquals("Do - Re - Mi", score.workTitle().orElse(null));
        assertEquals(1, score.parts().size());
    }
}
