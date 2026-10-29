package com.sheetmusic4j.pdf.recognize;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.sheetmusic4j.core.model.Attributes;
import com.sheetmusic4j.core.model.ClefSign;
import com.sheetmusic4j.core.model.Measure;
import com.sheetmusic4j.core.model.MusicElement;
import com.sheetmusic4j.core.model.Note;
import com.sheetmusic4j.core.model.NoteType;
import com.sheetmusic4j.core.model.Part;
import com.sheetmusic4j.core.model.Step;
import com.sheetmusic4j.pdf.extract.PageContent;
import com.sheetmusic4j.pdf.extract.PageContent.Box;
import com.sheetmusic4j.pdf.extract.PageContent.Glyph;
import com.sheetmusic4j.pdf.extract.PageContent.HLine;
import com.sheetmusic4j.pdf.extract.PageContent.VLine;

/**
 * Recognition on hand-made pages, so each case shows up on its own: a staff of five lines at y = 100..120 (space 5),
 * Maestro glyphs placed by staff step, and just the lines and polygons a case needs.
 */
class SyntheticPageTest {

    private static final String G_CLEF = "&";
    private static final String F_CLEF = "?";
    private static final String SHARP = "#";
    private static final String COMMON_TIME = "c";
    private static final String QUARTER_HEAD = "œ";

    private final List<Glyph> glyphs = new ArrayList<>();
    private final List<HLine> hlines = new ArrayList<>();
    private final List<VLine> vlines = new ArrayList<>();
    private final List<Box> beams = new ArrayList<>();

    private void staff(double top) {
        for (int i = 0; i < 5; i++) {
            hlines.add(new HLine(50, 550, top + 5 * i, 0.5));
        }
    }

    /** A Maestro glyph whose origin sits {@code step} half-spaces above the bottom line of the staff at {@code top}. */
    private void glyph(String text, double x, int step, double width, double top) {
        double y = top + 20 - step * 2.5;
        glyphs.add(new Glyph("Maestro", null, text, x, y, new Box(x, y - 2.6, x + width, y + 2.6)));
    }

    private void glyph(String text, double x, int step, double width) {
        glyph(text, x, step, width, 100);
    }

    private void barline(double x, double top) {
        vlines.add(new VLine(x, top, top + 20, 0.5));
    }

    private List<Part> recognize() {
        ScoreRecognizer recognizer = new ScoreRecognizer();
        recognizer.addPage(new PageContent(0, 600, 800, glyphs, hlines, vlines, List.of(), beams, List.of()));
        return recognizer.parts();
    }

    private static List<Note> notes(Measure measure) {
        return measure.elements().stream().filter(Note.class::isInstance).map(Note.class::cast).toList();
    }

    /** Bar 1 ends with a bass clef, bar 3 opens with a treble clef; the key is two sharps throughout. */
    private List<Measure> clefChangesPage() {
        staff(100);
        glyph(G_CLEF, 60, 2, 8);
        glyph(SHARP, 70, 8, 4);
        glyph(SHARP, 76, 5, 4);
        glyph(COMMON_TIME, 88, 4, 6);
        for (double x : new double[]{100, 130, 160, 190}) {
            glyph(QUARTER_HEAD, x, 4, 6.5);
        }
        glyph(F_CLEF, 235, 6, 8);
        barline(250, 100);
        for (double x : new double[]{280, 310, 340, 370}) {
            glyph(QUARTER_HEAD, x, 4, 6.5);
        }
        barline(450, 100);
        glyph(G_CLEF, 455, 2, 8);
        glyph(QUARTER_HEAD, 480, 4, 6.5);
        return recognize().get(0).measures();
    }

    @Test
    void clefPrintedBeforeTheBarlineChangesTheNextBar() {
        List<Measure> measures = clefChangesPage();

        assertEquals(ClefSign.F, measures.get(1).attributes().orElseThrow().clef().orElseThrow().sign(),
                "the next bar announces the bass clef");
        Note first = notes(measures.get(1)).get(0);
        assertEquals(Step.D, first.pitch().step(), "the staff step that is B in treble clef is D in bass clef");
        assertEquals(3, first.pitch().octave());
        assertEquals(Step.B, notes(measures.get(0)).get(0).pitch().step(), "the bar before the clef is still treble");
    }

    @Test
    void clefAtTheStartOfABarDoesNotResetTheKey() {
        List<Measure> measures = clefChangesPage();

        assertEquals(2, measures.get(0).attributes().orElseThrow().keySignature().orElseThrow().fifths());
        assertTrue(measures.get(1).attributes().flatMap(Attributes::keySignature).isEmpty());
        Measure third = measures.get(2);
        assertEquals(ClefSign.G, third.attributes().orElseThrow().clef().orElseThrow().sign());
        assertTrue(third.attributes().flatMap(Attributes::keySignature).isEmpty(),
                "a clef change opens the bar but not a system, so the key signature stays");
    }

    @Test
    void sixteenthNoteCountsTheBeamAlongItsStem() {
        staff(100);
        glyph(G_CLEF, 60, 2, 8);
        glyph(COMMON_TIME, 88, 4, 6);
        glyph(QUARTER_HEAD, 100, 4, 6.5);
        vlines.add(new VLine(106.2, 92.5, 110, 0.5));
        beams.add(new Box(106, 92.5, 130, 95));      // primary beam at the tip of the stem
        beams.add(new Box(106, 96.1, 130, 98.6));    // secondary beam one level further down the stem

        Note note = notes(recognize().get(0).measures().get(0)).get(0);

        assertEquals(NoteType.SIXTEENTH, note.type());
        assertEquals(2, note.beams().size());
    }

    @Test
    void stavesWithTheSameBarlinesStaySeparateWithoutAStartLine() {
        staff(100);
        staff(200);
        for (double top : new double[]{100, 200}) {
            barline(250, top);
            barline(450, top);
        }
        for (double top : new double[]{100, 200}) {
            glyph(G_CLEF, 60, 2, 8, top);
            glyph(QUARTER_HEAD, 100, 4, 6.5, top);
        }

        List<Part> parts = recognize();

        assertEquals(1, parts.size(), "consecutive rows are one part");
        assertTrue(parts.get(0).measures().size() >= 6, "with the measures of both rows");
    }

    @Test
    void startLineJoinsStavesIntoOneSystem() {
        staff(100);
        staff(200);
        vlines.add(new VLine(50, 100, 220, 0.5));
        for (double top : new double[]{100, 200}) {
            glyph(G_CLEF, 60, 2, 8, top);
            glyph(QUARTER_HEAD, 100, 4, 6.5, top);
        }

        assertEquals(2, recognize().size(), "two staves of one system are two parts");
    }
}
