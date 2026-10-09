package com.sheetmusic4j.engraving.analysis;

import java.util.ArrayList;
import java.util.List;

import com.sheetmusic4j.core.model.Attributes;
import com.sheetmusic4j.core.model.Clef;
import com.sheetmusic4j.core.model.Direction;
import com.sheetmusic4j.core.model.Harmony;
import com.sheetmusic4j.core.model.KeySignature;
import com.sheetmusic4j.core.model.Lyric;
import com.sheetmusic4j.core.model.Measure;
import com.sheetmusic4j.core.model.MusicElement;
import com.sheetmusic4j.core.model.Note;
import com.sheetmusic4j.core.model.Part;
import com.sheetmusic4j.core.model.Placement;
import com.sheetmusic4j.core.model.TimeSignature;

/**
 * Per-part immutable metadata plus a per-measure trace of the clef/key/time
 * signature state (the state effective from that measure onwards).
 */
public final class PartInfo {
    private final Part part;
    private final int staveCount;
    private final Clef[][] clefsPerMeasure; // [measureIndex][staffIndex]
    private final KeySignature[] keyPerMeasure;
    private final TimeSignature[] timePerMeasure;
    private final boolean hasLyrics;
    private final boolean[] hasDirectionsAbovePerMeasure;
    private final boolean hasDirectionsBelow;
    private final boolean[] hasHarmonyPerMeasure;

    private PartInfo(Part part, int staveCount,
                     Clef[][] clefs, KeySignature[] keys, TimeSignature[] times,
                     boolean hasLyrics,
                     boolean[] hasDirectionsAbovePerMeasure,
                     boolean hasDirectionsBelow,
                     boolean[] hasHarmonyPerMeasure) {
        this.part = part;
        this.staveCount = staveCount;
        this.clefsPerMeasure = clefs;
        this.keyPerMeasure = keys;
        this.timePerMeasure = times;
        this.hasLyrics = hasLyrics;
        this.hasDirectionsAbovePerMeasure = hasDirectionsAbovePerMeasure;
        this.hasDirectionsBelow = hasDirectionsBelow;
        this.hasHarmonyPerMeasure = hasHarmonyPerMeasure;
    }

    public boolean hasLyrics() {
        return hasLyrics;
    }

    public boolean hasDirectionsBelow() {
        return hasDirectionsBelow;
    }

    public boolean hasDirectionsAboveInRange(int start, int endExclusive) {
        int upper = Math.min(endExclusive, hasDirectionsAbovePerMeasure.length);
        for (int i = Math.max(0, start); i < upper; i++) {
            if (hasDirectionsAbovePerMeasure[i]) {
                return true;
            }
        }
        return false;
    }

    public boolean hasHarmonyInRange(int start, int endExclusive) {
        int upper = Math.min(endExclusive, hasHarmonyPerMeasure.length);
        for (int i = Math.max(0, start); i < upper; i++) {
            if (hasHarmonyPerMeasure[i]) {
                return true;
            }
        }
        return false;
    }

    public Part part() {
        return part;
    }

    public List<Measure> measures() {
        return part.measures();
    }

    public int staveCount() {
        return staveCount;
    }

    public Clef clefAt(int measureIdx, int staffIdx) {
        if (clefsPerMeasure.length == 0) {
            return Clef.treble();
        }
        int m = Math.min(measureIdx, clefsPerMeasure.length - 1);
        return clefsPerMeasure[m][Math.min(staffIdx, staveCount - 1)];
    }

    public KeySignature keyAt(int measureIdx) {
        if (keyPerMeasure.length == 0) {
            return KeySignature.cMajor();
        }
        return keyPerMeasure[Math.min(measureIdx, keyPerMeasure.length - 1)];
    }

    public TimeSignature timeAt(int measureIdx) {
        if (timePerMeasure.length == 0) {
            return null;
        }
        return timePerMeasure[Math.min(measureIdx, timePerMeasure.length - 1)];
    }

    /**
     * Whether the time signature effective at {@code measureIdx} differs
     * from the one effective at the previous measure. Used to decide
     * whether to re-emit the time signature at the start of a new system.
     */
    public boolean timeChangedAt(int measureIdx) {
        if (measureIdx <= 0 || timePerMeasure.length == 0) {
            return timeAt(measureIdx) != null;
        }
        TimeSignature previous = timePerMeasure[Math.min(measureIdx - 1, timePerMeasure.length - 1)];
        TimeSignature current = timeAt(measureIdx);
        if (previous == null) {
            return current != null;
        }
        if (current == null) {
            return false;
        }
        return previous.beats() != current.beats() || previous.beatType() != current.beatType();
    }

    public static PartInfo of(Part part) {
        int staveCount = determineStaveCount(part);
        List<Clef> defaults = defaultClefsFor(part, staveCount);

        int n = part.measures().size();
        Clef[][] clefs = new Clef[n][staveCount];
        KeySignature[] keys = new KeySignature[n];
        TimeSignature[] times = new TimeSignature[n];

        Clef[] currentClefs = new Clef[staveCount];
        for (int i = 0; i < staveCount; i++) {
            currentClefs[i] = defaults.get(i);
        }
        KeySignature currentKey = KeySignature.cMajor();
        TimeSignature currentTime = null;

        for (int idx = 0; idx < n; idx++) {
            Measure measure = part.measures().get(idx);
            if (measure.attributes().isPresent()) {
                Attributes a = measure.attributes().get();
                if (!a.clefs().isEmpty()) {
                    for (int i = 0; i < staveCount; i++) {
                        if (i < a.clefs().size()) {
                            currentClefs[i] = a.clefs().get(i);
                        } else if (a.clef().isPresent()) {
                            currentClefs[i] = a.clef().get();
                        }
                    }
                } else if (a.clef().isPresent()) {
                    for (int i = 0; i < staveCount; i++) {
                        currentClefs[i] = a.clef().get();
                    }
                }
                if (a.keySignature().isPresent()) {
                    currentKey = a.keySignature().get();
                }
                if (a.timeSignature().isPresent()) {
                    currentTime = a.timeSignature().get();
                }
            }
            for (int i = 0; i < staveCount; i++) {
                clefs[idx][i] = currentClefs[i];
            }
            keys[idx] = currentKey;
            times[idx] = currentTime;
        }
        boolean[] aboveMask = new boolean[n];
        boolean belowAny = false;
        boolean[] harmonyMask = new boolean[n];
        for (int idx = 0; idx < n; idx++) {
            Measure measure = part.measures().get(idx);
            for (MusicElement element : measure.elements()) {
                if (element instanceof Direction direction) {
                    Placement resolved = ScoreAnalysis.resolvedPlacement(direction);
                    if (resolved == Placement.ABOVE) {
                        aboveMask[idx] = true;
                    } else if (resolved == Placement.BELOW) {
                        belowAny = true;
                    }
                } else if (element instanceof Harmony) {
                    harmonyMask[idx] = true;
                }
            }
        }
        return new PartInfo(part, staveCount, clefs, keys, times, scanHasLyrics(part),
                aboveMask, belowAny, harmonyMask);
    }

    /**
     * Whether the part contains at least one verse-1 lyric with non-empty
     * text, on any note (including the primary member of a chord).
     */
    private static boolean scanHasLyrics(Part part) {
        for (Measure measure : part.measures()) {
            for (MusicElement element : measure.elements()) {
                Note note = ScoreAnalysis.lyricCarrier(element);
                if (note == null) {
                    continue;
                }
                for (Lyric lyric : note.lyrics()) {
                    if (lyric.verse() == 1 && !lyric.text().isEmpty()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Number of staves declared by the first measure of the part that carries an
     * {@code Attributes#staves()} value.
     */
    private static int determineStaveCount(Part part) {
        for (Measure measure : part.measures()) {
            if (measure.attributes().isPresent()) {
                Attributes attr = measure.attributes().get();
                if (attr.staves().isPresent()) {
                    return Math.max(1, attr.staves().get());
                }
            }
        }
        return 1;
    }

    /**
     * Initial clef per staff, taken from the first measure's clef list when
     * available and falling back to standard treble/bass grand-staff defaults.
     */
    private static List<Clef> defaultClefsFor(Part part, int staveCount) {
        for (Measure measure : part.measures()) {
            if (measure.attributes().isPresent()) {
                Attributes attr = measure.attributes().get();
                if (!attr.clefs().isEmpty()) {
                    List<Clef> clefs = new ArrayList<>(staveCount);
                    for (int i = 0; i < staveCount; i++) {
                        if (i < attr.clefs().size()) {
                            clefs.add(attr.clefs().get(i));
                        } else {
                            clefs.add(i == 0 ? Clef.treble() : Clef.bass());
                        }
                    }
                    return clefs;
                }
            }
        }
        List<Clef> defaults = new ArrayList<>(staveCount);
        for (int i = 0; i < staveCount; i++) {
            defaults.add(i == 0 ? Clef.treble() : Clef.bass());
        }
        return defaults;
    }
}
