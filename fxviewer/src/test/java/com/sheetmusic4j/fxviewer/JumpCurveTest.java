package com.sheetmusic4j.fxviewer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class JumpCurveTest {

    @Test
    void ballIsOnTheNotesAtTheirOnsets() {
        assertEquals(0, JumpCurve.height(2.0, 2.0, 3.0));
        assertEquals(0, JumpCurve.height(3.0, 2.0, 3.0));
        assertEquals(0, JumpCurve.progress(2.0, 2.0, 3.0));
        assertEquals(1, JumpCurve.progress(3.0, 2.0, 3.0));
    }

    @Test
    void ballPeaksHalfwayBetweenNotes() {
        assertEquals(1.0, JumpCurve.height(2.5, 2.0, 3.0), 1e-9);
        assertEquals(0.5, JumpCurve.progress(2.5, 2.0, 3.0), 1e-9);
        double early = JumpCurve.height(2.2, 2.0, 3.0);
        assertTrue(early > 0 && early < 1);
        assertEquals(early, JumpCurve.height(2.8, 2.0, 3.0), 1e-9);
    }

    @Test
    void shortHopsAreLower() {
        double sixteenth = JumpCurve.height(0.125, 0.0, 0.25);
        assertEquals(0.5, sixteenth, 1e-9);
        assertEquals(1.0, JumpCurve.height(2.0, 0.0, 4.0), 1e-9);
    }

    @Test
    void degenerateHopsStayOnTheNote() {
        assertEquals(0, JumpCurve.height(1.0, 1.0, 1.0));
        assertEquals(1, JumpCurve.progress(1.0, 1.0, 1.0));
    }

    @Test
    void noteGlowsFromItsOnsetWhileItSounds() {
        assertEquals(0, JumpCurve.glow(1.99, 2.0, 1.0));
        assertEquals(1, JumpCurve.glow(2.0, 2.0, 1.0));
        assertEquals(1, JumpCurve.glow(3.0, 2.0, 1.0));
    }

    @Test
    void glowFadesAfterTheNoteEnds() {
        double fading = JumpCurve.glow(3.0 + JumpCurve.FADE_QUARTERS / 2, 2.0, 1.0);
        assertTrue(fading > 0 && fading < 1);
        assertEquals(0, JumpCurve.glow(3.0 + JumpCurve.FADE_QUARTERS, 2.0, 1.0));
        assertEquals(1.0 + JumpCurve.FADE_QUARTERS, JumpCurve.afterglow(1.0));
    }

    @Test
    void timelessElementsNeverGlow() {
        assertEquals(0, JumpCurve.glow(0.1, 0.0, 0.0));
    }
}
