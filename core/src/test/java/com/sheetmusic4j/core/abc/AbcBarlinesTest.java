package com.sheetmusic4j.core.abc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.sheetmusic4j.core.model.Barline;
import com.sheetmusic4j.core.model.Measure;

class AbcBarlinesTest {

    private static List<Measure> measures(String body) {
        String abc = "X:1\nT:Bars\nM:C\nL:1/4\nK:C\n" + body + "\n";
        return new AbcReader().read(new ByteArrayInputStream(abc.getBytes(StandardCharsets.UTF_8)))
                .parts().get(0).measures();
    }

    @Test
    void openingHeavyLightBarBecomesALeadingBarline() {
        List<Measure> measures = measures("[| C D E F | G A B c |]");
        assertEquals(2, measures.size());
        assertEquals(new Barline(Barline.Style.HEAVY_LIGHT, Barline.Repeat.NONE),
                measures.get(0).leadingBarline().orElseThrow());
        assertFalse(measures.get(0).leadingRepeatStart());
        assertEquals(Barline.Style.FINAL, measures.get(1).barline().orElseThrow().style());
    }

    @Test
    void repeatStartIsALeadingForwardRepeat() {
        List<Measure> measures = measures("|: C D E F :: G A B c :|");
        assertTrue(measures.get(0).leadingRepeatStart());
        assertEquals(Barline.Repeat.BOTH, measures.get(0).barline().orElseThrow().repeat());
        assertEquals(Barline.Repeat.BACKWARD, measures.get(1).barline().orElseThrow().repeat());
    }

    @Test
    void midTuneHeavyLightBarClosesTheMeasure() {
        List<Measure> measures = measures("C D E F [| G A B c |]");
        assertEquals(Barline.Style.HEAVY_LIGHT, measures.get(0).barline().orElseThrow().style());
        assertTrue(measures.get(1).leadingBarline().isEmpty());
    }
}
