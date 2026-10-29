package com.sheetmusic4j.pdf;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

import com.sheetmusic4j.core.io.ScoreFile;
import com.sheetmusic4j.core.model.Attributes;
import com.sheetmusic4j.core.model.Beam;
import com.sheetmusic4j.core.model.Chord;
import com.sheetmusic4j.core.model.ClefSign;
import com.sheetmusic4j.core.model.Direction;
import com.sheetmusic4j.core.model.DirectionType;
import com.sheetmusic4j.core.model.DynamicMark;
import com.sheetmusic4j.core.model.Fraction;
import com.sheetmusic4j.core.model.Measure;
import com.sheetmusic4j.core.model.MeasureTiming;
import com.sheetmusic4j.core.model.MusicElement;
import com.sheetmusic4j.core.model.Note;
import com.sheetmusic4j.core.model.NoteType;
import com.sheetmusic4j.core.model.Part;
import com.sheetmusic4j.core.model.Rest;
import com.sheetmusic4j.core.model.Score;
import com.sheetmusic4j.core.model.Step;
import com.sheetmusic4j.core.model.TimeSignature;

/**
 * Reads the engraved PDFs in the {@code fxdemo} test resources: a Finale part (Maestro font) and a LilyPond score
 * (Emmentaler font).
 */
class PdfScoreReaderTest {

    private static Path fixture(String name) {
        Path[] candidates = {
                Path.of("src/test/resources/engraved-pdf", name),
                Path.of("../fxdemo/src/test/resources/engraved-pdf", name),
        };
        for (Path candidate : candidates) {
            if (Files.exists(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private static Score load(String name) {
        Path path = fixture(name);
        assumeTrue(path != null, name + " fixture not available");
        return ScoreFile.load(path);
    }

    /** The notes, chords and rests of a measure, without dynamics and wedges. */
    private static List<MusicElement> timed(Measure measure) {
        return measure.elements().stream().filter(e -> !(e instanceof Direction)).toList();
    }

    /** Share of measures whose timeline adds up to the time signature in force. */
    private static double completeMeasures(Part part) {
        TimeSignature time = TimeSignature.fourFour();
        int complete = 0;
        for (Measure measure : part.measures()) {
            time = measure.attributes().flatMap(Attributes::timeSignature).orElse(time);
            Fraction expected = Fraction.of(time.beats() * 4L, time.beatType());
            if (MeasureTiming.of(measure).duration().equals(expected)) {
                complete++;
            }
        }
        return complete / (double) part.measures().size();
    }

    @Test
    void scoreFileFindsThePdfFormat() {
        Path path = fixture("fantasiestucke.pdf");
        assumeTrue(path != null, "fixture not available");
        assertEquals("Engraved PDF", ScoreFile.formatFor(path).name());
        assertTrue(ScoreFile.formatFor(path).writer().isEmpty());
    }

    @Test
    void readsFinalePart() {
        Score score = load("fantasiestucke.pdf");

        assertEquals(1, score.parts().size());
        Part part = score.parts().get(0);
        assertTrue(part.measures().size() > 200);

        Attributes first = part.measures().get(0).attributes().orElseThrow();
        assertEquals(2, first.keySignature().orElseThrow().fifths(), "two sharps");
        assertEquals(new TimeSignature(4, 4), first.timeSignature().orElseThrow());
        assertEquals(ClefSign.G, first.clef().orElseThrow().sign());
        assertEquals(2, first.clef().orElseThrow().line());

        // bar 1: half rest, quarter rest, D5 quarter; bar 2 starts C#5
        List<?> bar1 = timed(part.measures().get(0));
        assertEquals(3, bar1.size());
        assertEquals(NoteType.HALF, assertInstanceOf(Rest.class, bar1.get(0)).type());
        assertEquals(NoteType.QUARTER, assertInstanceOf(Rest.class, bar1.get(1)).type());
        Note d5 = assertInstanceOf(Note.class, bar1.get(2));
        assertEquals(Step.D, d5.pitch().step());
        assertEquals(5, d5.pitch().octave());
        Note c5 = assertInstanceOf(Note.class, timed(part.measures().get(1)).get(0));
        assertEquals(Step.C, c5.pitch().step());
        assertEquals(1, c5.pitch().alter(), "C sharp from the key signature");

        assertTrue(part.measures().stream().skip(1).anyMatch(Measure::forceSystemBreak),
                "system breaks of the engraving are kept");
        assertTrue(completeMeasures(part) > 0.8, "most bars add up to the time signature");
    }

    @Test
    void readsBeamsTiesSlursAndTuplets() {
        Part part = load("fantasiestucke.pdf").parts().get(0);
        List<Note> notes = part.measures().stream().flatMap(m -> timed(m).stream())
                .flatMap(e -> e instanceof Chord c ? c.notes().stream() : e instanceof Note n ? java.util.stream.Stream.of(n)
                        : java.util.stream.Stream.<Note>empty())
                .toList();

        // bar 2 holds F#5 E5 D5 C#5 as one beamed group of eighths
        List<Note> bar2 = timed(part.measures().get(1)).stream().map(e -> (Note) e).toList();
        assertEquals(Beam.State.BEGIN, bar2.get(2).beams().get(0).state());
        assertEquals(Beam.State.CONTINUE, bar2.get(3).beams().get(0).state());
        assertEquals(Beam.State.END, bar2.get(5).beams().get(0).state());

        assertTrue(notes.stream().anyMatch(Note::tieStart), "ties");
        assertTrue(notes.stream().anyMatch(Note::tieStop), "ties end on a note too");
        assertTrue(notes.stream().anyMatch(n -> !n.slurs().isEmpty()), "slurs");
        assertTrue(notes.stream().anyMatch(n -> n.timeModification().isPresent()), "tuplets");
    }

    @Test
    void readsDynamicsAndHairpins() {
        Part part = load("fantasiestucke.pdf").parts().get(0);
        List<DirectionType> types = part.measures().stream().flatMap(m -> m.elements().stream())
                .filter(Direction.class::isInstance).map(e -> ((Direction) e).type()).toList();

        // bar 1: p under the first note
        DirectionType first = part.measures().get(0).elements().stream().filter(Direction.class::isInstance)
                .map(e -> ((Direction) e).type()).findFirst().orElseThrow();
        assertEquals(new DirectionType.Dynamic(DynamicMark.P), first);

        List<DynamicMark> dynamics = types.stream().filter(DirectionType.Dynamic.class::isInstance)
                .map(t -> ((DirectionType.Dynamic) t).mark()).toList();
        assertTrue(dynamics.containsAll(List.of(DynamicMark.P, DynamicMark.PP, DynamicMark.F, DynamicMark.MF,
                DynamicMark.MP, DynamicMark.SF)), dynamics.toString());

        long crescendos = count(types, DirectionType.WedgeType.CRESCENDO);
        long diminuendos = count(types, DirectionType.WedgeType.DIMINUENDO);
        assertTrue(crescendos > 5 && diminuendos > 5, crescendos + " crescendos, " + diminuendos + " diminuendos");
        assertEquals(crescendos + diminuendos, count(types, DirectionType.WedgeType.STOP), "every wedge is closed");
        assertWedgesPair(types);
    }

    /** Every stop closes the wedge that is open under its number, and no start replaces one that is still open. */
    private static void assertWedgesPair(List<DirectionType> types) {
        java.util.Map<Integer, DirectionType.WedgeType> open = new java.util.HashMap<>();
        for (DirectionType type : types) {
            if (!(type instanceof DirectionType.Wedge wedge)) {
                continue;
            }
            if (wedge.type() == DirectionType.WedgeType.STOP) {
                assertTrue(open.remove(wedge.number()) != null, "a stop without a start for number " + wedge.number());
            } else {
                assertTrue(open.put(wedge.number(), wedge.type()) == null, "a start replaces an open wedge");
            }
        }
        assertTrue(open.isEmpty(), "wedges left open: " + open);
    }

    @Test
    void crescendoFollowedByDiminuendoKeepsBoth() {
        // bar 2 starts with a "<>" figure: a crescendo that closes just as a diminuendo opens
        Part part = load("fantasiestucke.pdf").parts().get(0);
        List<DirectionType.Wedge> wedges = part.measures().get(1).elements().stream().filter(Direction.class::isInstance)
                .map(e -> ((Direction) e).type()).filter(DirectionType.Wedge.class::isInstance)
                .map(DirectionType.Wedge.class::cast).toList();

        assertEquals(List.of(DirectionType.WedgeType.CRESCENDO, DirectionType.WedgeType.DIMINUENDO,
                DirectionType.WedgeType.STOP, DirectionType.WedgeType.STOP),
                wedges.stream().map(DirectionType.Wedge::type).toList());
        assertTrue(wedges.get(0).number() != wedges.get(1).number(), "overlapping wedges need different numbers");
        assertEquals(wedges.get(0).number(), wedges.get(2).number(), "the first stop closes the crescendo");
        assertEquals(wedges.get(1).number(), wedges.get(3).number(), "the second stop closes the diminuendo");
    }

    private static long count(List<DirectionType> types, DirectionType.WedgeType wedge) {
        return types.stream().filter(t -> t instanceof DirectionType.Wedge w && w.type() == wedge).count();
    }

    @Test
    void readsLilyPondScore() {
        Score score = load("mehul-symphonie.pdf");

        assertEquals(11, score.parts().size());
        assertTrue(score.parts().get(0).name().startsWith("Fl"), score.parts().get(0).name());
        int length = score.parts().get(0).measures().size();
        assertTrue(score.parts().stream().allMatch(p -> p.measures().size() >= length - 1
                && p.measures().size() <= length + 1), "all parts have the same number of bars");

        Attributes first = score.parts().get(0).measures().get(0).attributes().orElseThrow();
        assertEquals(-2, first.keySignature().orElseThrow().fifths(), "two flats");

        // flutes I-II hold a G5/B5 whole-note chord in bar 2
        Chord chord = assertInstanceOf(Chord.class, timed(score.parts().get(0).measures().get(1)).get(0));
        assertEquals(2, chord.notes().size());
        assertEquals(NoteType.WHOLE, chord.notes().get(0).type());
        assertEquals(Step.G, chord.notes().get(0).pitch().step());
        assertEquals(Step.B, chord.notes().get(1).pitch().step());

        assertTrue(completeMeasures(score.parts().get(0)) > 0.6);
    }

    @Test
    void rejectsPdfWithoutStaves() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(50, 700);
                content.showText("Just text");
                content.endText();
            }
            document.save(out);
        }
        PdfScoreReader reader = new PdfScoreReader();
        ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
        PdfException e = assertThrows(PdfException.class, () -> reader.read(in));
        assertTrue(e.getMessage().contains("No engraved staves"), e.getMessage());
    }

    @Test
    void rejectsNonPdfContent() {
        PdfScoreReader reader = new PdfScoreReader();
        ByteArrayInputStream in = new ByteArrayInputStream(new byte[]{1, 2, 3});
        assertThrows(PdfException.class, () -> reader.read(in));
    }
}
