package com.sheetmusic4j.engraving.staff;

import com.sheetmusic4j.core.model.Clef;
import com.sheetmusic4j.core.model.ClefSign;
import com.sheetmusic4j.core.model.NoteType;
import com.sheetmusic4j.core.model.Pitch;
import com.sheetmusic4j.core.model.Step;

/**
 * Vertical staff geometry: where pitches, rests and clefs sit on a
 * five-line staff, expressed in staff steps (half staff spaces below the
 * top line, positive downward).
 */
public final class StaffGeometry {

    /**
     * Half-staff-step position of the middle staff line for a five-line staff.
     */
    public static final int MIDDLE_LINE_STAFF_STEP = 4;

    /**
     * Number of horizontal lines on a standard staff.
     */
    public static final int STAFF_LINES = 5;

    private StaffGeometry() {
    }

    /**
     * Vertical position of a pitch expressed in half staff spaces below the top
     * staff line (0 = top line, positive downward).
     */
    public static int staffStep(Pitch pitch, Clef clef) {
        int refStaffStep = 2 * (5 - clef.line());
        int refStepNumber = clefReferenceStepNumber(clef.sign());
        return refStaffStep + (refStepNumber - pitch.diatonicStepNumber());
    }

    private static int clefReferenceStepNumber(ClefSign sign) {
        return switch (sign) {
            case F -> new Pitch(Step.F, 3).diatonicStepNumber();
            case C -> new Pitch(Step.C, 4).diatonicStepNumber();
            default -> new Pitch(Step.G, 4).diatonicStepNumber();
        };
    }

    /**
     * Vertical line index (0 = top line, 4 = bottom line) the given clef is
     * anchored on. MusicXML {@code <line>} is counted from the bottom (1-based),
     * so top-index = 5 - line.
     */
    public static int clefAnchorLineIndex(Clef clef) {
        int line = clef.line();
        if (line < 1) {
            line = 1;
        } else if (line > 5) {
            line = 5;
        }
        return 5 - line;
    }

    /**
     * Staff-step position at which a rest of the given type is anchored.
     */
    public static int restAnchorStaffStep(NoteType type) {
        return switch (type) {
            case WHOLE, BREVE, LONG, MAXIMA -> 2;
            case HALF -> 4;
            default -> 4;
        };
    }

    public static double noteheadHalfWidth(NoteType type, double gap) {
        return switch (type) {
            case WHOLE, BREVE, LONG, MAXIMA -> 0.75 * gap;
            default -> 0.59 * gap;
        };
    }

    /**
     * Whether a note of this type has a stem at all. Whole/breve/long/maxima
     * notes don't.
     */
    public static boolean hasStemForType(NoteType type) {
        return switch (type) {
            case WHOLE, BREVE, LONG, MAXIMA -> false;
            default -> true;
        };
    }
}
