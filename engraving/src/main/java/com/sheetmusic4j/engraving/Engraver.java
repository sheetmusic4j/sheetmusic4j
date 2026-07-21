package com.sheetmusic4j.engraving;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.sheetmusic4j.core.model.Accidental;
import com.sheetmusic4j.core.model.Attributes;
import com.sheetmusic4j.core.model.Beam;
import com.sheetmusic4j.core.model.Chord;
import com.sheetmusic4j.core.model.Clef;
import com.sheetmusic4j.core.model.ClefSign;
import com.sheetmusic4j.core.model.KeySignature;
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
 *   <li>lays every part out as one or more staves (multi-staff piano grand staff supported)
 *       on a single system,</li>
 *   <li>emits real per-digit time signature glyphs based on
 *       {@link Attributes#timeSignature()},</li>
 *   <li>emits key-signature accidentals after the clef,</li>
 *   <li>emits {@link Glyph#STEM_UP}/{@link Glyph#STEM_DOWN} placements for notes
 *       whose duration is shorter than a whole note, with direction chosen by
 *       staff position (down when above the middle line, up otherwise),</li>
 *   <li>emits flags for unbeamed short notes and beam segments for beamed groups,</li>
 *   <li>emits accidentals, augmentation dots, and tie arcs when the note carries them,</li>
 *   <li>sizes measures proportionally to the sum of their note/rest durations,
 *       clamped by {@link LayoutOptions#measureMinWidth()}.</li>
 * </ul>
 */
public final class Engraver {

    /**
     * Half-staff-step position of the middle staff line for a five-line staff.
     */
    private static final int MIDDLE_LINE_STAFF_STEP = 4;
    private static final double STEM_LENGTH_GAPS = 3.5;

    public LayoutResult layout(Score score, LayoutOptions options) {
        List<StaffLayout> staves = new ArrayList<>();
        double y = options.topMargin();
        double staffWidth = options.systemWidth() - options.leftMargin() - options.rightMargin();

        for (Part part : score.parts()) {
            List<StaffLayout> partStaves = layoutPart(part, options.leftMargin(), y, staffWidth, options);
            staves.addAll(partStaves);
            y += (options.staffHeight() + options.staffSpacing()) * Math.max(1, partStaves.size());
        }

        double height = staves.isEmpty()
                ? options.topMargin() + options.staffHeight()
                : y - options.staffSpacing() + options.rightMargin();
        SystemLayout system = new SystemLayout(0, 0, options.systemWidth(), staves);
        return new LayoutResult(List.of(system), options.systemWidth(), height);
    }

    private List<StaffLayout> layoutPart(Part part, double x, double y, double width, LayoutOptions options) {
        int staveCount = determineStaveCount(part);
        List<Clef> defaultClefs = defaultClefsFor(part, staveCount);

        List<StaffLayout> result = new ArrayList<>(staveCount);
        double staffTop = y;
        for (int staffIdx = 0; staffIdx < staveCount; staffIdx++) {
            Clef clefForStaff = staffIdx < defaultClefs.size() ? defaultClefs.get(staffIdx) : Clef.treble();
            int staffNumber = staffIdx + 1;
            result.add(layoutStaff(part, x, staffTop, width, options, staffNumber, clefForStaff, staveCount));
            staffTop += options.staffHeight() + options.staffSpacing();
        }
        return result;
    }

    /**
     * Number of staves declared by the first measure of the part that carries an
     * {@code Attributes#staves()} value.
     */
    private static int determineStaveCount(Part part) {
        for (Measure measure : part.measures()) {
            if (measure.attributes().isPresent()) {
                Attributes attr = measure.attributes().get();
                if (attr.staves().isPresent()) {
                    return Math.max(1, attr.staves().get());
                }
            }
        }
        return 1;
    }

    /**
     * Initial clef per staff, taken from the first measure's clef list when
     * available and falling back to standard treble/bass grand-staff defaults.
     */
    private static List<Clef> defaultClefsFor(Part part, int staveCount) {
        for (Measure measure : part.measures()) {
            if (measure.attributes().isPresent()) {
                Attributes attr = measure.attributes().get();
                if (!attr.clefs().isEmpty()) {
                    return attr.clefs();
                }
            }
        }
        List<Clef> defaults = new ArrayList<>(staveCount);
        for (int i = 0; i < staveCount; i++) {
            defaults.add(i == 0 ? Clef.treble() : Clef.bass());
        }
        return defaults;
    }

    private StaffLayout layoutStaff(Part part, double x, double y, double width, LayoutOptions options,
                                    int staffNumber, Clef initialClef, int totalStaves) {
        List<MeasureLayout> measureLayouts = new ArrayList<>();
        List<GlyphPlacement> glyphs = new ArrayList<>();
        List<BeamPlacement> beams = new ArrayList<>();
        List<TiePlacement> ties = new ArrayList<>();

        List<Double> measureWidths = computeMeasureWidths(part, width, options);

        Clef currentClef = initialClef;
        KeySignature currentKey = KeySignature.cMajor();
        TimeSignature currentTimeSignature = null;
        double cursorX = x;
        boolean first = true;

        Map<Integer, BeamRun> openBeams = new HashMap<>();
        Map<String, PlacedNote> tieCandidates = new HashMap<>();

        for (int idx = 0; idx < part.measures().size(); idx++) {
            Measure measure = part.measures().get(idx);
            if (measure.attributes().isPresent()) {
                Attributes attributes = measure.attributes().get();
                // Per-staff clef selection.
                if (!attributes.clefs().isEmpty()) {
                    if (attributes.clefs().size() >= staffNumber) {
                        currentClef = attributes.clefs().get(staffNumber - 1);
                    } else if (attributes.clef().isPresent()) {
                        currentClef = attributes.clef().get();
                    }
                } else if (attributes.clef().isPresent()) {
                    currentClef = attributes.clef().get();
                }
                if (attributes.keySignature().isPresent()) {
                    currentKey = attributes.keySignature().get();
                }
                if (attributes.timeSignature().isPresent()) {
                    currentTimeSignature = attributes.timeSignature().get();
                }
            }

            double measureWidth = measureWidths.get(idx);
            measureLayouts.add(new MeasureLayout(measure.number(), cursorX, measureWidth));

            double contentStart = cursorX + options.staffLineGap();
            if (first) {
                double clefY = y + clefAnchorLineIndex(currentClef) * options.staffLineGap();
                glyphs.add(new GlyphPlacement(cursorX + options.staffLineGap() * 0.5,
                        clefY, clefGlyph(currentClef), 4));
                contentStart = cursorX + options.staffLineGap() * 4;
                if (currentKey.fifths() != 0) {
                    contentStart = placeKeySignature(glyphs, currentKey, currentClef, contentStart, y, options);
                }
                if (currentTimeSignature != null) {
                    contentStart = placeTimeSignature(glyphs, currentTimeSignature, contentStart, y, options);
                }
            }

            List<MusicElement> elements = filterElementsForStaff(measure.elements(), staffNumber, totalStaves);
            double available = (cursorX + measureWidth) - contentStart - options.staffLineGap();
            double step = elements.isEmpty() ? available : available / elements.size();
            double noteX = contentStart;
            for (MusicElement element : elements) {
                placeElement(glyphs, beams, ties, openBeams, tieCandidates, element, noteX, y, currentClef, options);
                noteX += step;
            }

            cursorX += measureWidth;
            first = false;
        }

        return new StaffLayout(x, y, cursorX - x, options.staffLineGap(), measureLayouts, glyphs, beams, ties);
    }

    /**
     * For a multi-staff part, keep only elements that belong to the given
     * staff number. For a single-staff part (or elements with a default
     * staff assignment), all elements pass through.
     */
    private static List<MusicElement> filterElementsForStaff(List<MusicElement> elements, int staffNumber, int totalStaves) {
        if (totalStaves <= 1) {
            return elements;
        }
        List<MusicElement> filtered = new ArrayList<>(elements.size());
        for (MusicElement element : elements) {
            if (elementStaff(element) == staffNumber) {
                filtered.add(element);
            }
        }
        return filtered;
    }

    private static int elementStaff(MusicElement element) {
        if (element instanceof Note note) {
            return note.staff();
        }
        if (element instanceof Chord chord) {
            for (Note note : chord.notes()) {
                return note.staff();
            }
        }
        // Rests inherit the previous staff assignment; default to staff 1.
        return 1;
    }

    /**
     * Vertical line index (0 = top line, 4 = bottom line) the given clef is
     * anchored on. MusicXML {@code <line>} is counted from the bottom (1-based),
     * so top-index = 5 - line.
     */
    private static int clefAnchorLineIndex(Clef clef) {
        int line = clef.line();
        if (line < 1) {
            line = 1;
        } else if (line > 5) {
            line = 5;
        }
        return 5 - line;
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
        if (sumRawWidth <= totalWidth) {
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
        return sum > 0 ? sum : 1.0;
    }

    private double placeKeySignature(List<GlyphPlacement> glyphs, KeySignature key, Clef clef,
                                     double startX, double staffY, LayoutOptions options) {
        int[] positions = KeySignatureLayout.positions(clef, key);
        Glyph glyph = KeySignatureLayout.glyphFor(key);
        double advance = options.staffLineGap() * 1.1;
        double x = startX;
        for (int step : positions) {
            double gy = staffY + step * (options.staffLineGap() / 2.0);
            glyphs.add(new GlyphPlacement(x, gy, glyph, step));
            x += advance;
        }
        return x + options.staffLineGap() * 0.5;
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

    private void placeElement(List<GlyphPlacement> glyphs, List<BeamPlacement> beams, List<TiePlacement> ties,
                              Map<Integer, BeamRun> openBeams, Map<String, PlacedNote> tieCandidates,
                              MusicElement element, double noteX, double staffY,
                              Clef clef, LayoutOptions options) {
        if (element instanceof Note note) {
            placeNote(glyphs, beams, ties, openBeams, tieCandidates, note, noteX, staffY, clef, options);
        } else if (element instanceof Chord chord) {
            for (Note note : chord.notes()) {
                placeNote(glyphs, beams, ties, openBeams, tieCandidates, note, noteX, staffY, clef, options);
            }
        } else if (element instanceof Rest rest) {
            double gap = options.staffLineGap();
            int restStep = restAnchorStaffStep(rest.type());
            double y = staffY + restStep * (gap / 2.0);
            glyphs.add(new GlyphPlacement(noteX, y, restGlyph(rest.type()), restStep));
            for (int i = 0; i < rest.dots(); i++) {
                double dx = noteX + gap * 1.2 + i * gap * 0.6;
                glyphs.add(new GlyphPlacement(dx, y, Glyph.AUG_DOT, restStep));
            }
        }
    }

    /**
     * Staff-step position at which a rest of the given type is anchored.
     */
    private static int restAnchorStaffStep(NoteType type) {
        return switch (type) {
            case WHOLE, BREVE, LONG, MAXIMA -> 2;
            case HALF -> 4;
            default -> 4;
        };
    }

    private void placeNote(List<GlyphPlacement> glyphs, List<BeamPlacement> beams, List<TiePlacement> ties,
                           Map<Integer, BeamRun> openBeams, Map<String, PlacedNote> tieCandidates,
                           Note note, double noteX, double staffY, Clef clef, LayoutOptions options) {
        double gap = options.staffLineGap();
        int staffStep = staffStep(note.pitch(), clef);
        double y = staffY + staffStep * (gap / 2.0);

        // Accidental: prefer the explicit displayed accidental when present
        // (may be NATURAL), otherwise fall back to a chromatic alter on the pitch.
        Accidental acc = note.displayedAccidental().orElse(null);
        if (acc == null && note.pitch().alter() != 0) {
            acc = note.pitch().accidental();
        }
        if (acc != null) {
            Glyph accGlyph = accidentalGlyph(acc);
            if (accGlyph != null) {
                glyphs.add(new GlyphPlacement(noteX - gap * 1.5, y, accGlyph, staffStep));
            }
        }

        glyphs.add(new GlyphPlacement(noteX, y, noteheadGlyph(note.type()), staffStep));

        boolean stemUp = staffStep >= MIDDLE_LINE_STAFF_STEP;
        Glyph stem = stemGlyph(note.type(), staffStep);
        if (stem != null) {
            glyphs.add(new GlyphPlacement(noteX, y, stem, staffStep));
        }

        // Augmentation dots.
        for (int i = 0; i < note.dots(); i++) {
            int dotStep = staffStep % 2 == 0 ? staffStep - 1 : staffStep;
            double dx = noteX + gap * 1.2 + i * gap * 0.6;
            double dy = staffY + dotStep * (gap / 2.0);
            glyphs.add(new GlyphPlacement(dx, dy, Glyph.AUG_DOT, dotStep));
        }

        // Beam vs. flag handling.
        boolean beamed = note.isBeamed();
        if (!beamed && stem != null) {
            Glyph flag = flagGlyph(note.type(), stemUp);
            if (flag != null) {
                double stemTipY = stemUp ? y - gap * STEM_LENGTH_GAPS : y + gap * STEM_LENGTH_GAPS;
                double stemTipX = stemUp ? noteX + noteheadHalfWidth(note.type(), gap) : noteX - noteheadHalfWidth(note.type(), gap);
                glyphs.add(new GlyphPlacement(stemTipX, stemTipY, flag, staffStep));
            }
        } else if (beamed && stem != null) {
            double stemTipX = stemUp ? noteX + noteheadHalfWidth(note.type(), gap) : noteX - noteheadHalfWidth(note.type(), gap);
            double stemTipY = stemUp ? y - gap * STEM_LENGTH_GAPS : y + gap * STEM_LENGTH_GAPS;
            processBeams(beams, openBeams, note.beams(), stemTipX, stemTipY, stemUp);
        }

        // Ties: if this note carries tieStart, remember it; if it carries
        // tieStop, close any tie candidate at the same pitch.
        String key = pitchKey(note.pitch());
        if (note.tieStop()) {
            PlacedNote start = tieCandidates.remove(key);
            if (start != null) {
                boolean curveUp = staffStep >= MIDDLE_LINE_STAFF_STEP;
                double yShift = curveUp ? -gap * 0.8 : gap * 0.8;
                double startY = start.y() + yShift;
                double endY = y + yShift;
                ties.add(new TiePlacement(start.x() + gap * 0.5, startY, noteX - gap * 0.5, endY, curveUp));
            }
        }
        if (note.tieStart()) {
            tieCandidates.put(key, new PlacedNote(noteX, y));
        }
    }

    private static double noteheadHalfWidth(NoteType type, double gap) {
        return switch (type) {
            case WHOLE, BREVE, LONG, MAXIMA -> 0.75 * gap;
            default -> 0.59 * gap;
        };
    }

    private static String pitchKey(Pitch pitch) {
        return pitch.step().name() + pitch.octave() + "@" + pitch.alter();
    }

    private static Glyph accidentalGlyph(Accidental accidental) {
        if (accidental == null) {
            return null;
        }
        return switch (accidental) {
            case SHARP -> Glyph.ACCIDENTAL_SHARP;
            case FLAT -> Glyph.ACCIDENTAL_FLAT;
            case NATURAL -> Glyph.ACCIDENTAL_NATURAL;
            case DOUBLE_SHARP -> Glyph.ACCIDENTAL_DOUBLE_SHARP;
            case DOUBLE_FLAT -> Glyph.ACCIDENTAL_DOUBLE_FLAT;
        };
    }

    /**
     * Handle beam state transitions for a single note's beam entries.
     * Emits a {@link BeamPlacement} when a run of {@code BEGIN..CONTINUE*..END}
     * closes.
     */
    private static void processBeams(List<BeamPlacement> beams, Map<Integer, BeamRun> openBeams,
                                     List<Beam> noteBeams,
                                     double stemTipX, double stemTipY, boolean stemUp) {
        for (Beam beam : noteBeams) {
            int level = beam.number();
            switch (beam.state()) {
                case BEGIN -> openBeams.put(level, new BeamRun(stemTipX, stemTipY, stemUp));
                case CONTINUE -> {
                    // no-op: run stays open
                }
                case END -> {
                    BeamRun run = openBeams.remove(level);
                    if (run != null) {
                        beams.add(new BeamPlacement(run.startX, run.startY, stemTipX, stemTipY, level, run.stemUp));
                    }
                }
                case FORWARD_HOOK -> {
                    double hookX = stemTipX + 8.0;
                    beams.add(new BeamPlacement(stemTipX, stemTipY, hookX, stemTipY, level, stemUp));
                }
                case BACKWARD_HOOK -> {
                    double hookX = stemTipX - 8.0;
                    beams.add(new BeamPlacement(hookX, stemTipY, stemTipX, stemTipY, level, stemUp));
                }
            }
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
     * Choose the flag glyph for an unbeamed short note, or {@code null} if the
     * type does not use a flag.
     */
    static Glyph flagGlyph(NoteType type, boolean stemUp) {
        return switch (type) {
            case EIGHTH -> stemUp ? Glyph.FLAG_8TH_UP : Glyph.FLAG_8TH_DOWN;
            case SIXTEENTH, THIRTY_SECOND, SIXTY_FOURTH, HUNDRED_TWENTY_EIGHTH ->
                    stemUp ? Glyph.FLAG_16TH_UP : Glyph.FLAG_16TH_DOWN;
            default -> null;
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

    private record BeamRun(double startX, double startY, boolean stemUp) {
    }

    private record PlacedNote(double x, double y) {
    }
}
