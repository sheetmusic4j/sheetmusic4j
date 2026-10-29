package com.sheetmusic4j.pdf.symbol;

import java.util.Map;
import java.util.Optional;

import com.sheetmusic4j.core.model.DynamicMark;
import com.sheetmusic4j.pdf.extract.PageContent.Glyph;

/**
 * Maps glyphs of the supported notation fonts to {@link Symbol}s. Every notation program has its own font and
 * character layout, so each gets one table:
 * <ul>
 *   <li><b>Maestro</b> (Finale): characters in the Mac Roman layout, identified by their Unicode text;</li>
 *   <li><b>Emmentaler</b> (LilyPond): named glyphs such as {@code noteheads.s2}.</li>
 * </ul>
 */
public final class MusicFonts {

    private static final Map<String, Symbol> MAESTRO = Map.ofEntries(
            Map.entry("&", Symbol.clef(Symbol.G_CLEF)),
            Map.entry("?", Symbol.clef(Symbol.F_CLEF)),
            Map.entry("B", Symbol.clef(Symbol.C_CLEF)),
            Map.entry("#", Symbol.accidental(1)),
            Map.entry("b", Symbol.accidental(-1)),
            Map.entry("n", Symbol.accidental(0)),
            Map.entry("x", Symbol.accidental(2)),
            Map.entry("w", Symbol.head(Symbol.WHOLE)),
            Map.entry("˙", Symbol.head(Symbol.HALF)),
            Map.entry("œ", Symbol.head(Symbol.FILLED)),
            Map.entry("∑", Symbol.rest(0)),
            Map.entry("Ó", Symbol.rest(1)),
            Map.entry("Œ", Symbol.rest(2)),
            Map.entry("‰", Symbol.rest(3)),
            Map.entry("≈", Symbol.rest(4)),
            Map.entry(".", Symbol.dot()),
            Map.entry("J", Symbol.flag(1)),
            Map.entry("j", Symbol.flag(1)),
            Map.entry("K", Symbol.flag(2)),
            Map.entry("k", Symbol.flag(2)),
            Map.entry("L", Symbol.flag(3)),
            Map.entry("l", Symbol.flag(3)),
            Map.entry("p", Symbol.dynamic(DynamicMark.P)),
            Map.entry("f", Symbol.dynamic(DynamicMark.F)),
            Map.entry("\u03C0", Symbol.dynamic(DynamicMark.PP)),
            Map.entry("P", Symbol.dynamic(DynamicMark.MP)),
            Map.entry("F", Symbol.dynamic(DynamicMark.MF)),
            Map.entry("S", Symbol.dynamic(DynamicMark.SF)),
            Map.entry("\u00CD", Symbol.dynamic(DynamicMark.FF)),
            Map.entry("c", new Symbol(Symbol.Kind.TIME_COMMON, 0)),
            Map.entry("C", new Symbol(Symbol.Kind.TIME_CUT, 0)));

    private static final String[] DIGIT_NAMES =
            {"zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine"};

    private MusicFonts() {
    }

    /**
     * Whether a font is one of the supported notation fonts.
     */
    public static boolean isMusicFont(String fontName) {
        return fontName.startsWith("Maestro") || fontName.startsWith("Emmentaler");
    }

    /**
     * The symbol a glyph stands for, or empty for text, other symbols and fonts that are not supported.
     */
    public static Optional<Symbol> classify(Glyph glyph) {
        if (glyph.font().startsWith("Maestro")) {
            String text = glyph.text();
            if (text == null) {
                return Optional.empty();
            }
            if (text.length() == 1 && Character.isDigit(text.charAt(0))) {
                return Optional.of(Symbol.digit(text.charAt(0) - '0'));
            }
            return Optional.ofNullable(MAESTRO.get(text));
        }
        if (glyph.font().startsWith("Emmentaler") && glyph.name() != null) {
            return emmentaler(glyph.name());
        }
        return Optional.empty();
    }

    private static Optional<Symbol> emmentaler(String name) {
        return Optional.ofNullable(switch (name) {
            case "clefs.G", "clefs.G_change" -> Symbol.clef(Symbol.G_CLEF);
            case "clefs.F", "clefs.F_change" -> Symbol.clef(Symbol.F_CLEF);
            case "clefs.C", "clefs.C_change" -> Symbol.clef(Symbol.C_CLEF);
            case "accidentals.sharp" -> Symbol.accidental(1);
            case "accidentals.flat" -> Symbol.accidental(-1);
            case "accidentals.natural" -> Symbol.accidental(0);
            case "accidentals.doublesharp" -> Symbol.accidental(2);
            case "accidentals.flatflat" -> Symbol.accidental(-2);
            case "noteheads.s0" -> Symbol.head(Symbol.WHOLE);
            case "noteheads.s1" -> Symbol.head(Symbol.HALF);
            case "noteheads.s2" -> Symbol.head(Symbol.FILLED);
            case "dots.dot" -> Symbol.dot();
            case "timesig.C44" -> new Symbol(Symbol.Kind.TIME_COMMON, 0);
            case "timesig.C22" -> new Symbol(Symbol.Kind.TIME_CUT, 0);
            default -> restFlagOrDigit(name);
        });
    }

    private static Symbol restFlagOrDigit(String name) {
        if (name.length() == 1 && "fpmszr".contains(name)) {
            return Symbol.dynamicLetter(name.charAt(0));
        }
        if (name.length() == 7 && name.startsWith("rests.") && Character.isDigit(name.charAt(6))) {
            return Symbol.rest(name.charAt(6) - '0');
        }
        if (name.length() == 8 && name.startsWith("flags.") && Character.isDigit(name.charAt(7))
                && (name.charAt(6) == 'u' || name.charAt(6) == 'd')) {
            return Symbol.flag(name.charAt(7) - '0' - 2);
        }
        for (int digit = 0; digit < DIGIT_NAMES.length; digit++) {
            if (DIGIT_NAMES[digit].equals(name)) {
                return Symbol.digit(digit);
            }
        }
        return null;
    }
}
