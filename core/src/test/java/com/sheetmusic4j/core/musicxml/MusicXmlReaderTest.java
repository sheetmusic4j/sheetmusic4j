package com.sheetmusic4j.core.musicxml;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.sheetmusic4j.core.model.Accidental;
import com.sheetmusic4j.core.model.Attributes;
import com.sheetmusic4j.core.model.Beam;
import com.sheetmusic4j.core.model.Creator;
import com.sheetmusic4j.core.model.Direction;
import com.sheetmusic4j.core.model.DirectionType;
import com.sheetmusic4j.core.model.DynamicMark;
import com.sheetmusic4j.core.model.Lyric;
import com.sheetmusic4j.core.model.MusicElement;
import com.sheetmusic4j.core.model.Note;
import com.sheetmusic4j.core.model.NoteType;
import com.sheetmusic4j.core.model.Part;
import com.sheetmusic4j.core.model.Placement;
import com.sheetmusic4j.core.model.Score;
import com.sheetmusic4j.core.model.Step;
import com.sheetmusic4j.core.model.Syllabic;

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
    void readsComposerAndLyricistFromIdentification() {
        String xml = """
                <?xml version=\"1.0\" encoding=\"UTF-8\"?>
                <score-partwise version=\"4.0\">
                  <identification>
                    <creator type=\"composer\">Ludwig van Beethoven</creator>
                    <creator type=\"lyricist\">Friedrich Schiller</creator>
                  </identification>
                  <part-list><score-part id=\"P1\"><part-name>Piano</part-name></score-part></part-list>
                  <part id=\"P1\"><measure number=\"1\"/></part>
                </score-partwise>
                """;
        Score score = new MusicXmlReader().read(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        assertEquals(2, score.creators().size());
        Creator composer = score.creators().get(0);
        assertEquals("composer", composer.role());
        assertEquals("Ludwig van Beethoven", composer.name());
        Creator lyricist = score.creators().get(1);
        assertEquals("lyricist", lyricist.role());
        assertEquals("Friedrich Schiller", lyricist.name());
    }

    @Test
    void readsCreatorFromCreditWhenIdentificationAbsent() {
        String xml = """
                <?xml version=\"1.0\" encoding=\"UTF-8\"?>
                <score-partwise version=\"4.0\">
                  <credit page=\"1\">
                    <credit-type>composer</credit-type>
                    <credit-words default-x=\"600\" default-y=\"1500\">Frederic Chopin</credit-words>
                  </credit>
                  <part-list><score-part id=\"P1\"><part-name>Piano</part-name></score-part></part-list>
                  <part id=\"P1\"><measure number=\"1\"/></part>
                </score-partwise>
                """;
        Score score = new MusicXmlReader().read(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        assertEquals(1, score.creators().size());
        assertEquals("composer", score.creators().get(0).role());
        assertEquals("Frederic Chopin", score.creators().get(0).name());
    }

    @Test
    void identificationTakesPrecedenceOverCredit() {
        String xml = """
                <?xml version=\"1.0\" encoding=\"UTF-8\"?>
                <score-partwise version=\"4.0\">
                  <identification>
                    <creator type=\"composer\">Correct Name</creator>
                  </identification>
                  <credit page=\"1\">
                    <credit-type>composer</credit-type>
                    <credit-words>Wrong Name</credit-words>
                  </credit>
                  <part-list><score-part id=\"P1\"><part-name>Piano</part-name></score-part></part-list>
                  <part id=\"P1\"><measure number=\"1\"/></part>
                </score-partwise>
                """;
        Score score = new MusicXmlReader().read(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        assertEquals(1, score.creators().size());
        assertEquals("composer", score.creators().get(0).role());
        assertEquals("Correct Name", score.creators().get(0).name());
    }

    @Test
    void ignoresUnknownCreditTypes() {
        String xml = """
                <?xml version=\"1.0\" encoding=\"UTF-8\"?>
                <score-partwise version=\"4.0\">
                  <credit page=\"1\">
                    <credit-type>page number</credit-type>
                    <credit-words>1</credit-words>
                  </credit>
                  <part-list><score-part id=\"P1\"><part-name>Piano</part-name></score-part></part-list>
                  <part id=\"P1\"><measure number=\"1\"/></part>
                </score-partwise>
                """;
        Score score = new MusicXmlReader().read(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        assertTrue(score.creators().isEmpty());
    }

    @Test
    void readsSingleVerseLyric() {
        String xml = noteXmlWithLyric("""
                <lyric number="1">
                  <syllabic>single</syllabic>
                  <text>La</text>
                </lyric>
                """);
        Note note = (Note) new MusicXmlReader()
                .read(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)))
                .parts().get(0).measures().get(0).elements().get(0);
        assertEquals(1, note.lyrics().size());
        Lyric lyric = note.lyrics().get(0);
        assertEquals("La", lyric.text());
        assertEquals(Syllabic.SINGLE, lyric.syllabic());
        assertEquals(1, lyric.verse());
    }

    @Test
    void readsSyllabicHyphenation() {
        String xml = """
                <?xml version=\"1.0\" encoding=\"UTF-8\"?>
                <score-partwise version=\"4.0\">
                  <part-list><score-part id=\"P1\"><part-name>V</part-name></score-part></part-list>
                  <part id=\"P1\">
                    <measure number=\"1\">
                      <attributes>
                        <divisions>1</divisions>
                        <clef><sign>G</sign><line>2</line></clef>
                      </attributes>
                      <note>
                        <pitch><step>C</step><octave>4</octave></pitch>
                        <duration>1</duration>
                        <type>quarter</type>
                        <lyric number=\"1\"><syllabic>begin</syllabic><text>Ma</text></lyric>
                      </note>
                      <note>
                        <pitch><step>D</step><octave>4</octave></pitch>
                        <duration>1</duration>
                        <type>quarter</type>
                        <lyric number=\"1\"><syllabic>middle</syllabic><text>gni</text></lyric>
                      </note>
                      <note>
                        <pitch><step>E</step><octave>4</octave></pitch>
                        <duration>1</duration>
                        <type>quarter</type>
                        <lyric number=\"1\"><syllabic>end</syllabic><text>fi</text></lyric>
                      </note>
                    </measure>
                  </part>
                </score-partwise>
                """;
        Score score = new MusicXmlReader().read(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        var elements = score.parts().get(0).measures().get(0).elements();
        assertEquals(Syllabic.BEGIN, ((Note) elements.get(0)).lyrics().get(0).syllabic());
        assertEquals(Syllabic.MIDDLE, ((Note) elements.get(1)).lyrics().get(0).syllabic());
        assertEquals(Syllabic.END, ((Note) elements.get(2)).lyrics().get(0).syllabic());
    }

    @Test
    void defaultsVerseAndSyllabicWhenMissing() {
        String xml = noteXmlWithLyric("<lyric><text>Hi</text></lyric>");
        Note note = (Note) new MusicXmlReader()
                .read(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)))
                .parts().get(0).measures().get(0).elements().get(0);
        assertEquals(1, note.lyrics().size());
        Lyric lyric = note.lyrics().get(0);
        assertEquals("Hi", lyric.text());
        assertEquals(Syllabic.SINGLE, lyric.syllabic());
        assertEquals(1, lyric.verse());
    }

    @Test
    void dropsLyricWithBlankText() {
        String xml = noteXmlWithLyric("<lyric number=\"1\"><syllabic>single</syllabic><text></text></lyric>");
        Note note = (Note) new MusicXmlReader()
                .read(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)))
                .parts().get(0).measures().get(0).elements().get(0);
        assertTrue(note.lyrics().isEmpty());
    }

    @Test
    void readsMultipleVerses() {
        String xml = noteXmlWithLyric("""
                <lyric number="1"><text>La</text></lyric>
                <lyric number="2"><text>Le</text></lyric>
                """);
        Note note = (Note) new MusicXmlReader()
                .read(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)))
                .parts().get(0).measures().get(0).elements().get(0);
        assertEquals(2, note.lyrics().size());
        assertEquals(1, note.lyrics().get(0).verse());
        assertEquals(2, note.lyrics().get(1).verse());
    }

    @Test
    void concatenatesElidedTextChunks() {
        String xml = noteXmlWithLyric(
                "<lyric number=\"1\"><text>a</text><elision> </elision><text>e</text></lyric>");
        Note note = (Note) new MusicXmlReader()
                .read(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)))
                .parts().get(0).measures().get(0).elements().get(0);
        assertEquals("a e", note.lyrics().get(0).text());
    }

    @Test
    void readsLyricsFromBinchoisSample() {
        java.nio.file.Path p = findBinchoisSample();
        if (p == null) {
            return; // resource not on the core test classpath — skip
        }
        Score score = new MusicXmlReader().read(p);
        var elements = score.parts().get(0).measures().get(0).elements();
        String firstLyric = null;
        for (MusicElement el : elements) {
            if (el instanceof Note note && !note.lyrics().isEmpty()) {
                firstLyric = note.lyrics().get(0).text();
                break;
            }
        }
        assertEquals("Ma", firstLyric);
    }

    private static Path findBinchoisSample() {
        return findSample("Binchois.musicxml");
    }

    private static Path findActorPreludeSample() {
        return findSample("ActorPreludeSample.musicxml");
    }

    private static Path findSample(String fileName) {
        String[] candidates = {
            "../fxdemo/src/test/resources/xmlsamples/" + fileName,
            "fxdemo/src/test/resources/xmlsamples/" + fileName,
            "sheetmusic4j/fxdemo/src/test/resources/xmlsamples/" + fileName
        };
        for (String candidate : candidates) {
            java.nio.file.Path p = java.nio.file.Paths.get(candidate).toAbsolutePath().normalize();
            if (java.nio.file.Files.exists(p)) {
                return p;
            }
        }
        return null;
    }

    @Test
    void readsRehearsalFromActorPreludeSample() {
        java.nio.file.Path p = findActorPreludeSample();
        if (p == null) {
            return; // resource not on the core test classpath — skip
        }
        Score score = new MusicXmlReader().read(p);
        boolean found = false;
        outer:
        for (Part part : score.parts()) {
            for (var measure : part.measures()) {
                for (MusicElement el : measure.elements()) {
                    if (el instanceof Direction dir
                            && dir.type() instanceof DirectionType.Rehearsal rehearsal
                            && "A".equals(rehearsal.label())) {
                        found = true;
                        break outer;
                    }
                }
            }
        }
        assertTrue(found, "expected at least one Rehearsal(\"A\") in ActorPreludeSample");
    }

    private static String noteXmlWithLyric(String lyricXml) {
        return """
                <?xml version=\"1.0\" encoding=\"UTF-8\"?>
                <score-partwise version=\"4.0\">
                  <part-list><score-part id=\"P1\"><part-name>V</part-name></score-part></part-list>
                  <part id=\"P1\">
                    <measure number=\"1\">
                      <attributes>
                        <divisions>1</divisions>
                        <clef><sign>G</sign><line>2</line></clef>
                      </attributes>
                      <note>
                        <pitch><step>C</step><octave>4</octave></pitch>
                        <duration>1</duration>
                        <type>quarter</type>
                        """ + lyricXml + """
                      </note>
                    </measure>
                  </part>
                </score-partwise>
                """;
    }

    @Test
    void readsWordsDirection() {
        String xml = directionXml("""
                <direction placement="above">
                  <direction-type>
                    <words font-weight="bold">Andantino</words>
                  </direction-type>
                </direction>
                """);
        Score score = new MusicXmlReader().read(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        var elements = score.parts().get(0).measures().get(0).elements();
        Direction direction = (Direction) elements.get(0);
        assertEquals(Placement.ABOVE, direction.placement());
        DirectionType.Words words = (DirectionType.Words) direction.type();
        assertEquals("Andantino", words.text());
        assertTrue(words.bold());
        assertTrue(!words.italic());
    }

    @Test
    void readsItalicWordsDirection() {
        String xml = directionXml("""
                <direction>
                  <direction-type>
                    <words font-style="italic">dolce</words>
                  </direction-type>
                </direction>
                """);
        Score score = new MusicXmlReader().read(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        Direction direction = (Direction) score.parts().get(0).measures().get(0).elements().get(0);
        assertEquals(Placement.DEFAULT, direction.placement());
        DirectionType.Words words = (DirectionType.Words) direction.type();
        assertEquals("dolce", words.text());
        assertTrue(words.italic());
        assertTrue(!words.bold());
    }

    @Test
    void readsMetronomeDirection() {
        String xml = directionXml("""
                <direction placement="above">
                  <direction-type>
                    <metronome>
                      <beat-unit>quarter</beat-unit>
                      <per-minute>60</per-minute>
                    </metronome>
                  </direction-type>
                </direction>
                """);
        Score score = new MusicXmlReader().read(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        Direction direction = (Direction) score.parts().get(0).measures().get(0).elements().get(0);
        DirectionType.Metronome metronome = (DirectionType.Metronome) direction.type();
        assertEquals(NoteType.QUARTER, metronome.beatUnit());
        assertTrue(!metronome.dotted());
        assertEquals(60, metronome.perMinute());
    }

    @Test
    void readsDottedMetronomeDirection() {
        String xml = directionXml("""
                <direction>
                  <direction-type>
                    <metronome>
                      <beat-unit>eighth</beat-unit>
                      <beat-unit-dot/>
                      <per-minute>90</per-minute>
                    </metronome>
                  </direction-type>
                </direction>
                """);
        Score score = new MusicXmlReader().read(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        Direction direction = (Direction) score.parts().get(0).measures().get(0).elements().get(0);
        DirectionType.Metronome metronome = (DirectionType.Metronome) direction.type();
        assertEquals(NoteType.EIGHTH, metronome.beatUnit());
        assertTrue(metronome.dotted());
        assertEquals(90, metronome.perMinute());
    }

    @Test
    void readsDynamicsDirection() {
        String xml = directionXml("""
                <direction placement="below">
                  <direction-type>
                    <dynamics><p/></dynamics>
                  </direction-type>
                </direction>
                """);
        Score score = new MusicXmlReader().read(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        Direction direction = (Direction) score.parts().get(0).measures().get(0).elements().get(0);
        assertEquals(Placement.BELOW, direction.placement());
        DirectionType.Dynamic dynamic = (DirectionType.Dynamic) direction.type();
        assertEquals(DynamicMark.P, dynamic.mark());
    }

    @Test
    void readsRehearsalDirection() {
        String xml = directionXml("""
                <direction placement="above">
                  <direction-type>
                    <rehearsal>A</rehearsal>
                  </direction-type>
                </direction>
                """);
        Score score = new MusicXmlReader().read(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        Direction direction = (Direction) score.parts().get(0).measures().get(0).elements().get(0);
        assertEquals(Placement.ABOVE, direction.placement());
        DirectionType.Rehearsal rehearsal = (DirectionType.Rehearsal) direction.type();
        assertEquals("A", rehearsal.label());
    }

    @Test
    void dropsRehearsalDirectionWithBlankLabel() {
        String xml = directionXml("""
                <direction placement="above">
                  <direction-type>
                    <rehearsal>   </rehearsal>
                  </direction-type>
                </direction>
                """);
        Score score = new MusicXmlReader().read(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        var elements = score.parts().get(0).measures().get(0).elements();
        // Only the note remains — blank rehearsal drops the direction entirely.
        assertEquals(1, elements.size());
        assertInstanceOf(Note.class, elements.get(0));
    }

    @Test
    void unknownDirectionTypeIgnored() {
        String xml = directionXml("""
                <direction>
                  <direction-type>
                    <wedge type="crescendo"/>
                  </direction-type>
                </direction>
                """);
        Score score = new MusicXmlReader().read(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        var elements = score.parts().get(0).measures().get(0).elements();
        // Only the note remains — unrecognised direction is silently dropped.
        assertEquals(1, elements.size());
        assertInstanceOf(Note.class, elements.get(0));
    }

    private static String directionXml(String directionBlock) {
        return """
                <?xml version=\"1.0\" encoding=\"UTF-8\"?>
                <score-partwise version=\"4.0\">
                  <part-list><score-part id=\"P1\"><part-name>V</part-name></score-part></part-list>
                  <part id=\"P1\">
                    <measure number=\"1\">
                      <attributes>
                        <divisions>1</divisions>
                        <clef><sign>G</sign><line>2</line></clef>
                      </attributes>
                        """ + directionBlock + """
                      <note>
                        <pitch><step>C</step><octave>4</octave></pitch>
                        <duration>1</duration>
                        <type>quarter</type>
                      </note>
                    </measure>
                  </part>
                </score-partwise>
                """;
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
