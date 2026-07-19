package com.sheet4j.core.musicxml;

import com.sheet4j.core.model.MusicElement;
import com.sheet4j.core.model.Note;
import com.sheet4j.core.model.Part;
import com.sheet4j.core.model.Score;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class MusicXmlWriterTest {

    private Score readSample() {
        try (InputStream in = getClass().getResourceAsStream("/c-major-scale.musicxml")) {
            assertNotNull(in);
            return new MusicXmlReader().read(in);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void roundTripPreservesStructure() {
        Score original = readSample();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new MusicXmlWriter().write(original, out);

        Score reparsed = new MusicXmlReader().read(new ByteArrayInputStream(out.toByteArray()));

        assertEquals(original.workTitle(), reparsed.workTitle());
        assertEquals(original.parts().size(), reparsed.parts().size());

        Part originalPart = original.parts().get(0);
        Part reparsedPart = reparsed.parts().get(0);
        assertEquals(originalPart.measures().size(), reparsedPart.measures().size());

        for (int m = 0; m < originalPart.measures().size(); m++) {
            var origElems = originalPart.measures().get(m).elements();
            var reElems = reparsedPart.measures().get(m).elements();
            assertEquals(origElems.size(), reElems.size(), "measure " + m + " element count");
            for (int i = 0; i < origElems.size(); i++) {
                assertSameElement(origElems.get(i), reElems.get(i));
            }
        }
    }

    private void assertSameElement(MusicElement a, MusicElement b) {
        assertEquals(a.getClass(), b.getClass());
        assertEquals(a.duration().value(), b.duration().value());
        if (a instanceof Note na && b instanceof Note nb) {
            assertEquals(na.pitch(), nb.pitch());
            assertEquals(na.type(), nb.type());
        }
    }
}
