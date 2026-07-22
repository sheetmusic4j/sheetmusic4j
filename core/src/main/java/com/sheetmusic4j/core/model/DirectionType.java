package com.sheetmusic4j.core.model;

/**
 * The concrete payload of a {@link Direction}. Sealed to the subset of
 * MusicXML {@code <direction-type>} children currently modelled:
 * {@link Words}, {@link Metronome}, {@link Dynamic}, and {@link Rehearsal}.
 */
public sealed interface DirectionType
        permits DirectionType.Words, DirectionType.Metronome, DirectionType.Dynamic, DirectionType.Rehearsal,
        DirectionType.Wedge {

    /**
     * MusicXML {@code <wedge type="...">} values.
     */
    enum WedgeType {
        CRESCENDO,
        DIMINUENDO,
        STOP
    }

    /**
     * Free-form text direction (MusicXML {@code <words>}). Font style is
     * captured for future rendering; the current engraver ignores it.
     *
     * @param text   the string to display
     * @param italic whether {@code font-style="italic"} was set
     * @param bold   whether {@code font-weight="bold"} was set
     */
    record Words(String text, boolean italic, boolean bold) implements DirectionType {
        public Words {
            if (text == null) {
                text = "";
            }
        }
    }

    /**
     * A metronome mark (MusicXML {@code <metronome>}), typically rendered as
     * "beat-unit = per-minute" above the staff.
     *
     * @param beatUnit  the note type used as the beat unit (e.g. quarter)
     * @param dotted    whether the beat unit carries a dot
     * @param perMinute beats per minute
     */
    record Metronome(NoteType beatUnit, boolean dotted, int perMinute) implements DirectionType {
        public Metronome {
            if (beatUnit == null) {
                beatUnit = NoteType.QUARTER;
            }
        }
    }

    /**
     * A dynamic marking (MusicXML {@code <dynamics>}).
     *
     * @param mark the specific dynamic (e.g. {@code p}, {@code mf}, {@code ff})
     */
    record Dynamic(DynamicMark mark) implements DirectionType {
        public Dynamic {
            if (mark == null) {
                throw new IllegalArgumentException("Dynamic requires a mark");
            }
        }
    }

    /**
     * A rehearsal mark (MusicXML {@code <rehearsal>}), typically rendered as a
     * boxed, bold, uppercase label above the staff at the start of the
     * associated measure.
     *
     * <p>The {@code label} is the trimmed inner text of the source element
     * (e.g. {@code "A"}, {@code "12"}, {@code "Verse 2"}). MusicXML lets the
     * source choose the exact spelling; the engraver applies boxing and font
     * conventions on top.
     *
     * @param label the label text to display (never {@code null})
     */
    record Rehearsal(String label) implements DirectionType {
        public Rehearsal {
            if (label == null) {
                label = "";
            }
        }
    }

    /**
     * A crescendo/diminuendo hairpin endpoint (MusicXML {@code <wedge
     * type="crescendo|diminuendo|stop" number="N"/>}). {@code number}
     * disambiguates concurrent wedges the same way {@link Slur#number()}
     * disambiguates concurrent slurs; a {@code STOP} closes the most recent
     * unmatched {@code CRESCENDO}/{@code DIMINUENDO} with the same number.
     *
     * @param type   which edge of the hairpin this marks
     * @param number 1-based wedge id matching a start to its stop
     */
    record Wedge(WedgeType type, int number) implements DirectionType {
        public Wedge {
            if (type == null) {
                throw new IllegalArgumentException("Wedge requires a type");
            }
            if (number < 1) {
                throw new IllegalArgumentException("wedge number must be >= 1, got " + number);
            }
        }
    }
}
