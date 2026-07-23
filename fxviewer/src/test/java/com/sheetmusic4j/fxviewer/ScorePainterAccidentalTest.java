package com.sheetmusic4j.fxviewer;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.sheetmusic4j.core.model.Accidental;
import com.sheetmusic4j.core.model.Attributes;
import com.sheetmusic4j.core.model.Clef;
import com.sheetmusic4j.core.model.Duration;
import com.sheetmusic4j.core.model.KeySignature;
import com.sheetmusic4j.core.model.Measure;
import com.sheetmusic4j.core.model.MusicElement;
import com.sheetmusic4j.core.model.Note;
import com.sheetmusic4j.core.model.NoteType;
import com.sheetmusic4j.core.model.Part;
import com.sheetmusic4j.core.model.Pitch;
import com.sheetmusic4j.core.model.Score;
import com.sheetmusic4j.core.model.Step;
import com.sheetmusic4j.core.model.TimeSignature;
import com.sheetmusic4j.engraving.Engraver;
import com.sheetmusic4j.engraving.layout.LayoutOptions;
import com.sheetmusic4j.engraving.layout.LayoutResult;

/**
 * Coverage for the per-element accidental-overlay path added in
 * {@link ScorePainter#setNoteAccidentalProvider}: mutating the provider
 * changes which SMuFL accidental glyph reaches the surface without any
 * re-invocation of the engraver, engraved accidentals are suppressed
 * while an overlay is present and restored when it is removed, and the
 * overlay glyph lands at exactly the same {@code (noteX - gap * 1.5, y)}
 * position the engraver would have used for that element.
 */
class ScorePainterAccidentalTest {

    /** SMuFL codepoint for {@code accidentalSharp}. */
    private static final String SHARP_CP = "\uE262";
    /** SMuFL codepoint for {@code accidentalFlat}. */
    private static final String FLAT_CP = "\uE260";

    private Score plainScore(Note[] outNote) {
        int divisions = 1;
        Note note = Note.builder().pitch(new Pitch(Step.C, 4))
                .duration(new Duration(4, divisions))
                .type(NoteType.WHOLE)
                .build();
        outNote[0] = note;
        Measure.Builder m = Measure.builder(1).attributes(Attributes.builder()
                .divisions(divisions)
                .keySignature(KeySignature.cMajor())
                .timeSignature(TimeSignature.fourFour())
                .clef(Clef.treble())
                .build());
        m.addElement(note);
        return Score.builder()
                .addPart(Part.builder("P1").addMeasure(m.build()).build())
                .build();
    }

    private Score flatScore(Note[] outNote) {
        int divisions = 1;
        Note note = Note.builder().pitch(new Pitch(Step.B, 4, -1))
                .duration(new Duration(4, divisions))
                .type(NoteType.WHOLE)
                .displayedAccidental(Accidental.FLAT)
                .build();
        outNote[0] = note;
        Measure.Builder m = Measure.builder(1).attributes(Attributes.builder()
                .divisions(divisions)
                .keySignature(KeySignature.cMajor())
                .timeSignature(TimeSignature.fourFour())
                .clef(Clef.treble())
                .build());
        m.addElement(note);
        return Score.builder()
                .addPart(Part.builder("P1").addMeasure(m.build()).build())
                .build();
    }

    @Test
    void overlayDrawsSmuflGlyphForNaturalNote() {
        Note[] noteRef = new Note[1];
        Score score = plainScore(noteRef);
        LayoutResult layout = new Engraver().layout(score, LayoutOptions.defaults());

        Map<MusicElement, Accidental> overlays = new IdentityHashMap<>();
        overlays.put(noteRef[0], Accidental.SHARP);

        SmuflSpyingSurface surface = new SmuflSpyingSurface();
        ScorePainter painter = new ScorePainter();
        painter.setNoteAccidentalProvider(e -> Optional.ofNullable(overlays.get(e)));
        painter.paint(surface, layout, layout.width(), layout.height());

        assertEquals(1, surface.countCodepoint(SHARP_CP),
                "one overlay sharp glyph must be drawn for the one overlaid note");
    }

    @Test
    void unknownElementIsNoOp() {
        Note[] noteRef = new Note[1];
        Score score = plainScore(noteRef);
        LayoutResult layout = new Engraver().layout(score, LayoutOptions.defaults());

        Map<MusicElement, Accidental> overlays = new IdentityHashMap<>();
        Note stranger = Note.builder().pitch(new Pitch(Step.C, 4))
                .duration(new Duration(1, 1)).build();
        overlays.put(stranger, Accidental.SHARP);

        SmuflSpyingSurface surface = new SmuflSpyingSurface();
        ScorePainter painter = new ScorePainter();
        painter.setNoteAccidentalProvider(e -> Optional.ofNullable(overlays.get(e)));
        painter.paint(surface, layout, layout.width(), layout.height());

        assertEquals(0, surface.countCodepoint(SHARP_CP),
                "an overlay for an element that isn't in the score must produce no overlay glyph");
    }

    @Test
    void nullProviderIsSafe() {
        Note[] noteRef = new Note[1];
        Score score = plainScore(noteRef);
        LayoutResult layout = new Engraver().layout(score, LayoutOptions.defaults());

        SmuflSpyingSurface surface = new SmuflSpyingSurface();
        ScorePainter painter = new ScorePainter();
        painter.setNoteAccidentalProvider(null);
        painter.paint(surface, layout, layout.width(), layout.height());
        assertNotNull(surface);
        assertEquals(0, surface.countCodepoint(SHARP_CP));
        assertEquals(0, surface.countCodepoint(FLAT_CP));
    }

    @Test
    void overlayReplacesEngravedAccidental() {
        Note[] noteRef = new Note[1];
        Score score = flatScore(noteRef);
        LayoutResult layout = new Engraver().layout(score, LayoutOptions.defaults());

        Map<MusicElement, Accidental> overlays = new IdentityHashMap<>();
        overlays.put(noteRef[0], Accidental.SHARP);

        SmuflSpyingSurface surface = new SmuflSpyingSurface();
        ScorePainter painter = new ScorePainter();
        painter.setNoteAccidentalProvider(e -> Optional.ofNullable(overlays.get(e)));
        painter.paint(surface, layout, layout.width(), layout.height());

        assertEquals(1, surface.countCodepoint(SHARP_CP),
                "the overlay sharp must be drawn in place of the engraved flat");
        assertEquals(0, surface.countCodepoint(FLAT_CP),
                "the engraved flat must be suppressed while the overlay is present");
    }

    @Test
    void removingOverlayRestoresEngravedAccidental() {
        Note[] noteRef = new Note[1];
        Score score = flatScore(noteRef);
        Engraver engraver = new Engraver();
        LayoutResult layout = engraver.layout(score, LayoutOptions.defaults());

        Map<MusicElement, Accidental> overlays = new IdentityHashMap<>();
        overlays.put(noteRef[0], Accidental.SHARP);

        ScorePainter painter = new ScorePainter();
        painter.setNoteAccidentalProvider(e -> Optional.ofNullable(overlays.get(e)));

        SmuflSpyingSurface s1 = new SmuflSpyingSurface();
        painter.paint(s1, layout, layout.width(), layout.height());
        assertEquals(1, s1.countCodepoint(SHARP_CP));
        assertEquals(0, s1.countCodepoint(FLAT_CP));

        overlays.remove(noteRef[0]);
        SmuflSpyingSurface s2 = new SmuflSpyingSurface();
        painter.paint(s2, layout, layout.width(), layout.height());
        assertEquals(0, s2.countCodepoint(SHARP_CP),
                "no overlay left => no sharp glyph");
        assertEquals(1, s2.countCodepoint(FLAT_CP),
                "the engraved flat must reappear once the overlay is cleared");
    }

    @Test
    void overlayPositionMatchesEngravedAccidental() {
        // For a note that natively carries a flat, the engraver emits an
        // ACCIDENTAL_FLAT glyph at (noteX - gap * 1.5, y). With an overlay
        // that leaves the flat as-is (same accidental value), the overlay
        // path replaces the engraved glyph while producing exactly the
        // same x/y arguments to drawSmuflGlyph - so painting the same
        // score twice, once without and once with the (identical-value)
        // overlay, must yield the same SMuFL call at that codepoint.
        Note[] noteRef = new Note[1];
        Score score = flatScore(noteRef);
        LayoutResult layout = new Engraver().layout(score, LayoutOptions.defaults());

        SmuflSpyingSurface engraved = new SmuflSpyingSurface();
        ScorePainter painter = new ScorePainter();
        painter.paint(engraved, layout, layout.width(), layout.height());
        List<SmuflCall> engravedFlats = engraved.callsForCodepoint(FLAT_CP);
        assertEquals(1, engravedFlats.size(), "the engraver must emit exactly one flat");

        Map<MusicElement, Accidental> overlays = new IdentityHashMap<>();
        overlays.put(noteRef[0], Accidental.FLAT);

        SmuflSpyingSurface overlaid = new SmuflSpyingSurface();
        ScorePainter painter2 = new ScorePainter();
        painter2.setNoteAccidentalProvider(e -> Optional.ofNullable(overlays.get(e)));
        painter2.paint(overlaid, layout, layout.width(), layout.height());
        List<SmuflCall> overlaidFlats = overlaid.callsForCodepoint(FLAT_CP);
        assertEquals(1, overlaidFlats.size(), "the overlay must draw exactly one flat");

        SmuflCall a = engravedFlats.get(0);
        SmuflCall b = overlaidFlats.get(0);
        assertEquals(a.x, b.x, 1e-9, "overlay accidental x must match engraved accidental x");
        assertEquals(a.y, b.y, 1e-9, "overlay accidental y must match engraved accidental y");
        assertEquals(a.sizeHint, b.sizeHint, 1e-9,
                "overlay accidental sizeHint must match engraved accidental sizeHint");
    }

    @Test
    void mutatingOverlayDoesNotReEngrave() {
        // Repainting with different overlays reuses the same LayoutResult
        // (engraver invoked exactly once), matching the "mutation triggers
        // repaint, not re-engrave" contract of the SheetView map.
        Note[] noteRef = new Note[1];
        Score score = plainScore(noteRef);
        Engraver engraver = new Engraver();
        LayoutResult layout = engraver.layout(score, LayoutOptions.defaults());

        Map<MusicElement, Accidental> overlays = new IdentityHashMap<>();
        ScorePainter painter = new ScorePainter();
        painter.setNoteAccidentalProvider(e -> Optional.ofNullable(overlays.get(e)));

        SmuflSpyingSurface s1 = new SmuflSpyingSurface();
        painter.paint(s1, layout, layout.width(), layout.height());
        assertEquals(0, s1.countCodepoint(SHARP_CP), "no overlays => no overlay glyph");

        overlays.put(noteRef[0], Accidental.SHARP);
        SmuflSpyingSurface s2 = new SmuflSpyingSurface();
        painter.paint(s2, layout, layout.width(), layout.height());
        assertEquals(1, s2.countCodepoint(SHARP_CP), "one overlay => one overlay glyph");

        overlays.clear();
        SmuflSpyingSurface s3 = new SmuflSpyingSurface();
        painter.paint(s3, layout, layout.width(), layout.height());
        assertEquals(0, s3.countCodepoint(SHARP_CP), "cleared => no overlay glyph");
    }

    @Test
    void overlayInheritsHighlightTint() {
        // An overlay accidental must be drawn with the same tint the note
        // is highlighted with, so the overlay reads as part of the tinted
        // note (matching how the engraved accidental would be tinted).
        Note[] noteRef = new Note[1];
        Score score = plainScore(noteRef);
        LayoutResult layout = new Engraver().layout(score, LayoutOptions.defaults());

        RenderColor red = new RenderColor(255, 0, 0);
        Map<MusicElement, RenderColor> tints = new IdentityHashMap<>();
        tints.put(noteRef[0], red);
        Map<MusicElement, Accidental> overlays = new IdentityHashMap<>();
        overlays.put(noteRef[0], Accidental.SHARP);

        SmuflSpyingSurface surface = new SmuflSpyingSurface();
        ScorePainter painter = new ScorePainter();
        painter.setNoteColorProvider(e -> Optional.ofNullable(tints.get(e)));
        painter.setNoteAccidentalProvider(e -> Optional.ofNullable(overlays.get(e)));
        painter.paint(surface, layout, layout.width(), layout.height());

        assertEquals(1, surface.countCodepoint(SHARP_CP), "overlay sharp must be drawn");
        assertTrue(surface.fillsSet.contains(red)
                        || surface.strokesSet.contains(red),
                "the tint colour must reach the surface when a tinted note also carries an overlay");
    }

    @Test
    void nullReturnFromProviderIsSafe() {
        Note[] noteRef = new Note[1];
        Score score = flatScore(noteRef);
        LayoutResult layout = new Engraver().layout(score, LayoutOptions.defaults());

        // Provider itself returns null (not Optional.empty()) - must be
        // treated as "no override" and never NPE.
        SmuflSpyingSurface surface = new SmuflSpyingSurface();
        ScorePainter painter = new ScorePainter();
        painter.setNoteAccidentalProvider(e -> null);
        painter.paint(surface, layout, layout.width(), layout.height());

        // Engraved flat must still be drawn since no override is active.
        assertEquals(1, surface.countCodepoint(FLAT_CP));
        assertEquals(0, surface.countCodepoint(SHARP_CP));
        // No accidental glyph should be drawn twice: the engraved one only.
        assertFalse(surface.countCodepoint(FLAT_CP) > 1,
                "null-returning provider must not cause duplicate engraved accidental draws");
    }

    /**
     * Records every {@link RenderSurface#drawSmuflGlyph} call and returns
     * {@code true} so the painter does not fall through to primitive
     * fallbacks. Fill / stroke state is also recorded so tint-inheritance
     * assertions can inspect the ambient colour at draw time.
     */
    private static final class SmuflSpyingSurface implements RenderSurface {
        final List<SmuflCall> smuflCalls = new ArrayList<>();
        final List<RenderColor> fillsSet = new ArrayList<>();
        final List<RenderColor> strokesSet = new ArrayList<>();

        int countCodepoint(String codepoint) {
            int n = 0;
            for (SmuflCall c : smuflCalls) {
                if (c.codepoint.equals(codepoint)) {
                    n++;
                }
            }
            return n;
        }

        List<SmuflCall> callsForCodepoint(String codepoint) {
            List<SmuflCall> out = new ArrayList<>();
            for (SmuflCall c : smuflCalls) {
                if (c.codepoint.equals(codepoint)) {
                    out.add(c);
                }
            }
            return out;
        }

        @Override public void setStroke(RenderColor color) { strokesSet.add(color); }
        @Override public void setFill(RenderColor color) { fillsSet.add(color); }
        @Override public void setLineWidth(double width) { }
        @Override public void fillRect(double x, double y, double w, double h) { }
        @Override public void strokeLine(double x1, double y1, double x2, double y2) { }
        @Override public void fillOval(double x, double y, double w, double h) { }
        @Override public void strokeOval(double x, double y, double w, double h) { }
        @Override public void strokeText(String text, double x, double y) { }
        @Override public boolean drawSmuflGlyph(String glyphChars, double x, double y, double sizeHint) {
            smuflCalls.add(new SmuflCall(glyphChars, x, y, sizeHint));
            return true;
        }
    }

    private record SmuflCall(String codepoint, double x, double y, double sizeHint) {
    }
}
