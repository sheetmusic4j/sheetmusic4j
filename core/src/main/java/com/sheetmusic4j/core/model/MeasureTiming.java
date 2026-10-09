package com.sheetmusic4j.core.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * When each element of a {@link Measure} starts, measured exactly in quarter
 * notes from the start of the measure.
 *
 * <p>A measure's elements are stored in document order, possibly
 * interleaving several staves and voices. Each (staff, voice) pair is its own
 * timeline: its elements follow one another, while different voices start
 * together at the beginning of the measure. Readers fill any gap before a
 * voice that enters mid-measure with an invisible {@link Rest}, so summing
 * per voice is exact. Zero-duration elements ({@link Direction},
 * {@link Harmony}) are placed at the onset of the next time-consuming
 * element in document order.
 */
public final class MeasureTiming {

    /**
     * An element together with its onset within the measure.
     */
    public record Timed(MusicElement element, Fraction onset) {

        public Fraction end() {
            return onset.plus(element.duration().quarters());
        }
    }

    /**
     * Identifies one voice timeline within a part.
     */
    public record VoiceKey(int staff, int voice) {
    }

    private final List<Timed> elements;
    private final Fraction duration;

    private MeasureTiming(List<Timed> elements, Fraction duration) {
        this.elements = elements;
        this.duration = duration;
    }

    public static MeasureTiming of(Measure measure) {
        List<MusicElement> source = measure.elements();
        Fraction[] onsets = new Fraction[source.size()];
        Map<VoiceKey, Fraction> cursors = new HashMap<>();
        Fraction duration = Fraction.ZERO;
        List<Integer> pendingUntimed = new ArrayList<>();
        Fraction lastEnd = Fraction.ZERO;
        for (int i = 0; i < source.size(); i++) {
            MusicElement element = source.get(i);
            if (!isTimed(element)) {
                pendingUntimed.add(i);
                continue;
            }
            VoiceKey key = voiceKey(element);
            Fraction onset = cursors.getOrDefault(key, Fraction.ZERO);
            onsets[i] = onset;
            for (int pending : pendingUntimed) {
                onsets[pending] = onset;
            }
            pendingUntimed.clear();
            lastEnd = onset.plus(element.duration().quarters());
            cursors.put(key, lastEnd);
            duration = Fraction.max(duration, lastEnd);
        }
        for (int pending : pendingUntimed) {
            onsets[pending] = lastEnd;
        }
        List<Timed> timed = new ArrayList<>(source.size());
        for (int i = 0; i < source.size(); i++) {
            timed.add(new Timed(source.get(i), onsets[i]));
        }
        return new MeasureTiming(List.copyOf(timed), duration);
    }

    /**
     * Every element of the measure with its onset, in document order.
     */
    public List<Timed> elements() {
        return elements;
    }

    /**
     * Onset of the element at {@code index} in {@link Measure#elements()}.
     */
    public Fraction onset(int index) {
        return elements.get(index).onset();
    }

    /**
     * The measure's length: the latest end of any voice, or zero for a
     * measure without time-consuming elements.
     */
    public Fraction duration() {
        return duration;
    }

    /**
     * Whether the element consumes time (a note, rest or chord), as opposed
     * to a zero-duration marking (direction, harmony).
     */
    public static boolean isTimed(MusicElement element) {
        return element instanceof Note || element instanceof Rest || element instanceof Chord;
    }

    /**
     * The (staff, voice) timeline an element belongs to. Directions and
     * harmony, which carry neither, map to staff 1, voice 1.
     */
    public static VoiceKey voiceKey(MusicElement element) {
        return switch (element) {
            case Note note -> new VoiceKey(note.staff(), note.voice());
            case Rest rest -> new VoiceKey(rest.staff(), rest.voice());
            case Chord chord -> new VoiceKey(chord.staff(), chord.voice());
            default -> new VoiceKey(1, 1);
        };
    }
}
