package com.sheetmusic4j.pdf.recognize;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.sheetmusic4j.pdf.extract.PageContent;
import com.sheetmusic4j.pdf.extract.PageContent.Segment;

class HairpinTest {

    /** A staff of space 5 whose bottom line is at y = 120. */
    private static final Staff STAFF = new Staff(50, 550, 100, 120);

    private static Map<Staff, List<Hairpin>> find(Segment... segments) {
        PageContent page = new PageContent(0, 600, 800, List.of(), List.of(), List.of(), List.of(segments), List.of(),
                List.of());
        return Hairpin.find(page, List.of(STAFF));
    }

    @Test
    void shortSteepWedgesAreFound() {
        // a "<>" diamond as Finale draws it: the crescendo rises at 0.33, the diminuendo at 0.28
        Map<Staff, List<Hairpin>> found = find(
                new Segment(200.9, 146.4, 211.9, 142.8, 0.4), new Segment(200.9, 146.4, 211.9, 149.1, 0.4),
                new Segment(215.0, 142.8, 226.1, 145.9, 0.4), new Segment(215.0, 149.1, 226.1, 145.9, 0.4));

        List<Hairpin> hairpins = found.get(STAFF);
        assertEquals(2, hairpins.size());
        assertTrue(hairpins.get(0).crescendo());
        assertFalse(hairpins.get(1).crescendo());
        assertTrue(hairpins.get(0).end() < hairpins.get(1).end());
    }

    @Test
    void duplicateLinesGiveOneWedge() {
        Segment up = new Segment(300, 140, 340, 137, 0.4);
        Segment down = new Segment(300, 140, 340, 143, 0.4);

        assertEquals(1, find(up, down, up, down).get(STAFF).size());
    }

    @Test
    void parallelLinesAreNoWedge() {
        // the two long edges of a sloped beam run in the same direction and do not meet
        assertTrue(find(new Segment(300, 140, 340, 130, 0.4), new Segment(300, 143, 340, 133, 0.4)).isEmpty());
    }
}
