package com.sheetmusic4j.engraving.staff;

import java.util.List;

import com.sheetmusic4j.core.model.Beam;
import com.sheetmusic4j.core.model.Chord;
import com.sheetmusic4j.core.model.Clef;
import com.sheetmusic4j.core.model.MusicElement;
import com.sheetmusic4j.core.model.Note;
import com.sheetmusic4j.engraving.layout.LayoutOptions;

import static com.sheetmusic4j.engraving.staff.StaffGeometry.MIDDLE_LINE_STAFF_STEP;
import static com.sheetmusic4j.engraving.staff.StaffGeometry.hasStemForType;
import static com.sheetmusic4j.engraving.staff.StaffGeometry.staffStep;

/**
 * Stem direction and tip for every time-consuming element of one measure on
 * one staff, indexed parallel to that element list.
 *
 * <p>Elements are first given their own {@link #naturalStem} independently;
 * then every level-1 beam run (start..end) is flattened to share one
 * direction and one tip - the extreme across every note the whole run
 * spans, not just each note's own pitch - so every stem in the run reaches
 * the identical beam height instead of each independently falling short of
 * or overshooting it.
 */
public final class StemPlan {

    private static final double STEM_LENGTH_GAPS = 3.5;

    private final double[] tipY;
    private final boolean[] stemUp;
    private final boolean[] hasStem;

    private StemPlan(double[] tipY, boolean[] stemUp, boolean[] hasStem) {
        this.tipY = tipY;
        this.stemUp = stemUp;
        this.hasStem = hasStem;
    }

    /**
     * Stem of one element.
     *
     * @param present whether the element has a stem at all
     * @param up      stem direction (meaningless when not present)
     * @param tipY    y of the stem tip / beam (meaningless when not present)
     */
    public record Stem(boolean present, boolean up, double tipY) {
    }

    /**
     * Stem of the {@code i}-th element of the list the plan was computed for.
     */
    public Stem stem(int i) {
        return new Stem(hasStem[i], stemUp[i], tipY[i]);
    }

    /**
     * The highest point (smallest y) any up-stem or its beam reaches,
     * or {@code staffY} when nothing reaches above the staff top. Lets an
     * ABOVE-placed direction (a tempo marking, say) be pushed clear of it
     * instead of using a fixed offset from the staff - beams above notes
     * with several ledger lines can reach well above a "normal" fixed
     * clearance would assume.
     */
    public double topY(double staffY) {
        double top = staffY;
        for (int i = 0; i < tipY.length; i++) {
            if (hasStem[i] && stemUp[i]) {
                top = Math.min(top, tipY[i]);
            }
        }
        return top;
    }

    public static StemPlan compute(List<MusicElement> elements, Clef clef, double staffY, LayoutOptions options) {
        double[] outTipY = new double[elements.size()];
        boolean[] outStemUp = new boolean[elements.size()];
        boolean[] outHasStem = new boolean[elements.size()];
        for (int i = 0; i < elements.size(); i++) {
            NaturalStem ns = naturalStem(elements.get(i), staffY, clef, options);
            outHasStem[i] = ns != null;
            if (ns != null) {
                outStemUp[i] = ns.stemUp();
                outTipY[i] = ns.tipY();
            }
        }
        int runStart = -1;
        for (int i = 0; i < elements.size(); i++) {
            Beam.State state = beamLevelState(elements.get(i), 1);
            if (state == null) {
                continue;
            }
            if (state == Beam.State.BEGIN) {
                runStart = i;
            } else if (state == Beam.State.END && runStart >= 0) {
                boolean up = outStemUp[runStart];
                double extreme = outTipY[runStart];
                for (int j = runStart; j <= i; j++) {
                    if (!outHasStem[j]) {
                        continue;
                    }
                    extreme = up ? Math.min(extreme, outTipY[j]) : Math.max(extreme, outTipY[j]);
                }
                for (int j = runStart; j <= i; j++) {
                    outTipY[j] = extreme;
                    outStemUp[j] = up;
                }
                runStart = -1;
            }
        }
        return new StemPlan(outTipY, outStemUp, outHasStem);
    }

    /**
     * A note/chord's stem direction and tip, computed from its own pitch(es)
     * alone - before any beam-run-wide sharing is applied.
     */
    private record NaturalStem(boolean stemUp, double tipY) {
    }

    /**
     * Direction and length a single note or chord's stem would have in
     * isolation, or {@code null} for elements with no stem (rests,
     * whole/breve/long/maxima notes): explicit {@code <stem>} wins when
     * present (checking every note of a chord, since MusicXML normally
     * repeats the same value on each), otherwise the pitch farthest from
     * the middle line decides. The tip is measured from the pitch
     * <em>extreme</em> in the stem direction - the highest notehead for
     * stem-up, lowest for stem-down - across every note of a chord, not
     * just one, so it's guaranteed to clear every notehead the stem passes
     * through.
     */
    private static NaturalStem naturalStem(MusicElement element, double staffY, Clef clef, LayoutOptions options) {
        double gap = options.staffLineGap();
        if (element instanceof Note note) {
            if (!hasStemForType(note.type())) {
                return null;
            }
            int step = staffStep(note.pitch(), clef);
            double y = staffY + step * (gap / 2.0);
            boolean stemUp = note.stemUp().orElse(step >= MIDDLE_LINE_STAFF_STEP);
            double tipY = stemUp ? y - gap * STEM_LENGTH_GAPS : y + gap * STEM_LENGTH_GAPS;
            return new NaturalStem(stemUp, tipY);
        }
        if (element instanceof Chord chord) {
            List<Note> notes = chord.notes();
            if (notes.isEmpty() || !hasStemForType(notes.get(0).type())) {
                return null;
            }
            Note outerNote = notes.get(0);
            int outerDist = -1;
            Boolean explicitStemUp = null;
            double minY = Double.MAX_VALUE;
            double maxY = -Double.MAX_VALUE;
            for (Note note : notes) {
                int step = staffStep(note.pitch(), clef);
                double noteY = staffY + step * (gap / 2.0);
                minY = Math.min(minY, noteY);
                maxY = Math.max(maxY, noteY);
                int dist = Math.abs(step - MIDDLE_LINE_STAFF_STEP);
                if (dist > outerDist) {
                    outerDist = dist;
                    outerNote = note;
                }
                if (explicitStemUp == null && note.stemUp().isPresent()) {
                    explicitStemUp = note.stemUp().get();
                }
            }
            boolean stemUp = explicitStemUp != null
                    ? explicitStemUp
                    : staffStep(outerNote.pitch(), clef) >= MIDDLE_LINE_STAFF_STEP;
            double tipY = stemUp ? minY - gap * STEM_LENGTH_GAPS : maxY + gap * STEM_LENGTH_GAPS;
            return new NaturalStem(stemUp, tipY);
        }
        return null;
    }

    /**
     * The given beam level's state on an element, or {@code null} if it
     * carries no such level (including rests, and chord members other than
     * whichever one MusicXML put the {@code <beam>} elements on).
     */
    private static Beam.State beamLevelState(MusicElement element, int level) {
        List<Beam> beamList;
        if (element instanceof Note note) {
            beamList = note.beams();
        } else if (element instanceof Chord chord) {
            beamList = List.of();
            for (Note note : chord.notes()) {
                if (!note.beams().isEmpty()) {
                    beamList = note.beams();
                    break;
                }
            }
        } else {
            return null;
        }
        for (Beam b : beamList) {
            if (b.number() == level) {
                return b.state();
            }
        }
        return null;
    }
}
