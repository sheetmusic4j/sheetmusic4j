package com.sheetmusic4j.core.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class KeySignatureTest {

    @Test
    void alterFollowsOrderOfSharpsAndFlats() {
        KeySignature dMajor = new KeySignature(2);
        assertEquals(1, dMajor.alter(Step.F));
        assertEquals(1, dMajor.alter(Step.C));
        assertEquals(0, dMajor.alter(Step.G));

        KeySignature eFlatMajor = new KeySignature(-3);
        assertEquals(-1, eFlatMajor.alter(Step.B));
        assertEquals(-1, eFlatMajor.alter(Step.A));
        assertEquals(0, eFlatMajor.alter(Step.D));

        assertEquals(0, KeySignature.cMajor().alter(Step.F));
        assertEquals(0, KeySignature.cMajor().alter(Step.B));
    }
}
