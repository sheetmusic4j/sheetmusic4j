package com.sheet4j.engraving;

import com.sheet4j.core.model.Attributes;
import com.sheet4j.core.model.Chord;
import com.sheet4j.core.model.Clef;
import com.sheet4j.core.model.ClefSign;
import com.sheet4j.core.model.Measure;
import com.sheet4j.core.model.MusicElement;
import com.sheet4j.core.model.Note;
import com.sheet4j.core.model.NoteType;
import com.sheet4j.core.model.Part;
import com.sheet4j.core.model.Pitch;
import com.sheet4j.core.model.Rest;
import com.sheet4j.core.model.Score;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns a {@link Score} into a framework-agnostic {@link LayoutResult}.
 *
 * <p>This first iteration lays every part out as one staff on a single system,
 * with measures evenly spaced across the available width and note heads
 * positioned vertically from their pitch and the prevailing clef.
 */
public final class Engraver {

    public LayoutResult layout(Score score, LayoutOptions options) {
        List<StaffLayout> staves = new ArrayList<>();
        double y = options.topMargin();
        double staffWidth = options.systemWidth() - options.leftMargin() - options.rightMargin();

        for (Part part : score.parts()) {
            staves.add(layoutPart(part, options.leftMargin(), y, staffWidth, options));
            y += options.staffHeight() + options.staffSpacing();
        }

        double height = staves.isEmpty()
                ? options.topMargin() + options.staffHeight()
                : y - options.staffSpacing() + options.rightMargin();
        SystemLayout system = new SystemLayout(0, 0, options.systemWidth(), staves);
        return new LayoutResult(List.of(system), options.systemWidth(), height);
    }

    private StaffLayout layoutPart(Part part, double x, double y, double width, LayoutOptions options) {
        List<MeasureLayout> measureLayouts = new ArrayList<>();
        List<GlyphPlacement> glyphs = new ArrayList<>();

        int measureCount = Math.max(1, part.measures().size());
        double measureWidth = Math.max(options.measureMinWidth(), width / measureCount);

        Clef currentClef = Clef.treble();
        double cursorX = x;
        boolean first = true;

        for (Measure measure : part.measures()) {
            if (measure.attributes().isPresent()) {
                Attributes attributes = measure.attributes().get();
                if (attributes.clef().isPresent()) {
                    currentClef = attributes.clef().get();
                }
            }

            measureLayouts.add(new MeasureLayout(measure.number(), cursorX, measureWidth));

            double contentStart = cursorX + options.staffLineGap();
            if (first) {
                glyphs.add(new GlyphPlacement(cursorX + options.staffLineGap() * 0.5,
                        y + options.staffHeight() * 0.5, clefGlyph(currentClef), 4));
                contentStart = cursorX + options.staffLineGap() * 4;
                if (measure.attributes().flatMap(Attributes::timeSignature).isPresent()) {
                    glyphs.add(new GlyphPlacement(contentStart, y + options.staffLineGap(),
                            Glyph.TIME_DIGIT, 2));
                    contentStart += options.staffLineGap() * 2;
                }
            }

            List<MusicElement> elements = measure.elements();
            double available = (cursorX + measureWidth) - contentStart - options.staffLineGap();
            double step = elements.isEmpty() ? available : available / elements.size();
            double noteX = contentStart;
            for (MusicElement element : elements) {
                placeElement(glyphs, element, noteX, y, currentClef, options);
                noteX += step;
            }

            cursorX += measureWidth;
            first = false;
        }

        return new StaffLayout(x, y, cursorX - x, options.staffLineGap(), measureLayouts, glyphs);
    }

    private void placeElement(List<GlyphPlacement> glyphs, MusicElement element, double noteX,
                              double staffY, Clef clef, LayoutOptions options) {
        if (element instanceof Note note) {
            glyphs.add(placeNote(note, noteX, staffY, clef, options));
        } else if (element instanceof Chord chord) {
            for (Note note : chord.notes()) {
                glyphs.add(placeNote(note, noteX, staffY, clef, options));
            }
        } else if (element instanceof Rest rest) {
            glyphs.add(new GlyphPlacement(noteX, staffY + options.staffHeight() * 0.5,
                    restGlyph(rest.type()), 4));
        }
    }

    private GlyphPlacement placeNote(Note note, double noteX, double staffY, Clef clef, LayoutOptions options) {
        int staffStep = staffStep(note.pitch(), clef);
        double y = staffY + staffStep * (options.staffLineGap() / 2.0);
        return new GlyphPlacement(noteX, y, noteheadGlyph(note.type()), staffStep);
    }

    /**
     * Vertical position of a pitch expressed in half staff spaces below the top
     * staff line (0 = top line, positive downward).
     */
    static int staffStep(Pitch pitch, Clef clef) {
        int refStaffStep = 2 * (5 - clef.line());
        int refStepNumber = clefReferenceStepNumber(clef.sign());
        return refStaffStep + (refStepNumber - pitch.diatonicStepNumber());
    }

    private static int clefReferenceStepNumber(ClefSign sign) {
        return switch (sign) {
            case F -> new Pitch(com.sheet4j.core.model.Step.F, 3).diatonicStepNumber();
            case C -> new Pitch(com.sheet4j.core.model.Step.C, 4).diatonicStepNumber();
            default -> new Pitch(com.sheet4j.core.model.Step.G, 4).diatonicStepNumber();
        };
    }

    private static Glyph clefGlyph(Clef clef) {
        return switch (clef.sign()) {
            case F -> Glyph.CLEF_F;
            case C -> Glyph.CLEF_C;
            default -> Glyph.CLEF_G;
        };
    }

    private static Glyph noteheadGlyph(NoteType type) {
        return switch (type) {
            case WHOLE, BREVE, LONG, MAXIMA -> Glyph.NOTEHEAD_WHOLE;
            case HALF -> Glyph.NOTEHEAD_HALF;
            default -> Glyph.NOTEHEAD_BLACK;
        };
    }

    private static Glyph restGlyph(NoteType type) {
        return switch (type) {
            case WHOLE, BREVE, LONG, MAXIMA -> Glyph.REST_WHOLE;
            case HALF -> Glyph.REST_HALF;
            case QUARTER -> Glyph.REST_QUARTER;
            default -> Glyph.REST_EIGHTH;
        };
    }
}
