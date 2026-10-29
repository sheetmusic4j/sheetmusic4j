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
import com.sheetmusic4j.core.model.Chord;
import com.sheetmusic4j.core.model.ClefSign;
import com.sheetmusic4j.core.model.Fraction;
import com.sheetmusic4j.core.model.Measure;
import com.sheetmusic4j.core.model.MeasureTiming;
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
        List<?> bar1 = part.measures().get(0).elements();
        assertEquals(3, bar1.size());
        assertEquals(NoteType.HALF, assertInstanceOf(Rest.class, bar1.get(0)).type());
        assertEquals(NoteType.QUARTER, assertInstanceOf(Rest.class, bar1.get(1)).type());
        Note d5 = assertInstanceOf(Note.class, bar1.get(2));
        assertEquals(Step.D, d5.pitch().step());
        assertEquals(5, d5.pitch().octave());
        Note c5 = assertInstanceOf(Note.class, part.measures().get(1).elements().get(0));
        assertEquals(Step.C, c5.pitch().step());
        assertEquals(1, c5.pitch().alter(), "C sharp from the key signature");

        assertTrue(part.measures().stream().skip(1).anyMatch(Measure::forceSystemBreak),
                "system breaks of the engraving are kept");
        assertTrue(completeMeasures(part) > 0.8, "most bars add up to the time signature");
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
        Chord chord = assertInstanceOf(Chord.class, score.parts().get(0).measures().get(1).elements().get(0));
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
        PdfException e = assertThrows(PdfException.class,
                () -> new PdfScoreReader().read(new ByteArrayInputStream(out.toByteArray())));
        assertTrue(e.getMessage().contains("No engraved staves"), e.getMessage());
    }

    @Test
    void rejectsNonPdfContent() {
        assertThrows(PdfException.class, () -> new PdfScoreReader().read(new ByteArrayInputStream(new byte[]{1, 2, 3})));
    }
}
