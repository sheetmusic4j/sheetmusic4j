package com.sheetmusic4j.core.lilypond;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import com.sheetmusic4j.core.model.KeySignature;
import com.sheetmusic4j.core.model.Step;

/**
 * LilyPond note names used by {@link LilyPondReader} and {@link LilyPondWriter}.
 *
 * <p>LilyPond spells every pitch out in full: {@code b} is always B natural and
 * {@code bes} always B flat, whatever the key signature says. The reader looks
 * whole words up in the table of the active input language (Dutch by default,
 * switched with {@code \language "..."} or {@code \include "deutsch.ly"}); the
 * writer always emits Dutch names.
 */
final class LilyPondPitch {

    /** The default input language. */
    static final String DEFAULT_LANGUAGE = "nederlands";

    private static final Map<String, Map<String, StepAlter>> LANGUAGES = buildLanguages();

    private LilyPondPitch() {
    }

    /** A step + chromatic alteration pair. */
    record StepAlter(Step step, int alter) {
    }

    /**
     * The pitch a whole word names in the given language, or {@code null}
     * when the word is not a note name there.
     */
    static StepAlter lookup(String word, String language) {
        Map<String, StepAlter> table = LANGUAGES.get(language);
        if (table == null) {
            table = LANGUAGES.get(DEFAULT_LANGUAGE);
        }
        return table.get(word);
    }

    /**
     * Normalise a language name, as given to {@code \language} or as the base
     * name of an included language file ({@code "deutsch.ly"}), or return
     * {@code null} when it is not a known language.
     */
    static String language(String name) {
        if (name == null) {
            return null;
        }
        String key = name.trim().toLowerCase(Locale.ROOT);
        if (key.endsWith(".ly")) {
            key = key.substring(0, key.length() - 3);
        }
        int slash = key.lastIndexOf('/');
        if (slash >= 0) {
            key = key.substring(slash + 1);
        }
        key = switch (key) {
            case "dutch" -> "nederlands";
            case "german" -> "deutsch";
            case "italian" -> "italiano";
            case "french", "français" -> "francais";
            case "spanish", "español" -> "espanol";
            case "português" -> "portugues";
            case "català" -> "catalan";
            default -> key;
        };
        return LANGUAGES.containsKey(key) ? key : null;
    }

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
     * Render a key signature as a {@code \key} argument (tonic + mode). Only
     * major tonics are emitted — the reader does not care about the mode.
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

    private static Map<String, Map<String, StepAlter>> buildLanguages() {
        Map<String, Map<String, StepAlter>> languages = new HashMap<>();
        String[] letters = {"c", "d", "e", "f", "g", "a", "b"};
        String[] solfege = {"do", "re", "mi", "fa", "sol", "la", "si"};

        Map<String, StepAlter> nl = suffixed(letters, new String[]{"isis", "is", "", "es", "eses"});
        nl.put("as", new StepAlter(Step.A, -1));
        nl.put("ases", new StepAlter(Step.A, -2));
        nl.put("es", new StepAlter(Step.E, -1));
        nl.put("eses", new StepAlter(Step.E, -2));
        languages.put("nederlands", nl);

        Map<String, StepAlter> de = new HashMap<>(nl);
        de.keySet().removeIf(name -> name.startsWith("b"));
        de.putAll(suffixed(new String[]{"c", "d", "e", "f", "g", "a", "h"}, new String[]{"isis", "is", ""}));
        de.put("b", new StepAlter(Step.B, -1));
        de.put("heses", new StepAlter(Step.B, -2));
        languages.put("deutsch", de);
        languages.put("suomi", de);

        Map<String, StepAlter> no = new HashMap<>(de);
        no.putAll(suffixed(new String[]{"c", "d", "e", "f", "g", "a", "h"}, new String[]{"ississ", "iss", "", "ess", "essess"}));
        no.put("ess", new StepAlter(Step.E, -1));
        no.put("essess", new StepAlter(Step.E, -2));
        no.put("ass", new StepAlter(Step.A, -1));
        no.put("assess", new StepAlter(Step.A, -2));
        no.put("bes", new StepAlter(Step.B, -2));
        languages.put("norsk", no);
        languages.put("svenska", no);

        Map<String, StepAlter> en = suffixed(letters, new String[]{"ss", "s", "", "f", "ff"});
        en.putAll(suffixed(letters, new String[]{"x", "-sharpsharp", "-sharp", "", "-flat", "-flatflat"}));
        languages.put("english", en);

        Map<String, StepAlter> it = suffixed(solfege, new String[]{"dd", "d", "", "b", "bb"});
        languages.put("italiano", it);
        languages.put("catalan", it);

        Map<String, StepAlter> fr = new HashMap<>(it);
        fr.putAll(suffixed(new String[]{"do", "ré", "mi", "fa", "sol", "la", "si"},
                new String[]{"x", "dd", "d", "", "b", "bb"}));
        fr.putAll(suffixed(solfege, new String[]{"x"}));
        languages.put("francais", fr);

        Map<String, StepAlter> es = suffixed(solfege, new String[]{"ss", "x", "s", "", "b", "bb"});
        languages.put("espanol", es);
        languages.put("portugues", es);

        Map<String, StepAlter> vl = suffixed(solfege, new String[]{"kk", "k", "", "b", "bb"});
        languages.put("vlaams", vl);
        return Map.copyOf(languages);
    }

    /**
     * Every {@code name + suffix} combination, where a suffix's alteration
     * follows from its position around the empty (natural) suffix: each step
     * before it adds a sharp, each step after it a flat.
     */
    private static Map<String, StepAlter> suffixed(String[] names, String[] suffixes) {
        int natural = java.util.Arrays.asList(suffixes).indexOf("");
        Map<String, StepAlter> out = new HashMap<>();
        for (int s = 0; s < names.length; s++) {
            Step step = Step.values()[s];
            for (int k = 0; k < suffixes.length; k++) {
                int alter;
                if (natural < 0) {
                    alter = 2;
                } else if (k < natural) {
                    alter = Math.min(2, natural - k);
                } else {
                    alter = -(k - natural);
                }
                out.put(names[s] + suffixes[k], new StepAlter(step, alter));
            }
        }
        return out;
    }
}
