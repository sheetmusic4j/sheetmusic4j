package com.sheetmusic4j.core.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FractionTest {

    @Test
    void normalizesSignAndGcd() {
        assertEquals(Fraction.of(1, 2), Fraction.of(-3, -6));
        assertEquals(Fraction.of(-1, 2), Fraction.of(3, -6));
        assertEquals(Fraction.ZERO, Fraction.of(0, 7));
    }

    @Test
    void tripletsSumExactly() {
        Fraction tripletEighth = new Duration(1, 3).quarters();
        Fraction sum = Fraction.ZERO;
        for (int i = 0; i < 3 * 1000; i++) {
            sum = sum.plus(tripletEighth);
        }
        assertEquals(Fraction.of(1000), sum);
    }

    @Test
    void comparesAndRounds() {
        assertTrue(Fraction.of(1, 3).compareTo(Fraction.of(1, 2)) < 0);
        assertEquals(Fraction.of(1, 2), Fraction.max(Fraction.of(1, 3), Fraction.of(1, 2)));
        assertEquals(1, Fraction.of(1, 2).round());
        assertEquals(0, Fraction.of(1, 3).round());
        assertEquals(-1, Fraction.of(-2, 3).round());
    }

    @Test
    void multipliesFractions() {
        assertEquals(Fraction.of(1, 3), Fraction.of(1, 2).times(Fraction.of(2, 3)));
        assertEquals(Fraction.of(-3, 4), Fraction.of(3, 2).times(Fraction.of(-1, 2)));
    }

    @Test
    void noteTypeLengthsIncludeDots() {
        assertEquals(Fraction.of(1), NoteType.QUARTER.quarters(0));
        assertEquals(Fraction.of(3, 2), NoteType.QUARTER.quarters(1));
        assertEquals(Fraction.of(7, 8), NoteType.EIGHTH.quarters(2));
        assertEquals(Fraction.of(32), NoteType.MAXIMA.quarters(0));
        assertEquals(Fraction.of(1, 32), NoteType.HUNDRED_TWENTY_EIGHTH.quarters(0));
    }

    @Test
    void rejectsZeroDenominator() {
        assertThrows(IllegalArgumentException.class, () -> Fraction.of(1, 0));
    }
}
