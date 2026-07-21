package com.sheetmusic4j.core.model;

import java.util.Locale;

/**
 * A dynamic marking as expressed in MusicXML {@code <dynamics>}. Only the
 * common set of marks is enumerated; other MusicXML dynamic children (e.g.
 * {@code sffz}, {@code fp}, {@code rf}) not listed here are ignored by the
 * reader.
 */
public enum DynamicMark {
    PPP("ppp"),
    PP("pp"),
    P("p"),
    MP("mp"),
    MF("mf"),
    F("f"),
    FF("ff"),
    FFF("fff"),
    SF("sf"),
    SFZ("sfz"),
    FZ("fz"),
    FP("fp"),
    RF("rf"),
    RFZ("rfz"),
    N("n");

    private final String xmlValue;

    DynamicMark(String xmlValue) {
        this.xmlValue = xmlValue;
    }

    /**
     * MusicXML element name of this dynamic (lowercase).
     */
    public String xmlValue() {
        return xmlValue;
    }

    /**
     * Parse a MusicXML dynamic child element name to a {@link DynamicMark}.
     * Returns {@code null} for unknown / unsupported dynamic markings.
     */
    public static DynamicMark fromXml(String value) {
        if (value == null) {
            return null;
        }
        String lower = value.trim().toLowerCase(Locale.ROOT);
        for (DynamicMark mark : values()) {
            if (mark.xmlValue.equals(lower)) {
                return mark;
            }
        }
        return null;
    }
}
