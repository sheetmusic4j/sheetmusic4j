package com.sheetmusic4j.core.abc;

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

class AbcWriterTest {

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
    void emitsExpectedInfoFieldsAndNotes() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new AbcWriter().write(scale(), out);
        String abc = out.toString(StandardCharsets.UTF_8);

        assertTrue(abc.contains("X:1"), abc);
        assertTrue(abc.contains("T:Scale"), abc);
        assertTrue(abc.contains("M:4/4"), abc);
        assertTrue(abc.contains("L:1/8"), abc);
        assertTrue(abc.contains("K:C"), abc);
        // With L:1/8, quarter notes render as letter followed by "2".
        assertTrue(abc.contains("C2"), abc);
        assertTrue(abc.contains("D2"), abc);
        assertTrue(abc.contains("E2"), abc);
        assertTrue(abc.contains("F2"), abc);
        assertTrue(abc.contains("|"), abc);
    }
}
