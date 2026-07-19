package com.sheet4j.core.musicxml;

import com.sheet4j.core.model.Attributes;
import com.sheet4j.core.model.MusicElement;
import com.sheet4j.core.model.Note;
import com.sheet4j.core.model.Part;
import com.sheet4j.core.model.Score;
import com.sheet4j.core.model.Step;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MusicXmlReaderTest {

    private Score readSample() {
        try (InputStream in = getClass().getResourceAsStream("/c-major-scale.musicxml")) {
            assertNotNull(in, "sample resource must exist");
            return new MusicXmlReader().read(in);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void readsTitleAndParts() {
        Score score = readSample();
        assertEquals("C Major Scale", score.workTitle().orElse(null));
        assertEquals(1, score.parts().size());
        assertEquals("Piano", score.parts().get(0).name());
    }

    @Test
    void readsMeasuresAndNotes() {
        Score score = readSample();
        Part part = score.parts().get(0);
        assertEquals(2, part.measures().size());
        assertEquals(4, part.measures().get(0).elements().size());

        MusicElement first = part.measures().get(0).elements().get(0);
        assertInstanceOf(Note.class, first);
        assertEquals(Step.C, ((Note) first).pitch().step());
        assertEquals(4, ((Note) first).pitch().octave());
    }

    @Test
    void readsAttributes() {
        Score score = readSample();
        Attributes attributes = score.parts().get(0).measures().get(0).attributes().orElseThrow();
        assertEquals(1, attributes.divisions().orElseThrow());
        assertEquals(0, attributes.keySignature().orElseThrow().fifths());
        assertEquals(4, attributes.timeSignature().orElseThrow().beats());
        assertTrue(attributes.clef().isPresent());
    }
}
