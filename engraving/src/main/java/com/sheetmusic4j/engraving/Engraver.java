package com.sheetmusic4j.engraving;

import java.util.ArrayList;
import java.util.List;

import com.sheetmusic4j.core.model.Attributes;
import com.sheetmusic4j.core.model.Chord;
import com.sheetmusic4j.core.model.Clef;
import com.sheetmusic4j.core.model.ClefSign;
import com.sheetmusic4j.core.model.Measure;
import com.sheetmusic4j.core.model.MusicElement;
import com.sheetmusic4j.core.model.Note;
import com.sheetmusic4j.core.model.NoteType;
import com.sheetmusic4j.core.model.Part;
import com.sheetmusic4j.core.model.Pitch;
import com.sheetmusic4j.core.model.Rest;
import com.sheetmusic4j.core.model.Score;
import com.sheetmusic4j.core.model.TimeSignature;

/**
 * Turns a {@link Score} into a framework-agnostic {@link LayoutResult}.
 *
 * <p>This iteration:
 * <ul>
 *   <li>lays every part out as one staff on a single system,</li>
 *   <li>emits real per-digit time signature glyphs based on
 *       {@link Attributes#timeSignature()},</li>
 *   <li>emits {@link Glyph#STEM_UP}/{@link Glyph#STEM_DOWN} placements for notes
 *       whose duration is shorter than a whole note, with direction chosen by
 *       staff position (down when above the middle line, up otherwise), and</li>
 *   <li>sizes measures proportionally to the sum of their note/rest durations,
 *       clamped by {@link LayoutOptions#measureMinWidth()}.</li>
 * </ul>
 */
public final class Engraver {

    /**
     * Half-staff-step position of the middle staff line for a five-line staff.
     */
    private static final int MIDDLE_LINE_STAFF_STEP = 4;

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

        List<Double> measureWidths = computeMeasureWidths(part, width, options);

        Clef currentClef = Clef.treble();
        TimeSignature currentTimeSignature = null;
        double cursorX = x;
        boolean first = true;

        for (int idx = 0; idx < part.measures().size(); idx++) {
            Measure measure = part.measures().get(idx);
            if (measure.attributes().isPresent()) {
                Attributes attributes = measure.attributes().get();
                if (attributes.clef().isPresent()) {
                    currentClef = attributes.clef().get();
                }
                if (attributes.timeSignature().isPresent()) {
                    currentTimeSignature = attributes.timeSignature().get();
                }
            }

            double measureWidth = measureWidths.get(idx);
            measureLayouts.add(new MeasureLayout(measure.number(), cursorX, measureWidth));

            double contentStart = cursorX + options.staffLineGap();
            if (first) {
                glyphs.add(new GlyphPlacement(cursorX + options.staffLineGap() * 0.5,
                        y + options.staffHeight() * 0.5, clefGlyph(currentClef), 4));
                contentStart = cursorX + options.staffLineGap() * 4;
                if (currentTimeSignature != null) {
                    contentStart = placeTimeSignature(glyphs, currentTimeSignature, contentStart, y, options);
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

    /**
     * Compute a proportional width per measure based on the total quarter-note
     * duration each measure carries, clamped to at least {@link LayoutOptions#measureMinWidth()}
     * and scaled to fill the available staff width when possible.
     */
    private static List<Double> computeMeasureWidths(Part part, double totalWidth, LayoutOptions options) {
        List<Double> weights = new ArrayList<>();
        double sumWeights = 0.0;
        for (Measure measure : part.measures()) {
            double weight = measureWeight(measure);
            weights.add(weight);
            sumWeights += weight;
        }
        if (weights.isEmpty()) {
            return weights;
        }

        List<Double> widths = new ArrayList<>(weights.size());
        double min = options.measureMinWidth();
        double sumRawWidth = 0.0;
        for (double w : weights) {
            double raw = sumWeights > 0 ? totalWidth * (w / sumWeights) : totalWidth / weights.size();
            double width = Math.max(min, raw);
            widths.add(width);
            sumRawWidth += width;
        }
        // If the clamped total overshoots the available width, we still want the
        // caller to lay them out sequentially (they simply extend beyond the
        // system boundary). No further scaling — a real implementation would break
        // systems, which is a follow-up (see plan step C.9).
        if (sumRawWidth <= totalWidth) {
            // Distribute leftover space proportionally so the last measure lines up.
            double leftover = totalWidth - sumRawWidth;
            for (int i = 0; i < widths.size(); i++) {
                widths.set(i, widths.get(i) + leftover * (weights.get(i) / Math.max(sumWeights, 1e-9)));
            }
        }
        return widths;
    }

    private static double measureWeight(Measure measure) {
        double sum = 0.0;
        for (MusicElement element : measure.elements()) {
            sum += element.duration().inQuarters();
        }
        // A completely empty measure still deserves some width.
        return sum > 0 ? sum : 1.0;
    }

    private double placeTimeSignature(List<GlyphPlacement> glyphs, TimeSignature timeSignature,
                                      double startX, double staffY, LayoutOptions options) {
        String beats = Integer.toString(timeSignature.beats());
        String beatType = Integer.toString(timeSignature.beatType());
        int maxLen = Math.max(beats.length(), beatType.length());
        double digitWidth = options.staffLineGap() * 1.4;
        double topY = staffY + options.staffLineGap();
        double bottomY = staffY + options.staffLineGap() * 3;

        double x = startX;
        int lenDiff = maxLen - beats.length();
        for (int i = 0; i < beats.length(); i++) {
            int digit = beats.charAt(i) - '0';
            glyphs.add(new GlyphPlacement(x + (i + lenDiff) * digitWidth, topY,
                    Glyph.timeDigit(digit), 2));
        }
        lenDiff = maxLen - beatType.length();
        for (int i = 0; i < beatType.length(); i++) {
            int digit = beatType.charAt(i) - '0';
            glyphs.add(new GlyphPlacement(x + (i + lenDiff) * digitWidth, bottomY,
                    Glyph.timeDigit(digit), 6));
        }
        return startX + maxLen * digitWidth + options.staffLineGap();
    }

    private void placeElement(List<GlyphPlacement> glyphs, MusicElement element, double noteX,
                              double staffY, Clef clef, LayoutOptions options) {
        if (element instanceof Note note) {
            placeNote(glyphs, note, noteX, staffY, clef, options);
        } else if (element instanceof Chord chord) {
            for (Note note : chord.notes()) {
                placeNote(glyphs, note, noteX, staffY, clef, options);
            }
        } else if (element instanceof Rest rest) {
            glyphs.add(new GlyphPlacement(noteX, staffY + options.staffHeight() * 0.5,
                    restGlyph(rest.type()), 4));
        }
    }

    private void placeNote(List<GlyphPlacement> glyphs, Note note, double noteX, double staffY,
                           Clef clef, LayoutOptions options) {
        int staffStep = staffStep(note.pitch(), clef);
        double y = staffY + staffStep * (options.staffLineGap() / 2.0);
        glyphs.add(new GlyphPlacement(noteX, y, noteheadGlyph(note.type()), staffStep));

        Glyph stem = stemGlyph(note.type(), staffStep);
        if (stem != null) {
            glyphs.add(new GlyphPlacement(noteX, y, stem, staffStep));
        }
    }

    /**
     * Choose the stem direction for a note based on its type and staff position.
     * Whole/breve/long/maxima notes have no stem. Notes on or below the middle
     * line get a stem up; notes above the middle line get a stem down.
     */
    static Glyph stemGlyph(NoteType type, int staffStep) {
        return switch (type) {
            case WHOLE, BREVE, LONG, MAXIMA -> null;
            default -> staffStep >= MIDDLE_LINE_STAFF_STEP ? Glyph.STEM_UP : Glyph.STEM_DOWN;
        };
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
            case F -> new Pitch(com.sheetmusic4j.core.model.Step.F, 3).diatonicStepNumber();
            case C -> new Pitch(com.sheetmusic4j.core.model.Step.C, 4).diatonicStepNumber();
            default -> new Pitch(com.sheetmusic4j.core.model.Step.G, 4).diatonicStepNumber();
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
