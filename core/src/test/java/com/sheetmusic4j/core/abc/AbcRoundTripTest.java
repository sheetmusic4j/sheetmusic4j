package com.sheetmusic4j.core.abc;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import org.junit.jupiter.api.Test;

import com.sheetmusic4j.core.model.Attributes;
import com.sheetmusic4j.core.model.Clef;
import com.sheetmusic4j.core.model.Duration;
import com.sheetmusic4j.core.model.KeySignature;
import com.sheetmusic4j.core.model.Measure;
import com.sheetmusic4j.core.model.MusicElement;
import com.sheetmusic4j.core.model.Note;
import com.sheetmusic4j.core.model.Part;
import com.sheetmusic4j.core.model.Pitch;
import com.sheetmusic4j.core.model.Score;
import com.sheetmusic4j.core.model.Step;
import com.sheetmusic4j.core.model.TimeSignature;

class AbcRoundTripTest {

    private Score scale() {
        int divisions = 4;
        Step[] steps = {Step.C, Step.D, Step.E, Step.F};
        List<MusicElement> elements = new ArrayList<>();
        for (Step step : steps) {
            elements.add(Note.builder()
                    .pitch(new Pitch(step, 4))
                    .duration(new Duration(divisions, divisions))
                    .build());
        }
        Measure measure = Measure.builder(1)
                .attributes(Attributes.builder()
                        .divisions(divisions)
                        .keySignature(KeySignature.cMajor())
                        .timeSignature(TimeSignature.fourFour())
                        .clef(Clef.treble())
                        .build())
                .elements(elements)
                .build();
        Part part = Part.builder("P1").name("Piano").addMeasure(measure).build();
        return Score.builder().workTitle("Scale").addPart(part).build();
    }

    @Test
    void roundTripPreservesPitchAndOrdering() {
        Score original = scale();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new AbcWriter().write(original, out);

        Score reloaded = new AbcReader().read(new ByteArrayInputStream(out.toByteArray()));
        assertFalse(reloaded.parts().isEmpty());

        List<Integer> pitches = new ArrayList<>();
        for (MusicElement el : reloaded.parts().get(0).measures().get(0).elements()) {
            if (el instanceof Note n) {
                pitches.add(n.pitch().toMidiNumber());
            }
        }
        assertEquals(List.of(60, 62, 64, 65), pitches);
    }

    @Test
    void roundTripPreservesWorkTitle() {
        Score original = scale();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new AbcWriter().write(original, out);
        Score reloaded = new AbcReader().read(new ByteArrayInputStream(out.toByteArray()));
        assertEquals("Scale", reloaded.workTitle().orElse(null));
    }

    @Test
    void roundTripPreservesPostTuneText() {
        Part original = scale().parts().get(0);
        Part withVerses = Part.builder(original.id())
                .name(original.name())
                .measures(original.measures())
                .addPostTuneText("Verse one line one")
                .addPostTuneText("Verse one line two")
                .build();
        Score originalScore = Score.builder().workTitle("Scale").addPart(withVerses).build();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new AbcWriter().write(originalScore, out);
        Score reloaded = new AbcReader().read(new ByteArrayInputStream(out.toByteArray()));

        Part reloadedPart = reloaded.parts().get(0);
        assertEquals(2, reloadedPart.postTuneText().size());
        assertEquals("Verse one line one", reloadedPart.postTuneText().get(0));
        assertEquals("Verse one line two", reloadedPart.postTuneText().get(1));
    }
    }
