package com.sheetmusic4j.core.abc;

/**
 * Parses ABC note-length suffixes (the digits/slashes trailing a note letter)
 * into a rational multiplier of the current unit note length ({@code L:}).
 *
 * <p>Examples (assuming {@code L:1/8}):
 * <ul>
 *   <li>{@code A} → {@code 1/1} (one eighth)</li>
 *   <li>{@code A2} → {@code 2/1} (a quarter)</li>
 *   <li>{@code A/} → {@code 1/2} (a sixteenth)</li>
 *   <li>{@code A/2} → {@code 1/2} (a sixteenth)</li>
 *   <li>{@code A3/2} → {@code 3/2} (dotted eighth)</li>
 *   <li>{@code A//} → {@code 1/4} (a thirty-second)</li>
 * </ul>
 */
final class AbcNoteLength {

    /** Rational number in lowest terms (num, den both positive). */
    record Fraction(int num, int den) {
        Fraction {
            if (den <= 0) {
                throw new IllegalArgumentException("den must be positive");
            }
            if (num < 0) {
                throw new IllegalArgumentException("num must be non-negative");
            }
        }

        static Fraction of(int n, int d) {
            int g = gcd(Math.abs(n), Math.abs(d));
            return new Fraction(n / g, d / g);
        }

        Fraction times(Fraction other) {
            return Fraction.of(num * other.num, den * other.den);
        }

        Fraction times(int n, int d) {
            return Fraction.of(num * n, den * d);
        }

        private static int gcd(int a, int b) {
            return b == 0 ? (a == 0 ? 1 : a) : gcd(b, a % b);
        }
    }

    /** Result of parsing a length suffix from an input string. */
    record Parsed(Fraction multiplier, int consumed) {
    }

    private AbcNoteLength() {
    }

    /**
     * Parse the length suffix starting at {@code pos} of {@code input}. Returns
     * the multiplier and how many characters were consumed. When no digits or
     * slashes are present the multiplier is {@code 1/1} and {@code consumed}
     * is {@code 0}.
     */
    static Parsed parseSuffix(String input, int pos) {
        int i = pos;
        int len = input.length();

        // Numerator: optional leading digits.
        int num = 0;
        boolean hasNum = false;
        while (i < len && Character.isDigit(input.charAt(i))) {
            num = num * 10 + (input.charAt(i) - '0');
            hasNum = true;
            i++;
        }
        if (!hasNum) {
            num = 1;
        }

        // Slashes and optional denominator digits.
        int slashCount = 0;
        while (i < len && input.charAt(i) == '/') {
            slashCount++;
            i++;
        }

        int den = 1;
        if (slashCount > 0) {
            int denStart = i;
            int explicit = 0;
            boolean hasDen = false;
            while (i < len && Character.isDigit(input.charAt(i))) {
                explicit = explicit * 10 + (input.charAt(i) - '0');
                hasDen = true;
                i++;
            }
            if (hasDen) {
                den = explicit;
                // Multiple slashes with an explicit denominator (rare) — treat
                // as ambiguous but honour the explicit denominator; ignore
                // extra slashes' doubling to stay conservative.
            } else {
                // "A/" → /2, "A//" → /4, "A///" → /8, etc.
                den = 1 << slashCount;
            }
            // Guard against malformed "A/" with no denominator or trailing text.
            if (denStart == i && slashCount == 0) {
                den = 1;
            }
            if (den == 0) {
                den = 1;
            }
        }

        return new Parsed(Fraction.of(num, den), i - pos);
    }
}
