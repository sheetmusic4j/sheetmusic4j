package com.sheetmusic4j.engraving.staff;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.sheetmusic4j.core.model.Accidental;
import com.sheetmusic4j.core.model.Articulation;
import com.sheetmusic4j.core.model.Chord;
import com.sheetmusic4j.core.model.Clef;
import com.sheetmusic4j.core.model.Direction;
import com.sheetmusic4j.core.model.Harmony;
import com.sheetmusic4j.core.model.KeySignature;
import com.sheetmusic4j.core.model.Measure;
import com.sheetmusic4j.core.model.MeasureTiming;
import com.sheetmusic4j.core.model.MusicElement;
import com.sheetmusic4j.core.model.Note;
import com.sheetmusic4j.core.model.Pitch;
import com.sheetmusic4j.core.model.Rest;
import com.sheetmusic4j.core.model.TimeSignature;
import com.sheetmusic4j.engraving.analysis.PartInfo;
import com.sheetmusic4j.engraving.analysis.ScoreAnalysis;
import com.sheetmusic4j.engraving.glyph.Glyph;
import com.sheetmusic4j.engraving.glyph.MarkingCategory;
import com.sheetmusic4j.engraving.layout.KeySignatureLayout;
import com.sheetmusic4j.engraving.layout.LayoutOptions;
import com.sheetmusic4j.engraving.layout.MeasureLayout;
import com.sheetmusic4j.engraving.layout.NoteAnchor;
import com.sheetmusic4j.engraving.layout.StaffLayout;
import com.sheetmusic4j.engraving.placement.GlyphPlacement;
import com.sheetmusic4j.engraving.placement.GraceNotePlacement;
import com.sheetmusic4j.engraving.placement.StemPlacement;
import com.sheetmusic4j.engraving.placement.TextPlacement;
import com.sheetmusic4j.engraving.spacing.MeasureColumns;
import com.sheetmusic4j.engraving.spacing.MeasureStart;
import com.sheetmusic4j.engraving.staff.StemPlan.Stem;

import static com.sheetmusic4j.engraving.glyph.GlyphMapper.accidentalGlyph;
import static com.sheetmusic4j.engraving.glyph.GlyphMapper.articulationGlyph;
import static com.sheetmusic4j.engraving.glyph.GlyphMapper.clefGlyph;
import static com.sheetmusic4j.engraving.glyph.GlyphMapper.flagGlyph;
import static com.sheetmusic4j.engraving.glyph.GlyphMapper.noteheadGlyph;
import static com.sheetmusic4j.engraving.glyph.GlyphMapper.restGlyph;
import static com.sheetmusic4j.engraving.staff.StaffGeometry.clefAnchorLineIndex;
import static com.sheetmusic4j.engraving.staff.StaffGeometry.noteheadHalfWidth;
import static com.sheetmusic4j.engraving.staff.StaffGeometry.restStaffStep;
import static com.sheetmusic4j.engraving.staff.StaffGeometry.staffStep;

/**
 * Lays out one staff of one part across the measures of one row, producing
 * its {@link StaffLayout}. Stateful and single-use: it accumulates the
 * staff's placements while walking the measures, and owns the open spans
 * (beams, ties, slurs, tuplets, hairpins) that connect them.
 */
public final class StaffRowEngraver {

    /**
     * Stem length (in staff-line gaps) for the first/last grace note of a
     * run, measured from its own notehead - see
     * {@link #buildGraceNotePlacement}. The run's beam/stem-top line is
     * raked (sloped) between these two endpoints to follow the run's pitch
     * contour, the same way a beam over ordinary notes of varying pitch
     * would, rather than sitting flat.
     */
    private static final double GRACE_STEM_LENGTH_GAPS = 2.3;

    private final RowContext row;
    private final LayoutOptions options;
    private final PartInfo partInfo;
    private final int staffIdx;
    private final int staffNumber;
    private final int partIndex;
    private final int systemStaffIndex;
    private final double x;
    private final double staffY;
    private final List<NoteAnchor> anchors;

    private final List<MeasureLayout> measureLayouts = new ArrayList<>();
    private final List<GlyphPlacement> glyphs = new ArrayList<>();
    private final List<StemPlacement> stems = new ArrayList<>();
    private final List<GraceNotePlacement> graceNotePlacements = new ArrayList<>();
    private final SpanTracker spans;
    private final AnnotationLayout annotations;

    /**
     * @param row              row-wide context
     * @param partInfo         the part being laid out
     * @param staffIdx         0-based staff index within the part
     * @param partIndex        index of the part in the score
     * @param systemStaffIndex index of this staff within the system
     * @param x                left edge of the staff
     * @param staffY           y of the staff's top line
     * @param texts            page-level text collector
     * @param anchors          note-anchor collector
     */
    public StaffRowEngraver(RowContext row, PartInfo partInfo, int staffIdx, int partIndex,
                            int systemStaffIndex, double x, double staffY,
                            List<TextPlacement> texts, List<NoteAnchor> anchors) {
        this.row = row;
        this.options = row.options();
        this.partInfo = partInfo;
        this.staffIdx = staffIdx;
        this.staffNumber = staffIdx + 1;
        this.partIndex = partIndex;
        this.systemStaffIndex = systemStaffIndex;
        this.x = x;
        this.staffY = staffY;
        this.anchors = anchors;
        this.spans = new SpanTracker(options.staffLineGap());
        this.annotations = new AnnotationLayout(options, staffY, texts, glyphs);
    }

    public StaffLayout layout() {
        double cursorX = x;
        boolean firstMeasureInRow = true;
        // Layout x of musical time zero for the time-proportional STRIP path.
        double timeZeroX = x + row.rowHeader() + options.staffLineGap() * 0.5;

        for (int idx = row.start(); idx < row.endExclusive(); idx++) {
            if (idx >= partInfo.measures().size()) {
                break;
            }
            cursorX += layoutMeasure(idx, cursorX, firstMeasureInRow, timeZeroX);
            firstMeasureInRow = false;
        }

        return new StaffLayout(x, staffY, cursorX - x, options.staffLineGap(),
                measureLayouts, glyphs, spans.beams(), spans.ties(), spans.slurs(), spans.tuplets(),
                annotations.hairpins(), stems, graceNotePlacements);
    }

    /**
     * Lay out one measure starting at {@code cursorX} and return its width.
     */
    private double layoutMeasure(int idx, double cursorX, boolean firstMeasureInRow, double timeZeroX) {
        Measure measure = partInfo.measures().get(idx);
        Clef currentClef = partInfo.clefAt(idx, staffIdx);
        MeasureColumns columns = row.columns(idx);
        double gap = options.staffLineGap();

        double measureStartQ = row.timeline().start(idx);
        double measureDurQ = row.timeline().duration(idx);
        double measureWidth = row.rowWidths().get(idx - row.start());
        if (row.isStrip()) {
            // Notes are placed by absolute onset (independent of measure
            // geometry), so we're free to sit the barline at the MIDPOINT
            // of the whitespace between the system's last onset in this
            // measure and the next downbeat.
            double trailingQ = Math.max(0.0, Math.min(columns.trailingQuarters(), measureDurQ));
            double barlineX = timeZeroX + (measureStartQ + measureDurQ - trailingQ / 2.0) * row.pixelsPerQuarter();
            measureWidth = Math.max(barlineX - cursorX, gap);
        }
        boolean lastMeasureInRow = idx == row.endExclusive() - 1;
        MeasureStart start = MeasureStart.of(partInfo, staffIdx, idx, firstMeasureInRow, lastMeasureInRow,
                row.firstRow(), options);
        double leadingBarlineX = Double.isNaN(start.leadingBarlineOffset())
                ? cursorX : cursorX + start.leadingBarlineOffset();
        measureLayouts.add(new MeasureLayout(measure.number(), cursorX, measureWidth,
                measureStartQ, measureStartQ + measureDurQ,
                MeasureStart.closingBarline(partInfo, idx, lastMeasureInRow),
                MeasureStart.openingBarline(partInfo, idx, firstMeasureInRow), leadingBarlineX,
                measure.ending().orElse(null)));

        placeMeasureStart(start, idx, cursorX, currentClef);
        // Every staff places its notes from the system-wide content start,
        // at its onset's column, so simultaneous notes line up across
        // staves and voices.
        double contentStart = cursorX + row.contentOffset(idx);
        double[] columnX = row.isStrip()
                ? columns.stripPositions(timeZeroX, measureStartQ, row.pixelsPerQuarter())
                : columns.pagePositions(contentStart, (cursorX + measureWidth) - contentStart - row.trailingOffset(idx));

        List<MeasureTiming.Timed> elements = elementsOnThisStaff(partInfo.timing(idx));
        List<MusicElement> placed = new ArrayList<>(elements.size());
        Set<Integer> voices = new HashSet<>();
        for (MeasureTiming.Timed timed : elements) {
            if (MeasureColumns.isPlaced(timed.element())) {
                placed.add(timed.element());
                voices.add(MeasureTiming.voiceKey(timed.element()).voice());
            }
        }
        boolean polyphonic = voices.size() > 1;
        StemPlan stemPlan = StemPlan.compute(placed, currentClef, staffY, options, polyphonic);
        double measureTopY = stemPlan.topY(staffY);

        int placedIdx = 0;
        for (MeasureTiming.Timed timed : elements) {
            MusicElement element = timed.element();
            double elementX = columns.xAt(columnX, timed.onset(), contentStart);
            if (element instanceof Direction direction) {
                if (staffIdx == 0) {
                    annotations.placeDirection(direction, elementX, measureTopY);
                }
            } else if (element instanceof Harmony harmony) {
                if (staffIdx == 0) {
                    annotations.placeHarmony(harmony, elementX);
                }
            } else if (MeasureColumns.isPlaced(element)) {
                Stem stem = stemPlan.stem(placedIdx++);
                placeElement(element, elementX, currentClef, stem, polyphonic);
                if (element instanceof Note note && !note.graceNotes().isEmpty()) {
                    graceNotePlacements.add(buildGraceNotePlacement(note, elementX, currentClef));
                }
                if (staffIdx == 0) {
                    annotations.placeLyrics(element, elementX);
                }
                NoteAnchors.emit(anchors, element, elementX, staffY, currentClef, options,
                        stem.tipY(), stem.present(), polyphonic,
                        new NoteAnchors.Position(partIndex, systemStaffIndex, measure.number(),
                                measureStartQ + timed.onset().toDouble(), element.duration().inQuarters()));
            }
        }
        return measureWidth;
    }

    /**
     * The measure's elements that belong on this staff, in document order.
     * In a single-staff part that is all of them; in a multi-staff part,
     * directions and chord symbols (which carry no staff) go with staff 1.
     */
    private List<MeasureTiming.Timed> elementsOnThisStaff(MeasureTiming timing) {
        if (partInfo.staveCount() <= 1) {
            return timing.elements();
        }
        List<MeasureTiming.Timed> result = new ArrayList<>();
        for (MeasureTiming.Timed timed : timing.elements()) {
            if (MeasureTiming.voiceKey(timed.element()).staff() == staffNumber) {
                result.add(timed);
            }
        }
        return result;
    }

    /**
     * Draw what goes at the start of a measure - on the row's first measure
     * the clef, key signature and time signature - at the offsets computed
     * by {@link MeasureStart}.
     */
    private void placeMeasureStart(MeasureStart start, int idx, double cursorX, Clef currentClef) {
        double gap = options.staffLineGap();
        if (!Double.isNaN(start.clefOffset())) {
            double clefY = staffY + clefAnchorLineIndex(currentClef) * gap;
            glyphs.add(new GlyphPlacement(cursorX + start.clefOffset(), clefY, clefGlyph(currentClef), 4));
        }
        if (!Double.isNaN(start.keyOffset())) {
            placeKeySignature(partInfo.keyAt(idx), currentClef, cursorX + start.keyOffset());
        }
        if (!Double.isNaN(start.timeOffset())) {
            placeTimeSignature(partInfo.timeAt(idx), cursorX + start.timeOffset());
        }
    }

    /**
     * Place the key-signature accidentals from {@code startX}.
     */
    private void placeKeySignature(KeySignature key, Clef clef, double startX) {
        Glyph glyph = KeySignatureLayout.glyphFor(key);
        double advance = MeasureStart.keySignatureAdvance(options);
        double gx = startX;
        for (int step : KeySignatureLayout.positions(clef, key)) {
            double gy = staffY + step * (options.staffLineGap() / 2.0);
            glyphs.add(new GlyphPlacement(gx, gy, glyph, step));
            gx += advance;
        }
    }

    /**
     * Place the time-signature digits from {@code startX}.
     */
    private void placeTimeSignature(TimeSignature timeSignature, double startX) {
        String beats = Integer.toString(timeSignature.beats());
        String beatType = Integer.toString(timeSignature.beatType());
        int maxLen = Math.max(beats.length(), beatType.length());
        double digitWidth = MeasureStart.timeDigitWidth(options);
        double topY = staffY + options.staffLineGap();
        double bottomY = staffY + options.staffLineGap() * 3;

        int lenDiff = maxLen - beats.length();
        for (int i = 0; i < beats.length(); i++) {
            int digit = beats.charAt(i) - '0';
            glyphs.add(new GlyphPlacement(startX + (i + lenDiff) * digitWidth, topY,
                    Glyph.timeDigit(digit), 2));
        }
        lenDiff = maxLen - beatType.length();
        for (int i = 0; i < beatType.length(); i++) {
            int digit = beatType.charAt(i) - '0';
            glyphs.add(new GlyphPlacement(startX + (i + lenDiff) * digitWidth, bottomY,
                    Glyph.timeDigit(digit), 6));
        }
    }

    private void placeElement(MusicElement element, double noteX, Clef clef, Stem stem, boolean polyphonic) {
        if (element instanceof Note note) {
            placeNote(note, noteX, clef, stem, false, true, true);
        } else if (element instanceof Chord chord) {
            placeChord(chord, noteX, clef, stem);
        } else if (element instanceof Rest rest) {
            placeRest(rest, noteX, polyphonic);
        }
    }

    /**
     * A stacked chord shares exactly one stem (and one beam/flag) across all
     * its notes, anchored at whichever notehead sits closest to the stem
     * tip - not one independent stem per note. MusicXML also puts
     * {@code <beam>} elements only on one representative note per chord
     * (usually the first non-{@code <chord/>}-tagged one); the other stacked
     * notes carry an empty beams() list even though the whole chord is
     * beamed. Direction and tip are precomputed (see {@link StemPlan}) from
     * the true pitch extreme across the whole beamed run, not just this one
     * chord.
     */
    private void placeChord(Chord chord, double noteX, Clef clef, Stem stem) {
        List<Note> notes = chord.notes();
        Note beamDataNote = null;
        boolean chordBeamed = false;
        for (Note note : notes) {
            if (note.isBeamed()) {
                chordBeamed = true;
                if (beamDataNote == null) {
                    beamDataNote = note;
                }
            }
        }
        // The stem is drawn once, anchored at whichever notehead is
        // geometrically nearest the stem tip - the lowest pitch (largest
        // staffStep) for stem-up, the highest pitch (smallest staffStep)
        // for stem-down - regardless of which note happens to carry the
        // real <beam> data. MusicXML tags exactly one representative
        // note per chord with <beam> elements (commonly always the same
        // member, e.g. the lowest, irrespective of stem direction), so
        // that note is NOT reliably the geometric anchor: using it as
        // the anchor for a stem-down chord would start the stem at the
        // wrong end, leaving the actual near notehead disconnected
        // rather than merely under-clearing it.
        Note stemNote = notes.get(0);
        int nearStep = staffStep(stemNote.pitch(), clef);
        for (Note note : notes) {
            int step = staffStep(note.pitch(), clef);
            if (stem.up() ? step > nearStep : step < nearStep) {
                nearStep = step;
                stemNote = note;
            }
        }
        for (Note note : notes) {
            placeNote(note, noteX, clef, stem, chordBeamed, note == stemNote, note == beamDataNote);
        }
    }

    private void placeRest(Rest rest, double noteX, boolean polyphonic) {
        double gap = options.staffLineGap();
        int restStep = restStaffStep(rest, polyphonic);
        double y = staffY + restStep * (gap / 2.0);
        glyphs.add(new GlyphPlacement(noteX, y, restGlyph(rest.type()), restStep, MarkingCategory.NOTE, rest));
        for (int i = 0; i < rest.dots(); i++) {
            double dx = noteX + gap * 1.2 + i * gap * 0.6;
            glyphs.add(new GlyphPlacement(dx, y, Glyph.AUG_DOT, restStep, MarkingCategory.NOTE, rest));
        }
        spans.tuplets(rest.voice(), rest.tuplets(), rest.timeModification(), noteX, y);
    }

    /**
     * Place one note (standalone, or one member of a chord).
     *
     * @param chordBeamed           whether any note of the enclosing chord is beamed
     * @param isStemResponsible     whether this note draws the (shared) stem and flag
     * @param isBeamDataResponsible whether this note's beam data drives the beam
     */
    private void placeNote(Note note, double noteX, Clef clef, Stem stem,
                           boolean chordBeamed, boolean isStemResponsible, boolean isBeamDataResponsible) {
        double gap = options.staffLineGap();
        int staffStep = staffStep(note.pitch(), clef);
        double y = staffY + staffStep * (gap / 2.0);

        // Accidental: only an explicit displayed accidental is shown (may be
        // NATURAL). A non-zero chromatic alter on the pitch is not itself a
        // display signal - it just encodes the sounding pitch, and
        // well-formed MusicXML always carries an explicit <accidental> on
        // notes that should actually show one (see ScoreAnalysis.hasAccidental).
        Accidental acc = note.displayedAccidental().orElse(null);
        if (acc != null) {
            Glyph accGlyph = accidentalGlyph(acc);
            if (accGlyph != null) {
                glyphs.add(new GlyphPlacement(noteX - gap * 1.5, y, accGlyph, staffStep, MarkingCategory.NOTE, note));
            }
        }

        glyphs.add(new GlyphPlacement(noteX, y, noteheadGlyph(note.type()), staffStep, MarkingCategory.NOTE, note));

        // A chord shares one stem across all its notes, drawn once by
        // whichever note is stem-responsible (see placeChord); the other
        // stacked notes must not each draw their own independent stem. Its
        // length (stem.tipY) is precomputed by StemPlan from the true
        // pitch extreme across the whole beamed run so every note in the
        // run reaches the exact same beam height, and clears the staff when
        // that extreme sits on a ledger line.
        // Inset slightly from the notehead's exact geometric edge (rather
        // than sitting exactly tangent to it): a straight stem only touches
        // an elliptical notehead's true edge at one exact point, so any
        // *other* notehead the same stem passes through (the far side of a
        // chord, or a middle note in a flattened beam run) shows a visible
        // sliver of a gap above/below that single tangent point otherwise.
        double stemInset = noteheadHalfWidth(note.type(), gap) * 0.8;
        double stemX = stem.up() ? noteX + stemInset : noteX - stemInset;
        if (stem.present() && isStemResponsible) {
            stems.add(new StemPlacement(stemX, y, stem.tipY(), note));
        }

        placeArticulations(note, noteX, y, staffStep, stem.up());
        placeDots(note, noteX, staffStep);

        // Beam vs. flag handling. A stacked chord note that carries no
        // <beam> elements of its own (MusicXML only tags one representative
        // note per chord) is still beamed when any other note in the same
        // chord is - it just contributes nothing further here, since
        // whichever note actually carries the beam data drives the beam
        // for the whole group. That note is not necessarily the same one
        // that anchors the drawn stem line (isStemResponsible) - the two are
        // independent: one is a geometric choice, the other reflects
        // whatever member of the chord MusicXML happened to tag.
        boolean beamed = note.isBeamed() || chordBeamed;
        if (!beamed && stem.present() && isStemResponsible) {
            Glyph flag = flagGlyph(note.type(), stem.up());
            if (flag != null) {
                glyphs.add(new GlyphPlacement(stemX, stem.tipY(), flag, staffStep, MarkingCategory.NOTE, note));
            }
        } else if (note.isBeamed() && stem.present() && isBeamDataResponsible) {
            spans.beams(note.voice(), note.beams(), stemX, stem.tipY(), stem.up());
        }

        spans.tie(note, staffStep, noteX, y);
        spans.slurs(note, staffStep, noteX, y);

        // Track the stem/beam tip (not just the notehead) so a tuplet number
        // over a beamed run clears the beam itself, not just the noteheads -
        // the beam commonly reaches well above the highest notehead.
        double tupletTrackedY = stem.present() && stem.up() ? Math.min(stem.tipY(), y) : y;
        spans.tuplets(note.voice(), note.tuplets(), note.timeModification(), noteX, tupletTrackedY);
    }

    /**
     * Articulations (staccato, accent) sit on the side opposite the stem,
     * clear of the notehead. Bowing and ornament marks (up/down-bow,
     * roll) are conventionally always placed above the note regardless
     * of stem direction. Stacked outward one per mark when a note
     * carries more than one.
     */
    private void placeArticulations(Note note, double noteX, double y, int staffStep, boolean stemUp) {
        double gap = options.staffLineGap();
        int articulationIndex = 0;
        for (Articulation articulation : note.articulations()) {
            Glyph articulationGlyph = articulationGlyph(articulation);
            // Staccato/accent marks hug the notehead - just past its edge,
            // not the wide clearance the always-above ornaments need.
            double offset = gap * (0.8 + articulationIndex * 0.6);
            double articulationY;
            if (ScoreAnalysis.isAlwaysAbove(articulation)) {
                // Clear the staff top as well as the notehead itself (a low
                // note's own clearance may not reach above the staff lines).
                double aboveStaff = staffY - gap * (1.0 + articulationIndex);
                double aboveNote = y - gap * (1.0 + articulationIndex);
                articulationY = Math.min(aboveStaff, aboveNote);
            } else {
                articulationY = stemUp ? y + offset : y - offset;
            }
            glyphs.add(new GlyphPlacement(noteX, articulationY, articulationGlyph, staffStep, MarkingCategory.NOTE, note));
            articulationIndex++;
        }
    }

    /**
     * Augmentation dots, nudged up into the space when the note sits on a
     * line.
     */
    private void placeDots(Note note, double noteX, int staffStep) {
        double gap = options.staffLineGap();
        for (int i = 0; i < note.dots(); i++) {
            int dotStep = staffStep % 2 == 0 ? staffStep - 1 : staffStep;
            double dx = noteX + gap * 1.2 + i * gap * 0.6;
            double dy = staffY + dotStep * (gap / 2.0);
            glyphs.add(new GlyphPlacement(dx, dy, Glyph.AUG_DOT, dotStep, MarkingCategory.NOTE, note));
        }
    }

    /**
     * Lay out a note's grace-note run: small noteheads spaced immediately
     * before it, oldest furthest left, with a stem-top / beam line raked
     * between the first and last grace note.
     */
    private GraceNotePlacement buildGraceNotePlacement(Note note, double mainNoteX, Clef clef) {
        double gap = options.staffLineGap();
        List<Pitch> pitches = note.graceNotes();
        int count = pitches.size();
        double spacing = gap * MeasureColumns.GRACE_NOTE_SPACING_GAPS;
        List<Double> xs = new ArrayList<>(count);
        List<Double> ys = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            xs.add(mainNoteX - spacing * (count - i));
            double graceY = staffY + staffStep(pitches.get(i), clef) * (gap / 2.0);
            ys.add(graceY);
        }
        // Rake the stem-top/beam line between the first and last grace
        // note's own clearance, following the run's pitch contour instead
        // of sitting flat - matching how a beam over ordinary notes of
        // varying pitch is drawn.
        double stemLen = gap * GRACE_STEM_LENGTH_GAPS;
        double firstTop = ys.get(0) - stemLen;
        double lastTop = ys.get(count - 1) - stemLen;
        List<Double> stemTops = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            double t = count == 1 ? 0.0 : (double) i / (count - 1);
            stemTops.add(firstTop + (lastTop - firstTop) * t);
        }
        double mainNoteY = staffY + staffStep(note.pitch(), clef) * (gap / 2.0);
        return new GraceNotePlacement(xs, ys, stemTops, mainNoteX, mainNoteY);
    }
}
