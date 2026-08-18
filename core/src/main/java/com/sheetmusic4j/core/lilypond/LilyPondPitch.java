package com.sheetmusic4j.core.lilypond;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import com.sheetmusic4j.core.model.KeySignature;
import com.sheetmusic4j.core.model.Step;

/**
 * Dutch LilyPond pitch-name lookup used by {@link LilyPondReader} and
 * {@link LilyPondWriter}. Recognises the standard Dutch note names ({@code c
 * d e f g a b}) with the usual {@code -is}/{@code -es} accidental suffixes
 * (including the double variants {@code -isis}/{@code -eses}) and the short
 * flat forms {@code as}/{@code es} preferred by many editors.
 *
 * <p>Order of sharps / flats mirrors {@code AbcKey}: {@link #alterFor} lets
 * the reader resolve unaccidental note letters against a key signature
 * exactly the same way ABC does.
 */
final class LilyPondPitch {

    /** Order of sharps in a key signature: F, C, G, D, A, E, B. */
    private static final Step[] SHARP_ORDER = {Step.F, Step.C, Step.G, Step.D, Step.A, Step.E, Step.B};
    /** Order of flats in a key signature: B, E, A, D, G, C, F. */
    private static final Step[] FLAT_ORDER = {Step.B, Step.E, Step.A, Step.D, Step.G, Step.C, Step.F};

    /**
     * Longest-first list of pitch-name tokens ↔ {@code (Step, alter)} so that
     * the reader can match the accidental suffix greedily (e.g. {@code cis}
     * must win over {@code c}, {@code cisis} over {@code cis}). Insertion
     * order matters — never re-sort this map.
     */
    private static final Map<String, StepAlter> NAME_TO_STEP = buildNameTable();

    private LilyPondPitch() {
    }

    /** A step + chromatic alteration pair. */
    record StepAlter(Step step, int alter) {
    }

    /** Result of matching a pitch name against the front of an input. */
    record Parsed(Step step, int alter, int consumed) {
    }

    /**
     * Attempt to match a pitch-name token at the start of {@code input} from
     * position {@code pos}. Returns {@code null} when no known pitch name
     * begins there. Matching is greedy: longer names win over shorter
     * prefixes.
     */
    static Parsed parse(String input, int pos) {
        String best = null;
        StepAlter bestSa = null;
        int len = input.length();
        // Names are all lowercase ASCII letters — bail early on anything else.
        if (pos >= len || input.charAt(pos) < 'a' || input.charAt(pos) > 'g') {
            return null;
        }
        for (Map.Entry<String, StepAlter> entry : NAME_TO_STEP.entrySet()) {
            String name = entry.getKey();
            int end = pos + name.length();
            if (end > len) {
                continue;
            }
            // Match against the character run [pos, end) only when the
            // following character isn't itself another lowercase letter that
            // could form a longer name we'd otherwise miss.
            if (input.regionMatches(pos, name, 0, name.length())) {
                if (best == null || name.length() > best.length()) {
                    best = name;
                    bestSa = entry.getValue();
                }
            }
        }
        if (best == null) {
            return null;
        }
        return new Parsed(bestSa.step(), bestSa.alter(), best.length());
    }

    /**
     * The Dutch pitch-name for the given step and alteration, e.g.
     * {@code (D, 1) -> "dis"}, {@code (B, -2) -> "beses"}. Only the twelve
     * chromatic pitches ± double accidentals are covered; unknown
     * combinations fall back to the natural letter.
     */
    static String nameOf(Step step, int alter) {
        String letter = step.name().toLowerCase(Locale.ROOT);
        return switch (alter) {
            case -2 -> letter + "eses";
            case -1 -> letter + "es";
            case 0 -> letter;
            case 1 -> letter + "is";
            case 2 -> letter + "isis";
            default -> letter;
        };
    }

    /**
     * The chromatic alteration ({@code +1}, {@code 0}, {@code -1}) implied by
     * the given key signature for the given diatonic step.
     */
    static int alterFor(Step step, KeySignature key) {
        if (key == null || key.fifths() == 0) {
            return 0;
        }
        if (key.fifths() > 0) {
            for (int i = 0; i < key.fifths() && i < SHARP_ORDER.length; i++) {
                if (SHARP_ORDER[i] == step) {
                    return 1;
                }
            }
            return 0;
        }
        for (int i = 0; i < -key.fifths() && i < FLAT_ORDER.length; i++) {
            if (FLAT_ORDER[i] == step) {
                return -1;
            }
        }
        return 0;
    }

    /**
     * Render a key signature as the argument to a LilyPond {@code \key}
     * directive, in the form {@code "<tonic> \major"}. The model doesn't
     * carry mode information for MVP, so this always picks the major
     * representative — matching how {@link com.sheetmusic4j.core.abc.AbcWriter}
     * picks a canonical major key name.
     */
    static String keyString(KeySignature key) {
        int fifths = key == null ? 0 : key.fifths();
        String tonic = switch (fifths) {
            case -7 -> "ces";
            case -6 -> "ges";
            case -5 -> "des";
            case -4 -> "aes";
            case -3 -> "ees";
            case -2 -> "bes";
            case -1 -> "f";
            case 0 -> "c";
            case 1 -> "g";
            case 2 -> "d";
            case 3 -> "a";
            case 4 -> "e";
            case 5 -> "b";
            case 6 -> "fis";
            case 7 -> "cis";
            default -> "c";
        };
        return tonic + " \\major";
    }

    private static Map<String, StepAlter> buildNameTable() {
        // Insertion order matters: longest names first so a naïve linear
        // scan through the entries can just track the longest match seen.
        Map<String, StepAlter> map = new LinkedHashMap<>();
        // Double sharps / flats
        for (Step step : Step.values()) {
            String letter = step.name().toLowerCase(Locale.ROOT);
            map.put(letter + "isis", new StepAlter(step, 2));
            map.put(letter + "eses", new StepAlter(step, -2));
        }
        // Common short forms for double flats: "ases", "eses" (already added),
        // "eeses" for E double flat is standard.
        map.put("eeses", new StepAlter(Step.E, -2));
        map.put("aeses", new StepAlter(Step.A, -2));
        // Sharps / flats
        for (Step step : Step.values()) {
            String letter = step.name().toLowerCase(Locale.ROOT);
            map.put(letter + "is", new StepAlter(step, 1));
            map.put(letter + "es", new StepAlter(step, -1));
        }
        // Common short forms for flats: "as" == a-flat, "es" == e-flat.
        map.put("as", new StepAlter(Step.A, -1));
        map.put("aes", new StepAlter(Step.A, -1));
        map.put("ees", new StepAlter(Step.E, -1));
        // Naturals
        for (Step step : Step.values()) {
            String letter = step.name().toLowerCase(Locale.ROOT);
            map.putIfAbsent(letter, new StepAlter(step, 0));
        }
        return map;
    }
}
