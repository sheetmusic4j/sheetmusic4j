package com.sheetmusic4j.core.model;

/**
 * Graphical note/rest type as defined by MusicXML {@code <type>}.
 */
public enum NoteType {
    /** Maxima (largest note value). */
    MAXIMA("maxima"),
    /** Long note. */
    LONG("long"),
    /** Breve. */
    BREVE("breve"),
    /** Whole note. */
    WHOLE("whole"),
    /** Half note. */
    HALF("half"),
    /** Quarter note. */
    QUARTER("quarter"),
    /** Eighth note. */
    EIGHTH("eighth"),
    /** Sixteenth note. */
    SIXTEENTH("16th"),
    /** Thirty-second note. */
    THIRTY_SECOND("32nd"),
    /** Sixty-fourth note. */
    SIXTY_FOURTH("64th"),
    /** Hundred twenty-eighth note (smallest standard). */
    HUNDRED_TWENTY_EIGHTH("128th");

    private final String xmlValue;

    NoteType(String xmlValue) {
        this.xmlValue = xmlValue;
    }

    /** Returns the MusicXML string representation. */
    public String xmlValue() {
        return xmlValue;
    }

    /**
     * Parse a MusicXML note type string.
     *
     * @param value the MusicXML string value
     * @return the corresponding NoteType
     * @throws IllegalArgumentException if the value is not recognized
     */
    public static NoteType fromXml(String value) {
        for (NoteType type : values()) {
            if (type.xmlValue.equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown note type: " + value);
    }

    /**
     * The nominal number of quarter notes this type occupies (undotted).
     *
     * @return the quarter note value
     */
    public double quarterValue() {
        return switch (this) {
            case MAXIMA -> 32.0;
            case LONG -> 16.0;
            case BREVE -> 8.0;
            case WHOLE -> 4.0;
            case HALF -> 2.0;
            case QUARTER -> 1.0;
            case EIGHTH -> 0.5;
            case SIXTEENTH -> 0.25;
            case THIRTY_SECOND -> 0.125;
            case SIXTY_FOURTH -> 0.0625;
            case HUNDRED_TWENTY_EIGHTH -> 0.03125;
        };
    }

    /**
     * Determine the closest note type for a duration expressed in quarter notes.
     *
     * @param quarters the duration in quarter notes
     * @return the NoteType closest to the given duration
     */
    public static NoteType fromQuarterValue(double quarters) {
        NoteType best = QUARTER;
        double bestDiff = Double.MAX_VALUE;
        for (NoteType type : values()) {
            double diff = Math.abs(type.quarterValue() - quarters);
            if (diff < bestDiff) {
                bestDiff = diff;
                best = type;
            }
        }
        return best;
    }
}
