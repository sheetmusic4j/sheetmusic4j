package com.sheetmusic4j.core.lilypond;

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
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class LilyPondWriterTest {

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
    void emitsVersionHeaderAndCoreDirectives() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new LilyPondWriter().write(scale(), out);
        String ly = out.toString(StandardCharsets.UTF_8);

        assertTrue(ly.contains("\\version \"2.24.0\""), ly);
        assertTrue(ly.contains("\\header"), ly);
        assertTrue(ly.contains("title = \"Scale\""), ly);
        assertTrue(ly.contains("\\score"), ly);
        assertTrue(ly.contains("\\new Staff"), ly);
        assertTrue(ly.contains("\\time 4/4"), ly);
        assertTrue(ly.contains("\\key c \\major"), ly);
        assertTrue(ly.contains("\\clef treble"), ly);
        // Middle-C row: c'4 d'4 e'4 f'4 (duration carry means only the first has the "4").
        assertTrue(ly.contains("c'4"), ly);
        assertTrue(ly.contains("d'"), ly);
        assertTrue(ly.contains("|"), ly);
    }
}
