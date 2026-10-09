package com.sheetmusic4j.core.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class MeasureTimingTest {

    private static Note note(Step step, int quarters, int staff, int voice) {
        return Note.builder().pitch(new Pitch(step, 4)).duration(new Duration(quarters, 1))
                .staff(staff).voice(voice).build();
    }

    @Test
    void voicesStartTogether() {
        Measure measure = Measure.builder(1)
                .addElement(note(Step.C, 2, 1, 1))
                .addElement(note(Step.D, 2, 1, 1))
                .addElement(note(Step.E, 4, 1, 2))
                .addElement(note(Step.F, 4, 2, 1))
                .build();
        MeasureTiming timing = MeasureTiming.of(measure);
        assertEquals(Fraction.ZERO, timing.onset(0));
        assertEquals(Fraction.of(2), timing.onset(1));
        assertEquals(Fraction.ZERO, timing.onset(2));
        assertEquals(Fraction.ZERO, timing.onset(3));
        assertEquals(Fraction.of(4), timing.duration());
    }

    @Test
    void invisibleRestDelaysAVoiceEntry() {
        Measure measure = Measure.builder(1)
                .addElement(note(Step.C, 4, 1, 1))
                .addElement(Rest.builder().duration(new Duration(2, 1)).voice(2).visible(false).build())
                .addElement(note(Step.G, 2, 1, 2))
                .build();
        MeasureTiming timing = MeasureTiming.of(measure);
        assertEquals(Fraction.of(2), timing.onset(2));
    }

    @Test
    void directionTakesTheNextOnset() {
        Measure measure = Measure.builder(1)
                .addElement(note(Step.C, 1, 1, 1))
                .addElement(new Direction(new DirectionType.Words("dolce", false, false), Placement.ABOVE))
                .addElement(note(Step.D, 1, 1, 1))
                .build();
        assertEquals(Fraction.of(1), MeasureTiming.of(measure).onset(1));
    }
}
