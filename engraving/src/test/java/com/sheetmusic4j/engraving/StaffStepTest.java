package com.sheetmusic4j.engraving;

import com.sheetmusic4j.core.model.Clef;
import com.sheetmusic4j.core.model.Pitch;
import com.sheetmusic4j.core.model.Step;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StaffStepTest {

    @Test
    void trebleTopLineIsF5() {
        // Top staff line (staffStep 0) of treble clef is F5.
        assertEquals(0, Engraver.staffStep(new Pitch(Step.F, 5), Clef.treble()));
    }

    @Test
    void trebleBottomLineIsE4() {
        // Bottom staff line (staffStep 8) of treble clef is E4.
        assertEquals(8, Engraver.staffStep(new Pitch(Step.E, 4), Clef.treble()));
    }

    @Test
    void trebleMiddleCIsBelowStaff() {
        // Middle C (C4) sits one ledger line below the treble staff (staffStep 10).
        assertEquals(10, Engraver.staffStep(new Pitch(Step.C, 4), Clef.treble()));
    }

    @Test
    void bassTopLineIsA3() {
        // Bottom line of bass clef is G2; top line is A3 (staffStep 0).
        assertEquals(0, Engraver.staffStep(new Pitch(Step.A, 3), Clef.bass()));
    }
}
