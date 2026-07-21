package com.sheetmusic4j.core.musicxml;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.Test;

import com.sheetmusic4j.core.model.Attributes;
import com.sheetmusic4j.core.model.Clef;
import com.sheetmusic4j.core.model.Creator;
import com.sheetmusic4j.core.model.Direction;
import com.sheetmusic4j.core.model.DirectionType;
import com.sheetmusic4j.core.model.Duration;
import com.sheetmusic4j.core.model.DynamicMark;
import com.sheetmusic4j.core.model.Lyric;
import com.sheetmusic4j.core.model.Measure;
import com.sheetmusic4j.core.model.MusicElement;
import com.sheetmusic4j.core.model.Note;
import com.sheetmusic4j.core.model.NoteType;
import com.sheetmusic4j.core.model.Part;
import com.sheetmusic4j.core.model.Pitch;
import com.sheetmusic4j.core.model.Placement;
import com.sheetmusic4j.core.model.Score;
import com.sheetmusic4j.core.model.Step;
import com.sheetmusic4j.core.model.Syllabic;

class MusicXmlWriterTest {

    private Score readSample() {
        try (InputStream in = getClass().getResourceAsStream("/c-major-scale.musicxml")) {
            assertNotNull(in);
            return new MusicXmlReader().read(in);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void roundTripPreservesStructure() {
        Score original = readSample();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new MusicXmlWriter().write(original, out);

        Score reparsed = new MusicXmlReader().read(new ByteArrayInputStream(out.toByteArray()));

        assertEquals(original.workTitle(), reparsed.workTitle());
        assertEquals(original.parts().size(), reparsed.parts().size());

        Part originalPart = original.parts().get(0);
        Part reparsedPart = reparsed.parts().get(0);
        assertEquals(originalPart.measures().size(), reparsedPart.measures().size());

        for (int m = 0; m < originalPart.measures().size(); m++) {
            var origElems = originalPart.measures().get(m).elements();
            var reElems = reparsedPart.measures().get(m).elements();
            assertEquals(origElems.size(), reElems.size(), "measure " + m + " element count");
            for (int i = 0; i < origElems.size(); i++) {
                assertSameElement(origElems.get(i), reElems.get(i));
            }
        }
    }

    @Test
    void roundTripPreservesCreators() {
        Score original = Score.builder()
                .workTitle("Test Piece")
                .addCreator(new Creator("composer", "Alice Composer"))
                .addCreator(new Creator("lyricist", "Bob Lyricist"))
                .addPart(Part.builder("P1").name("Piano").build())
                .build();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new MusicXmlWriter().write(original, out);

        Score reparsed = new MusicXmlReader().read(new ByteArrayInputStream(out.toByteArray()));
        assertEquals(original.creators(), reparsed.creators());
    }

    @Test
    void roundTripPreservesLyrics() {
        int divisions = 1;
        Note note = Note.builder()
                .pitch(new Pitch(Step.C, 4))
                .duration(new Duration(1, divisions))
                .type(NoteType.QUARTER)
                .addLyric(new Lyric("La", Syllabic.SINGLE, 1))
                .build();
        Measure measure = Measure.builder(1)
                .attributes(Attributes.builder()
                        .divisions(divisions)
                        .clef(Clef.treble())
                        .build())
                .addElement(note)
                .build();
        Score original = Score.builder()
                .addPart(Part.builder("P1").name("V").addMeasure(measure).build())
                .build();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new MusicXmlWriter().write(original, out);
        Score reparsed = new MusicXmlReader().read(new ByteArrayInputStream(out.toByteArray()));

        Note reparsedNote = (Note) reparsed.parts().get(0).measures().get(0).elements().get(0);
        assertEquals(1, reparsedNote.lyrics().size());
        assertEquals(new Lyric("La", Syllabic.SINGLE, 1), reparsedNote.lyrics().get(0));
    }

    @Test
    void roundTripPreservesMultipleLyricVerses() {
        int divisions = 1;
        Note note = Note.builder()
                .pitch(new Pitch(Step.D, 4))
                .duration(new Duration(1, divisions))
                .type(NoteType.QUARTER)
                .addLyric(new Lyric("Ma", Syllabic.BEGIN, 1))
                .addLyric(new Lyric("Le", Syllabic.SINGLE, 2))
                .build();
        Measure measure = Measure.builder(1)
                .attributes(Attributes.builder().divisions(divisions).clef(Clef.treble()).build())
                .addElement(note)
                .build();
        Score original = Score.builder()
                .addPart(Part.builder("P1").name("V").addMeasure(measure).build())
                .build();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new MusicXmlWriter().write(original, out);
        Score reparsed = new MusicXmlReader().read(new ByteArrayInputStream(out.toByteArray()));

        Note reparsedNote = (Note) reparsed.parts().get(0).measures().get(0).elements().get(0);
        assertEquals(2, reparsedNote.lyrics().size());
        assertEquals(new Lyric("Ma", Syllabic.BEGIN, 1), reparsedNote.lyrics().get(0));
        assertEquals(new Lyric("Le", Syllabic.SINGLE, 2), reparsedNote.lyrics().get(1));
    }

    @Test
    void roundTripPreservesDirections() {
        int divisions = 1;
        Direction words = new Direction(
                new DirectionType.Words("Andantino", false, true), Placement.ABOVE);
        Direction metronome = new Direction(
                new DirectionType.Metronome(NoteType.QUARTER, false, 60), Placement.ABOVE);
        Direction dynamic = new Direction(
                new DirectionType.Dynamic(DynamicMark.MF), Placement.BELOW);
        Note note = Note.builder()
                .pitch(new Pitch(Step.C, 4))
                .duration(new Duration(1, divisions))
                .type(NoteType.QUARTER)
                .build();
        Measure measure = Measure.builder(1)
                .attributes(Attributes.builder().divisions(divisions).clef(Clef.treble()).build())
                .addElement(words)
                .addElement(metronome)
                .addElement(dynamic)
                .addElement(note)
                .build();
        Score original = Score.builder()
                .addPart(Part.builder("P1").name("V").addMeasure(measure).build())
                .build();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new MusicXmlWriter().write(original, out);
        Score reparsed = new MusicXmlReader().read(new ByteArrayInputStream(out.toByteArray()));

        var elements = reparsed.parts().get(0).measures().get(0).elements();
        assertEquals(4, elements.size());
        Direction reWords = (Direction) elements.get(0);
        assertEquals(Placement.ABOVE, reWords.placement());
        assertEquals(new DirectionType.Words("Andantino", false, true), reWords.type());

        Direction reMetronome = (Direction) elements.get(1);
        assertEquals(new DirectionType.Metronome(NoteType.QUARTER, false, 60), reMetronome.type());

        Direction reDynamic = (Direction) elements.get(2);
        assertEquals(Placement.BELOW, reDynamic.placement());
        assertEquals(new DirectionType.Dynamic(DynamicMark.MF), reDynamic.type());
    }

    @Test
    void roundTripPreservesRehearsalDirection() {
        int divisions = 1;
        Direction rehearsal = new Direction(
                new DirectionType.Rehearsal("A"), Placement.ABOVE);
        Note note = Note.builder()
                .pitch(new Pitch(Step.C, 4))
                .duration(new Duration(1, divisions))
                .type(NoteType.QUARTER)
                .build();
        Measure measure = Measure.builder(1)
                .attributes(Attributes.builder().divisions(divisions).clef(Clef.treble()).build())
                .addElement(rehearsal)
                .addElement(note)
                .build();
        Score original = Score.builder()
                .addPart(Part.builder("P1").name("V").addMeasure(measure).build())
                .build();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new MusicXmlWriter().write(original, out);
        Score reparsed = new MusicXmlReader().read(new ByteArrayInputStream(out.toByteArray()));

        var elements = reparsed.parts().get(0).measures().get(0).elements();
        assertEquals(2, elements.size());
        Direction reRehearsal = (Direction) elements.get(0);
        assertEquals(Placement.ABOVE, reRehearsal.placement());
        assertEquals(new DirectionType.Rehearsal("A"), reRehearsal.type());
    }

    private void assertSameElement(MusicElement a, MusicElement b) {
        assertEquals(a.getClass(), b.getClass());
        assertEquals(a.duration().value(), b.duration().value());
        if (a instanceof Note na && b instanceof Note nb) {
            assertEquals(na.pitch(), nb.pitch());
            assertEquals(na.type(), nb.type());
            assertEquals(na.lyrics(), nb.lyrics());
        }
        if (a instanceof Direction da && b instanceof Direction db) {
            assertEquals(da.placement(), db.placement());
            assertEquals(da.type(), db.type());
        }
    }
    }
