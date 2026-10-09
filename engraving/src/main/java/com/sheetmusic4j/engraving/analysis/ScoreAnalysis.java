package com.sheetmusic4j.engraving.analysis;

import java.util.ArrayList;
import java.util.List;

import com.sheetmusic4j.core.model.Accidental;
import com.sheetmusic4j.core.model.Articulation;
import com.sheetmusic4j.core.model.Chord;
import com.sheetmusic4j.core.model.Direction;
import com.sheetmusic4j.core.model.Harmony;
import com.sheetmusic4j.core.model.Measure;
import com.sheetmusic4j.core.model.MusicElement;
import com.sheetmusic4j.core.model.Note;
import com.sheetmusic4j.core.model.Part;
import com.sheetmusic4j.core.model.Rest;
import com.sheetmusic4j.core.model.Score;
import com.sheetmusic4j.core.model.DirectionType;
import com.sheetmusic4j.core.model.Placement;

/**
 * Geometry-free queries over the score model: what a score, measure or
 * element contains, independent of where anything gets drawn.
 */
public final class ScoreAnalysis {

    private ScoreAnalysis() {
    }

    /**
     * Whether the element consumes musical time. {@link Direction}s and
     * {@link Harmony} markers occupy zero time and are anchored to the x of
     * a neighbouring time-consuming element instead.
     */
    public static boolean isTimed(MusicElement element) {
        return !(element instanceof Direction) && !(element instanceof Harmony);
    }

    /**
     * Resolve a {@link Direction}'s effective placement. Explicit ABOVE/BELOW
     * wins; DEFAULT follows the type convention (words/metronome above,
     * dynamics below).
     */
    public static Placement resolvedPlacement(Direction direction) {
        Placement raw = direction.placement();
        if (raw != null && raw != Placement.DEFAULT) {
            return raw;
        }
        return direction.type() instanceof DirectionType.Dynamic || direction.type() instanceof DirectionType.Wedge
                ? Placement.BELOW
                : Placement.ABOVE;
    }

    /**
     * Whether an articulation is always drawn above the staff regardless of
     * stem direction (bowings and ornaments), as opposed to sitting on the
     * side opposite the stem.
     */
    public static boolean isAlwaysAbove(Articulation articulation) {
        return articulation == Articulation.DOWN_BOW
                || articulation == Articulation.UP_BOW
                || articulation == Articulation.ROLL;
    }

    /**
     * Whether any note in the score carries a grace-note run - drawn
     * above the note itself (see {@code StaffRowEngraver}), which needs its
     * own clearance below the title block.
     */
    public static boolean hasGraceNotes(Score score) {
        for (Part part : score.parts()) {
            for (Measure measure : part.measures()) {
                for (MusicElement element : measure.elements()) {
                    if (element instanceof Note note && !note.graceNotes().isEmpty()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Whether any note/rest in the score carries a tuplet marking - its
     * number is drawn above the tuplet run's highest stem (see
     * {@code SpanTracker.TUPLET_ABOVE_GAP}), which needs its own clearance
     * below the title block just like the other above-staff marks.
     */
    public static boolean hasTuplets(Score score) {
        for (Part part : score.parts()) {
            for (Measure measure : part.measures()) {
                for (MusicElement element : measure.elements()) {
                    if (element instanceof Note note && !note.tuplets().isEmpty()) {
                        return true;
                    }
                    if (element instanceof Rest rest && !rest.tuplets().isEmpty()) {
                        return true;
                    }
                    if (element instanceof Chord chord) {
                        for (Note note : chord.notes()) {
                            if (!note.tuplets().isEmpty()) {
                                return true;
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    /**
     * Whether any measure in the score carries a chord-symbol annotation.
     */
    public static boolean hasHarmony(Score score) {
        for (Part part : score.parts()) {
            for (Measure measure : part.measures()) {
                for (MusicElement element : measure.elements()) {
                    if (element instanceof Harmony) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Whether any note in the score carries an articulation that is always
     * drawn above the staff (see {@link #isAlwaysAbove}), regardless of stem
     * direction — such marks need extra clearance below the title block.
     */
    public static boolean hasAboveStaffArticulation(Score score) {
        for (Part part : score.parts()) {
            for (Measure measure : part.measures()) {
                for (MusicElement element : measure.elements()) {
                    List<Note> notes = element instanceof Chord chord ? chord.notes()
                            : element instanceof Note note ? List.of(note) : List.of();
                    for (Note note : notes) {
                        for (Articulation articulation : note.articulations()) {
                            if (isAlwaysAbove(articulation)) {
                                return true;
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    /**
     * For a multi-staff part, keep only elements that belong to the given
     * staff number. For a single-staff part (or elements with a default
     * staff assignment), all elements pass through.
     */
    public static List<MusicElement> filterElementsForStaff(List<MusicElement> elements, int staffNumber, int totalStaves) {
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

    public static int elementStaff(MusicElement element) {
        if (element instanceof Note note) {
            return note.staff();
        }
        if (element instanceof Chord chord) {
            for (Note note : chord.notes()) {
                return note.staff();
            }
        }
        if (element instanceof Rest rest) {
            return rest.staff();
        }
        // Directions carry no explicit staff assignment; default to staff 1.
        return 1;
    }

    /**
     * Extract the lyric-carrying note from a measure element. For a plain
     * {@link Note} that is the note itself; for a {@link Chord} MusicXML
     * convention puts lyrics on the primary (first) note; rests do not
     * carry lyrics.
     */
    public static Note lyricCarrier(MusicElement element) {
        if (element instanceof Note note) {
            return note;
        }
        if (element instanceof Chord chord && !chord.notes().isEmpty()) {
            return chord.notes().get(0);
        }
        return null;
    }

    /**
     * Whether the given element carries a displayed accidental. Only an
     * explicit {@link Note#displayedAccidental()} counts. A non-zero
     * chromatic {@code alter} on the pitch is <em>not</em> by itself a
     * signal to show an accidental glyph - {@code alter} just encodes the
     * sounding pitch (e.g. every B-flat in a piece has {@code alter=-1}
     * whether or not the key signature already implies the flat), and
     * well-formed MusicXML always carries an explicit {@code <accidental>}
     * element on notes that should actually display one. For a {@link Chord}
     * the check is true when any member note qualifies; rests never carry
     * accidentals.
     */
    public static boolean hasAccidental(MusicElement e) {
        if (e instanceof Note note) {
            return note.displayedAccidental().isPresent();
        }
        if (e instanceof Chord chord) {
            for (Note note : chord.notes()) {
                if (note.displayedAccidental().isPresent()) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Whether the element's displayed accidental is a double sharp/flat -
     * these draw almost twice as wide as the other accidentals and need
     * more reserved space, see
     * {@code HorizontalSpacing.DOUBLE_ACCIDENTAL_RESERVE_GAPS}.
     */
    public static boolean isDoubleAccidental(MusicElement e) {
        if (e instanceof Note note) {
            return isDouble(note.displayedAccidental().orElse(null));
        }
        if (e instanceof Chord chord) {
            for (Note note : chord.notes()) {
                if (isDouble(note.displayedAccidental().orElse(null))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isDouble(Accidental accidental) {
        return accidental == Accidental.DOUBLE_SHARP || accidental == Accidental.DOUBLE_FLAT;
    }

    /**
     * Number of augmentation dots on the element. For a {@link Chord} the
     * maximum across all member notes is returned.
     */
    public static int dotCount(MusicElement e) {
        if (e instanceof Note note) {
            return note.dots();
        }
        if (e instanceof Rest rest) {
            return rest.dots();
        }
        if (e instanceof Chord chord) {
            int max = 0;
            for (Note note : chord.notes()) {
                if (note.dots() > max) {
                    max = note.dots();
                }
            }
            return max;
        }
        return 0;
    }

    /**
     * Number of grace notes attached ahead of the element (only {@link Note}
     * carries these). Used to reserve enough of the note's own slot for the
     * grace-note run to fit within.
     */
    public static int graceNoteCount(MusicElement e) {
        return e instanceof Note note ? note.graceNotes().size() : 0;
    }
}
