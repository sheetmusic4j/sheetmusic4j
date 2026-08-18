package com.sheetmusic4j.core.lilypond;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.sheetmusic4j.core.model.Attributes;
import com.sheetmusic4j.core.model.Clef;
import com.sheetmusic4j.core.model.Direction;
import com.sheetmusic4j.core.model.DirectionType;
import com.sheetmusic4j.core.model.Duration;
import com.sheetmusic4j.core.model.KeySignature;
import com.sheetmusic4j.core.model.Measure;
import com.sheetmusic4j.core.model.MusicElement;
import com.sheetmusic4j.core.model.Note;
import com.sheetmusic4j.core.model.NoteType;
import com.sheetmusic4j.core.model.Part;
import com.sheetmusic4j.core.model.Pitch;
import com.sheetmusic4j.core.model.Score;
import com.sheetmusic4j.core.model.Step;
import com.sheetmusic4j.core.model.TimeSignature;

class LilyPondRoundTripTest {

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
        new LilyPondWriter().write(original, out);

        Score reloaded = new LilyPondReader().read(new ByteArrayInputStream(out.toByteArray()));
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
        new LilyPondWriter().write(original, out);
        Score reloaded = new LilyPondReader().read(new ByteArrayInputStream(out.toByteArray()));
        assertEquals("Scale", reloaded.workTitle().orElse(null));
    }

    @Test
    void roundTripPreservesTempo() {
        int divisions = 4;
        List<MusicElement> elements = new ArrayList<>();
        elements.add(new Direction(new DirectionType.Metronome(NoteType.QUARTER, false, 120),
                com.sheetmusic4j.core.model.Placement.ABOVE));
        elements.add(Note.builder()
                .pitch(new Pitch(Step.C, 4))
                .duration(new Duration(divisions, divisions))
                .build());
        Measure measure = Measure.builder(1)
                .attributes(Attributes.builder()
                        .divisions(divisions)
                        .keySignature(KeySignature.cMajor())
                        .timeSignature(TimeSignature.fourFour())
                        .clef(Clef.treble())
                        .build())
                .elements(elements)
                .build();
        Part part = Part.builder("P1").addMeasure(measure).build();
        Score original = Score.builder().workTitle("Tempo").addPart(part).build();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new LilyPondWriter().write(original, out);
        String emitted = out.toString(java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(emitted.contains("\\tempo 4 = 120"), emitted);

        Score reloaded = new LilyPondReader().read(new ByteArrayInputStream(out.toByteArray()));
        boolean hasTempo = false;
        for (MusicElement el : reloaded.parts().get(0).measures().get(0).elements()) {
            if (el instanceof Direction d && d.type() instanceof DirectionType.Metronome m
                    && m.perMinute() == 120) {
                hasTempo = true;
            }
        }
        assertTrue(hasTempo, "Expected tempo 120 to round-trip");
    }
}
