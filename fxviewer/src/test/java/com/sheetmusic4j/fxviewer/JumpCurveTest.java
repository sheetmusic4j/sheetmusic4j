package com.sheetmusic4j.fxviewer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class JumpCurveTest {

    @Test
    void restsOnThePaperBeforeTheOnset() {
        assertEquals(0, JumpCurve.lift(1.99, 2.0, 1.0));
        assertEquals(0, JumpCurve.lift(2.0, 2.0, 1.0));
    }

    @Test
    void popsUpAndHoversWhileTheNoteSounds() {
        double halfway = JumpCurve.lift(2.0 + JumpCurve.RISE_QUARTERS / 2, 2.0, 1.0);
        assertTrue(halfway > 0 && halfway < 1);
        assertEquals(1.0, JumpCurve.lift(2.0 + JumpCurve.RISE_QUARTERS, 2.0, 1.0), 1e-9);
        assertEquals(1.0, JumpCurve.lift(2.9, 2.0, 1.0));
        assertEquals(1.0, JumpCurve.lift(3.0, 2.0, 1.0));
    }

    @Test
    void settlesBackAfterTheNoteEnds() {
        double falling = JumpCurve.lift(3.0 + JumpCurve.FALL_QUARTERS / 2, 2.0, 1.0);
        assertTrue(falling > 0 && falling < 1);
        assertEquals(0, JumpCurve.lift(3.0 + JumpCurve.FALL_QUARTERS, 2.0, 1.0));
        assertEquals(0, JumpCurve.lift(10.0, 2.0, 1.0));
        assertEquals(1.0 + JumpCurve.FALL_QUARTERS, JumpCurve.airborne(1.0));
    }

    @Test
    void veryShortNotesStillReachFullHeight() {
        double duration = 1.0 / 32;
        assertEquals(1.0, JumpCurve.lift(duration, 0.0, duration), 1e-9);
    }

    @Test
    void timelessElementsNeverLift() {
        assertEquals(0, JumpCurve.lift(0.1, 0.0, 0.0));
    }
}
