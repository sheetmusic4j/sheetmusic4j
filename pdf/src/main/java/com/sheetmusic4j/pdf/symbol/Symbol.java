package com.sheetmusic4j.pdf.symbol;

/**
 * A music symbol recognised from a font glyph, independent of the font it came from.
 *
 * @param kind  what the symbol is
 * @param value kind-specific: clef sign, accidental alteration, head type ({@link #WHOLE}, {@link #HALF},
 *              {@link #FILLED}), rest note value exponent, flag count or digit
 */
public record Symbol(Kind kind, int value) {

    public static final int WHOLE = 0;
    public static final int HALF = 1;
    public static final int FILLED = 2;

    public enum Kind {
        CLEF, ACCIDENTAL, HEAD, REST, DOT, FLAG, TIME_COMMON, TIME_CUT, DIGIT, DYNAMIC, DYNAMIC_LETTER
    }

    public static final int G_CLEF = 0;
    public static final int F_CLEF = 1;
    public static final int C_CLEF = 2;

    /**
     * @param sign {@link #G_CLEF}, {@link #F_CLEF} or {@link #C_CLEF}
     */
    public static Symbol clef(int sign) {
        return new Symbol(Kind.CLEF, sign);
    }

    public static Symbol accidental(int alter) {
        return new Symbol(Kind.ACCIDENTAL, alter);
    }

    public static Symbol head(int type) {
        return new Symbol(Kind.HEAD, type);
    }

    /**
     * @param exponent {@code 0} whole, {@code 1} half, {@code 2} quarter, {@code 3} eighth, and so on
     */
    public static Symbol rest(int exponent) {
        return new Symbol(Kind.REST, exponent);
    }

    public static Symbol dot() {
        return new Symbol(Kind.DOT, 0);
    }

    public static Symbol flag(int count) {
        return new Symbol(Kind.FLAG, count);
    }

    /**
     * A whole dynamic marking such as {@code mf}, drawn as one glyph.
     */
    public static Symbol dynamic(com.sheetmusic4j.core.model.DynamicMark mark) {
        return new Symbol(Kind.DYNAMIC, mark.ordinal());
    }

    /**
     * One letter of a dynamic marking that is drawn letter by letter ({@code f}, {@code p}, {@code m}, ...).
     */
    public static Symbol dynamicLetter(char letter) {
        return new Symbol(Kind.DYNAMIC_LETTER, letter);
    }

    public static Symbol digit(int digit) {
        return new Symbol(Kind.DIGIT, digit);
    }
}
