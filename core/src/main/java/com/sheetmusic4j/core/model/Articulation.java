package com.sheetmusic4j.core.model;

/**
 * A performance articulation, technical, or ornament mark attached to a
 * {@link Note} (MusicXML {@code <notations>} children: {@code
 * <articulations>}, {@code <technical>}, {@code <ornaments>}).
 */
public enum Articulation {
    STACCATO,
    ACCENT,
    /**
     * Down-bow indication (MusicXML {@code <technical><down-bow>}; ABC {@code v}).
     */
    DOWN_BOW,
    /**
     * Up-bow indication (MusicXML {@code <technical><up-bow>}; ABC {@code u}).
     */
    UP_BOW,
    /**
     * Irish roll ornament (MusicXML {@code <ornaments><turn>}; ABC {@code ~}).
     */
    ROLL
}
