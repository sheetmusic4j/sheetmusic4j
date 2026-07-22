package com.sheetmusic4j.engraving;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.sheetmusic4j.core.model.Accidental;
import com.sheetmusic4j.core.model.Attributes;
import com.sheetmusic4j.core.model.Clef;
import com.sheetmusic4j.core.model.Duration;
import com.sheetmusic4j.core.model.KeySignature;
import com.sheetmusic4j.core.model.Measure;
import com.sheetmusic4j.core.model.Note;
import com.sheetmusic4j.core.model.NoteType;
import com.sheetmusic4j.core.model.Part;
import com.sheetmusic4j.core.model.Pitch;
import com.sheetmusic4j.core.model.Score;
import com.sheetmusic4j.core.model.Step;
import com.sheetmusic4j.core.model.TimeSignature;
import com.sheetmusic4j.engraving.glyph.Glyph;
import com.sheetmusic4j.engraving.layout.LayoutOptions;
import com.sheetmusic4j.engraving.layout.LayoutResult;
import com.sheetmusic4j.engraving.layout.StaffLayout;
import com.sheetmusic4j.engraving.placement.GlyphPlacement;

/**
 * Verifies that a note carrying a displayed accidental reserves horizontal
 * room so the accidental glyph does not visually collide with the previous
 * notehead.
 */
class AccidentalSpacingTest {

    @Test
    void sharpAccidentalDoesNotOverlapPreviousNote() {
        int divisions = 1;
        Measure.Builder m = Measure.builder(1).attributes(Attributes.builder()
                .divisions(divisions)
                .keySignature(KeySignature.cMajor())
                .timeSignature(TimeSignature.fourFour())
                .clef(Clef.treble())
                .build());
        // ♩  ♩#  ♩  ♩  in C major, so the # on note 2 is a displayed accidental.
        m.addElement(Note.builder()
                .pitch(new Pitch(Step.C, 5))
                .duration(new Duration(1, divisions))
                .type(NoteType.QUARTER).build());
        m.addElement(Note.builder()
                .pitch(new Pitch(Step.D, 5, 1))
                .displayedAccidental(Accidental.SHARP)
                .duration(new Duration(1, divisions))
                .type(NoteType.QUARTER).build());
        m.addElement(Note.builder()
                .pitch(new Pitch(Step.E, 5))
                .duration(new Duration(1, divisions))
                .type(NoteType.QUARTER).build());
        m.addElement(Note.builder()
                .pitch(new Pitch(Step.F, 5))
                .duration(new Duration(1, divisions))
                .type(NoteType.QUARTER).build());

        Score score = Score.builder()
                .addPart(Part.builder("P1").addMeasure(m.build()).build())
                .build();

        LayoutOptions options = LayoutOptions.defaults();
        LayoutResult layout = new Engraver().layout(score, options);
        StaffLayout staff = layout.staves().get(0);

        List<Double> noteXs = new ArrayList<>();
        List<Double> sharpXs = new ArrayList<>();
        for (GlyphPlacement g : staff.glyphs()) {
            if (g.glyph() == Glyph.NOTEHEAD_BLACK) {
                noteXs.add(g.x());
            } else if (g.glyph() == Glyph.ACCIDENTAL_SHARP) {
                sharpXs.add(g.x());
            }
        }

        assertTrue(noteXs.size() == 4, "expected 4 quarter noteheads, got " + noteXs.size());
        assertTrue(sharpXs.size() == 1, "expected exactly one displayed sharp, got " + sharpXs.size());

        double firstNoteX = noteXs.get(0);
        double sharpX = sharpXs.get(0);
        // Notehead half-width for a quarter note is ~0.59 * gap; the accidental
        // must sit to the right of the previous notehead's right edge to avoid
        // visual overlap.
        double gap = options.staffLineGap();
        double firstNoteRightEdge = firstNoteX + 0.59 * gap;
        assertTrue(sharpX > firstNoteRightEdge,
                "sharp glyph x (" + sharpX + ") must be right of previous notehead right edge ("
                        + firstNoteRightEdge + ")");
    }
}
