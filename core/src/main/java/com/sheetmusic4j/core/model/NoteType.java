package com.sheetmusic4j.core.model;

/**
 * Graphical note/rest type as defined by MusicXML {@code <type>}.
 */
public enum NoteType {
    MAXIMA("maxima"),
    LONG("long"),
    BREVE("breve"),
    WHOLE("whole"),
    HALF("half"),
    QUARTER("quarter"),
    EIGHTH("eighth"),
    SIXTEENTH("16th"),
    THIRTY_SECOND("32nd"),
    SIXTY_FOURTH("64th"),
    HUNDRED_TWENTY_EIGHTH("128th");

    private final String xmlValue;

    NoteType(String xmlValue) {
        this.xmlValue = xmlValue;
    }

    public String xmlValue() {
        return xmlValue;
    }

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
