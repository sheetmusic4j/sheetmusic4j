package com.sheetmusic4j.engraving;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.sheetmusic4j.core.model.Attributes;
import com.sheetmusic4j.core.model.Clef;
import com.sheetmusic4j.core.model.Duration;
import com.sheetmusic4j.core.model.KeySignature;
import com.sheetmusic4j.core.model.Measure;
import com.sheetmusic4j.core.model.Note;
import com.sheetmusic4j.core.model.Part;
import com.sheetmusic4j.core.model.Pitch;
import com.sheetmusic4j.core.model.Score;
import com.sheetmusic4j.core.model.Step;
import com.sheetmusic4j.core.model.TimeSignature;
import com.sheetmusic4j.engraving.glyph.Glyph;
import com.sheetmusic4j.engraving.glyph.MarkingCategory;
import com.sheetmusic4j.engraving.layout.LayoutMode;
import com.sheetmusic4j.engraving.layout.LayoutOptions;
import com.sheetmusic4j.engraving.layout.LayoutResult;
import com.sheetmusic4j.engraving.layout.StaffLayout;
import com.sheetmusic4j.engraving.placement.GlyphPlacement;

class StripLayoutTest {

    private Score manyMeasureScore(int measureCount) {
        int divisions = 1;
        Part.Builder part = Part.builder("P1").name("Piano");
        for (int m = 1; m <= measureCount; m++) {
            Measure.Builder mb = Measure.builder(m);
            if (m == 1) {
                mb.attributes(Attributes.builder()
                        .divisions(divisions)
                        .keySignature(KeySignature.cMajor())
                        .timeSignature(TimeSignature.fourFour())
                        .clef(Clef.treble())
                        .build());
            }
            for (int i = 0; i < 4; i++) {
                mb.addElement(Note.builder().pitch(new Pitch(Step.C, 4))
                        .duration(new Duration(1, divisions)).build());
            }
            part.addMeasure(mb.build());
        }
        return Score.builder().addPart(part.build()).build();
    }

    @Test
    void stripModeProducesSingleSystem() {
        LayoutOptions options = LayoutOptions.builder().layoutMode(LayoutMode.STRIP).build();
        LayoutResult layout = new Engraver().layout(manyMeasureScore(20), options);
        assertEquals(1, layout.systems().size(),
                "STRIP mode must not break into multiple systems no matter how wide");
    }

    @Test
    void stripModeSystemContainsAllMeasures() {
        LayoutOptions options = LayoutOptions.builder().layoutMode(LayoutMode.STRIP).build();
        LayoutResult layout = new Engraver().layout(manyMeasureScore(12), options);
        StaffLayout staff = layout.staves().get(0);
        assertEquals(12, staff.measures().size());
    }

    @Test
    void stripModeIgnoresSystemWidth() {
        LayoutOptions narrow = LayoutOptions.builder()
                .layoutMode(LayoutMode.STRIP)
                .systemWidth(100)
                .build();
        LayoutResult layout = new Engraver().layout(manyMeasureScore(10), narrow);
        assertTrue(layout.width() > 100,
                "STRIP layout width must exceed the tiny requested systemWidth");
        assertEquals(1, layout.systems().size());
    }

    @Test
    void stripModeSpacesNotesProportionallyToTime() {
        LayoutOptions options = LayoutOptions.builder().layoutMode(LayoutMode.STRIP).build();
        // 3 measures of 4 equal quarter notes each.
        LayoutResult layout = new Engraver().layout(manyMeasureScore(3), options);

        java.util.List<com.sheetmusic4j.engraving.layout.NoteAnchor> notes = new java.util.ArrayList<>();
        for (var a : layout.noteAnchors()) {
            if (a.elementRef() instanceof Note) {
                notes.add(a);
            }
        }
        notes.sort(java.util.Comparator.comparingDouble(
                com.sheetmusic4j.engraving.layout.NoteAnchor::onsetQuarters));
        assertEquals(12, notes.size(), "3 measures x 4 quarters");

        // Every consecutive equal-duration step must span the same x distance,
        // INCLUDING across barlines (indices 3->4, 7->8) - that is what makes a
        // constant-tempo cursor move at constant speed with no pre-barline jump.
        double firstGap = notes.get(1).x() - notes.get(0).x();
        assertTrue(firstGap > 0, "notes must advance left-to-right");
        for (int i = 1; i < notes.size(); i++) {
            double gap = notes.get(i).x() - notes.get(i - 1).x();
            assertEquals(firstGap, gap, firstGap * 0.02,
                    "equal quarter notes must be equally spaced at step " + i
                            + " (including across the barline)");
        }
    }

    @Test
    void pageModeStillBreaksIntoRows() {
        LayoutOptions narrow = LayoutOptions.builder().systemWidth(400).build();
        LayoutResult layout = new Engraver().layout(manyMeasureScore(10), narrow);
        assertTrue(layout.systems().size() > 1,
                "PAGE mode with narrow width should break into multiple rows");
    }

    @Test
    void showFlagsSuppressClefKeyAndTime() {
        LayoutOptions options = LayoutOptions.builder()
                .showClef(false)
                .showKeySignature(false)
                .showTimeSignature(false)
                .build();
        LayoutResult layout = new Engraver().layout(dMajorSample(), options);
        StaffLayout staff = layout.staves().get(0);
        boolean hasClef = staff.glyphs().stream().anyMatch(g -> g.glyph() == Glyph.CLEF_G);
        assertFalse(hasClef, "showClef=false must not emit a clef glyph");
        boolean hasSharp = staff.glyphs().stream().anyMatch(g -> g.glyph() == Glyph.ACCIDENTAL_SHARP);
        assertFalse(hasSharp, "showKeySignature=false must not emit key-signature sharps");
        boolean hasDigit = staff.glyphs().stream().anyMatch(g -> g.glyph().timeDigitChar() != null);
        assertFalse(hasDigit, "showTimeSignature=false must not emit time-signature digits");
    }

    @Test
    void showTitleTextsFalseSuppressesTitles() {
        Score score = Score.builder()
                .workTitle("Title")
                .movementTitle("Movement")
                .addPart(Part.builder("P1")
                        .addMeasure(Measure.builder(1).attributes(Attributes.builder()
                                .divisions(1)
                                .clef(Clef.treble())
                                .timeSignature(TimeSignature.fourFour())
                                .build()).build())
                        .build())
                .build();
        LayoutOptions options = LayoutOptions.builder().showTitleTexts(false).build();
        LayoutResult layout = new Engraver().layout(score, options);
        boolean anyTitle = layout.texts().stream()
                .anyMatch(t -> t.category() == MarkingCategory.TITLE
                        || t.category() == MarkingCategory.SUBTITLE);
        assertFalse(anyTitle);
    }

    private Score dMajorSample() {
        int divisions = 1;
        Measure.Builder m = Measure.builder(1).attributes(Attributes.builder()
                .divisions(divisions)
                .keySignature(new KeySignature(2))
                .timeSignature(TimeSignature.fourFour())
                .clef(Clef.treble())
                .build());
        m.addElement(Note.builder().pitch(new Pitch(Step.D, 4))
                .duration(new Duration(4, divisions)).build());
        return Score.builder().addPart(Part.builder("P1").addMeasure(m.build()).build()).build();
    }

    @Test
    void glyphsCarryElementRefForNotes() {
        LayoutResult layout = new Engraver().layout(dMajorSample(),
                LayoutOptions.defaults());
        StaffLayout staff = layout.staves().get(0);
        // The notehead placement should carry a non-null element ref.
        boolean hasNoteheadWithRef = false;
        for (GlyphPlacement g : staff.glyphs()) {
            if (g.glyph() == Glyph.NOTEHEAD_WHOLE && g.elementRef() != null) {
                hasNoteheadWithRef = true;
                break;
            }
        }
        assertTrue(hasNoteheadWithRef,
                "notehead placements must carry the source Note as elementRef");
    }
}
