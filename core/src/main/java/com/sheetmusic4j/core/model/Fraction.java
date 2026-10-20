package com.sheetmusic4j.core.model;

/**
 * An exact, always-normalized rational number, used for musical time so
 * that tuplet durations (thirds, fifths, ...) add up without the rounding
 * drift of {@code double} arithmetic: three triplet eighths are exactly one
 * quarter, however many measures are summed.
 *
 * @param numerator   the numerator; carries the sign
 * @param denominator the denominator; always positive
 */
public record Fraction(long numerator, long denominator) implements Comparable<Fraction> {

    public static final Fraction ZERO = new Fraction(0, 1);

    public Fraction {
        if (denominator == 0) {
            throw new IllegalArgumentException("denominator must not be zero");
        }
        if (denominator < 0) {
            numerator = -numerator;
            denominator = -denominator;
        }
        long gcd = gcd(Math.abs(numerator), denominator);
        if (gcd > 1) {
            numerator /= gcd;
            denominator /= gcd;
        }
    }

    public static Fraction of(long numerator, long denominator) {
        return new Fraction(numerator, denominator);
    }

    public static Fraction of(long whole) {
        return new Fraction(whole, 1);
    }

    public Fraction plus(Fraction other) {
        return new Fraction(
                Math.addExact(Math.multiplyExact(numerator, other.denominator),
                        Math.multiplyExact(other.numerator, denominator)),
                Math.multiplyExact(denominator, other.denominator));
    }

    public Fraction minus(Fraction other) {
        return plus(new Fraction(-other.numerator, other.denominator));
    }

    public Fraction times(long factor) {
        return new Fraction(Math.multiplyExact(numerator, factor), denominator);
    }

    public Fraction times(Fraction other) {
        return new Fraction(Math.multiplyExact(numerator, other.numerator),
                Math.multiplyExact(denominator, other.denominator));
    }

    public boolean isZero() {
        return numerator == 0;
    }

    public boolean isPositive() {
        return numerator > 0;
    }

    public double toDouble() {
        return (double) numerator / denominator;
    }

    /**
     * The value rounded to the nearest whole number (halves round up).
     */
    public long round() {
        return Math.floorDiv(2 * numerator + denominator, 2 * denominator);
    }

    public static Fraction max(Fraction a, Fraction b) {
        return a.compareTo(b) >= 0 ? a : b;
    }

    public static Fraction min(Fraction a, Fraction b) {
        return a.compareTo(b) <= 0 ? a : b;
    }

    @Override
    public int compareTo(Fraction other) {
        return Long.compare(Math.multiplyExact(numerator, other.denominator),
                Math.multiplyExact(other.numerator, denominator));
    }

    @Override
    public String toString() {
        return denominator == 1 ? Long.toString(numerator) : numerator + "/" + denominator;
    }

    private static long gcd(long a, long b) {
        while (b != 0) {
            long t = a % b;
            a = b;
            b = t;
        }
        return a == 0 ? 1 : a;
    }
}
