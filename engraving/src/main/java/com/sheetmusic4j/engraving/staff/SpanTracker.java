package com.sheetmusic4j.engraving.staff;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.sheetmusic4j.core.model.Beam;
import com.sheetmusic4j.core.model.Note;
import com.sheetmusic4j.core.model.Pitch;
import com.sheetmusic4j.core.model.Placement;
import com.sheetmusic4j.core.model.Slur;
import com.sheetmusic4j.core.model.TimeModification;
import com.sheetmusic4j.core.model.Tuplet;
import com.sheetmusic4j.engraving.placement.BeamPlacement;
import com.sheetmusic4j.engraving.placement.SlurPlacement;
import com.sheetmusic4j.engraving.placement.TiePlacement;
import com.sheetmusic4j.engraving.placement.TupletPlacement;

import static com.sheetmusic4j.engraving.staff.StaffGeometry.MIDDLE_LINE_STAFF_STEP;

/**
 * Connects placed notes across one staff row: beams, ties, slurs and tuplet
 * brackets. Each kind opens at one element and closes at a later one, so
 * the tracker keeps the open runs and emits a placement when a run closes.
 * Beams and tuplets are matched within their own voice, since MusicXML
 * numbers them per voice. Runs still open at the end of the row are
 * dropped.
 */
public final class SpanTracker {

    /**
     * Extra clearance (in staff-line gaps) above the highest notehead in a
     * tuplet run at which its number/bracket is drawn.
     */
    public static final double TUPLET_ABOVE_GAP = 2.0;

    private final double gap;

    private final List<BeamPlacement> beams = new ArrayList<>();
    private final List<TiePlacement> ties = new ArrayList<>();
    private final List<SlurPlacement> slurs = new ArrayList<>();
    private final List<TupletPlacement> tuplets = new ArrayList<>();

    private final Map<Integer, BeamRun> openBeams = new HashMap<>();
    private final Map<String, PlacedNote> tieCandidates = new HashMap<>();
    private final Map<Integer, SlurStart> slurCandidates = new HashMap<>();
    private final Map<Integer, TupletRun> tupletCandidates = new HashMap<>();

    public SpanTracker(double gap) {
        this.gap = gap;
    }

    public List<BeamPlacement> beams() {
        return beams;
    }

    public List<TiePlacement> ties() {
        return ties;
    }

    public List<SlurPlacement> slurs() {
        return slurs;
    }

    public List<TupletPlacement> tuplets() {
        return tuplets;
    }

    /**
     * Handle beam state transitions for a single note's beam entries.
     * Emits a {@link BeamPlacement} when a run of {@code BEGIN..CONTINUE*..END}
     * closes.
     */
    public void beams(int voice, List<Beam> noteBeams, double stemTipX, double stemTipY, boolean stemUp) {
        for (Beam beam : noteBeams) {
            int level = beam.number();
            switch (beam.state()) {
                case BEGIN -> openBeams.put(key(voice, level), new BeamRun(stemTipX, stemTipY, stemUp));
                case CONTINUE -> {
                    // no-op: run stays open
                }
                case END -> {
                    BeamRun run = openBeams.remove(key(voice, level));
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
     * Ties: if this note carries tieStart, remember it; if it carries
     * tieStop, close any tie candidate at the same pitch.
     */
    public void tie(Note note, int staffStep, double noteX, double y) {
        String key = pitchKey(note.pitch());
        if (note.tieStop()) {
            PlacedNote start = tieCandidates.remove(key);
            if (start != null) {
                // Opposite the stem side, same convention as articulations:
                // notes below the middle line stem up, so their tie curves
                // below; notes above stem down, so their tie curves above.
                boolean curveUp = staffStep < MIDDLE_LINE_STAFF_STEP;
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

    /**
     * Slurs: matched by number rather than pitch, since a slur spans a
     * phrase rather than a single repeated pitch. A slur can swing
     * through notes far higher/lower than its own start/stop pitch (an
     * arpeggio arching up to a peak and back down, say), so every open
     * slur's pitch extremes are tracked across the whole span - not just
     * its two endpoints - the same way tuplet runs track their highest
     * notehead, so the curve is guaranteed to clear everything it spans.
     */
    public void slurs(Note note, int staffStep, double noteX, double y) {
        for (SlurStart start : slurCandidates.values()) {
            start.minY = Math.min(start.minY, y);
            start.maxY = Math.max(start.maxY, y);
        }
        for (Slur slur : note.slurs()) {
            if (slur.type() == Slur.Type.STOP) {
                SlurStart start = slurCandidates.remove(slur.number());
                if (start != null) {
                    // Prefer the explicit MusicXML placement (set on the
                    // start note); only guess from stem direction when the
                    // source left it unspecified. Guessing unconditionally
                    // is wrong for a slur spanning notes of varying pitch -
                    // it can bend the curve back into the very notes it
                    // spans instead of arcing clear of them.
                    boolean curveUp = switch (start.placement) {
                        case ABOVE -> true;
                        case BELOW -> false;
                        // Opposite the stem side (see the tie curveUp above).
                        case DEFAULT -> staffStep < MIDDLE_LINE_STAFF_STEP;
                    };
                    double yShift = curveUp ? -gap * 0.8 : gap * 0.8;
                    double clearY = curveUp ? start.minY : start.maxY;
                    slurs.add(new SlurPlacement(start.x + gap * 0.5, start.y + yShift,
                            noteX - gap * 0.5, y + yShift, curveUp, clearY));
                }
            } else {
                slurCandidates.put(slur.number(), new SlurStart(noteX, y, slur.placement()));
            }
        }
    }

    /**
     * Track an open tuplet run across every element it spans (not just its
     * start/stop): updates the running minimum y for all currently-open
     * tuplets, then opens or closes a run per {@code elementTuplets}.
     */
    public void tuplets(int voice, List<Tuplet> elementTuplets, Optional<TimeModification> timeModification,
                        double x, double y) {
        for (TupletRun run : tupletCandidates.values()) {
            run.minY = Math.min(run.minY, y);
        }
        for (Tuplet t : elementTuplets) {
            if (t.type() == Tuplet.Type.START) {
                int actualNotes = timeModification.map(TimeModification::actualNotes).orElse(0);
                tupletCandidates.put(key(voice, t.number()), new TupletRun(x, y, actualNotes, t.bracket()));
            } else {
                TupletRun run = tupletCandidates.remove(key(voice, t.number()));
                if (run != null) {
                    int number = run.actualNotes > 0
                            ? run.actualNotes
                            : timeModification.map(TimeModification::actualNotes).orElse(3);
                    tuplets.add(new TupletPlacement(run.startX, x,
                            run.minY - gap * TUPLET_ABOVE_GAP, number, run.bracket));
                }
            }
        }
    }

    /**
     * Map key for a beam level or tuplet number within one voice.
     */
    private static int key(int voice, int number) {
        return voice * 1000 + number;
    }

    private static String pitchKey(Pitch pitch) {
        return pitch.step().name() + pitch.octave() + "@" + pitch.alter();
    }

    private record BeamRun(double startX, double startY, boolean stemUp) {
    }

    private record PlacedNote(double x, double y) {
    }

    /**
     * Mutable state for an open slur: its start x/y and placement are fixed,
     * but {@code minY}/{@code maxY} track the pitch extremes of every note
     * the slur spans (not just its start/stop), so the curve can be sized
     * to clear a peak or trough in the middle of the phrase.
     */
    private static final class SlurStart {
        final double x;
        final double y;
        final Placement placement;
        double minY;
        double maxY;

        SlurStart(double x, double y, Placement placement) {
            this.x = x;
            this.y = y;
            this.placement = placement;
            this.minY = y;
            this.maxY = y;
        }
    }

    /**
     * Mutable state for an open tuplet run: the x of its first element, the
     * displayed count captured at the start (falls back to the stop
     * element's own {@link TimeModification} if the start didn't carry
     * one), whether a bracket line should be drawn, and the running minimum
     * y seen across every element in the run so the number/bracket clears
     * the highest notehead.
     */
    private static final class TupletRun {
        final double startX;
        final int actualNotes;
        final boolean bracket;
        double minY;

        TupletRun(double startX, double minY, int actualNotes, boolean bracket) {
            this.startX = startX;
            this.minY = minY;
            this.actualNotes = actualNotes;
            this.bracket = bracket;
        }
    }
}
