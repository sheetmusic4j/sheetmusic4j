package com.sheetmusic4j.engraving;

import com.sheetmusic4j.core.model.Clef;
import com.sheetmusic4j.core.model.KeySignature;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class KeySignatureLayoutTest {

    @Test
    void sharpsOnTreble() {
        int[] pos = KeySignatureLayout.positions(Clef.treble(), new KeySignature(3));
        assertArrayEquals(new int[]{0, 3, -1}, pos);
        assertEquals(Glyph.ACCIDENTAL_SHARP, KeySignatureLayout.glyphFor(new KeySignature(3)));
    }

    @Test
    void flatsOnTreble() {
        int[] pos = KeySignatureLayout.positions(Clef.treble(), new KeySignature(-2));
        assertArrayEquals(new int[]{4, 1}, pos);
        assertEquals(Glyph.ACCIDENTAL_FLAT, KeySignatureLayout.glyphFor(new KeySignature(-2)));
    }

    @Test
    void bassClefShiftsSharpsDown() {
        int[] treble = KeySignatureLayout.positions(Clef.treble(), new KeySignature(1));
        int[] bass = KeySignatureLayout.positions(Clef.bass(), new KeySignature(1));
        assertEquals(treble.length, bass.length);
        for (int i = 0; i < treble.length; i++) {
            assertEquals(treble[i] + 2, bass[i]);
        }
    }

    @Test
    void emptyKeyProducesNoPositions() {
        assertEquals(0, KeySignatureLayout.positions(Clef.treble(), KeySignature.cMajor()).length);
    }

    @Test
    void beyondSevenIsCapped() {
        int[] pos = KeySignatureLayout.positions(Clef.treble(), new KeySignature(9));
        assertEquals(7, pos.length);
    }
}
