package com.sheet4j.fxdemo;

import com.sheet4j.core.model.Attributes;
import com.sheet4j.core.model.Chord;
import com.sheet4j.core.model.Clef;
import com.sheet4j.core.model.Duration;
import com.sheet4j.core.model.KeySignature;
import com.sheet4j.core.model.Measure;
import com.sheet4j.core.model.Note;
import com.sheet4j.core.model.Part;
import com.sheet4j.core.model.Pitch;
import com.sheet4j.core.model.Rest;
import com.sheet4j.core.model.Score;
import com.sheet4j.core.model.Step;
import com.sheet4j.core.model.TimeSignature;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScoreInspectorTest {

    private Note note(Step step, int octave) {
        return Note.builder().pitch(new Pitch(step, octave)).duration(new Duration(1, 1)).build();
    }

    private Score sampleScore() {
        Measure measure = Measure.builder(1)
                .attributes(Attributes.builder()
                        .divisions(1)
                        .keySignature(KeySignature.cMajor())
                        .timeSignature(TimeSignature.fourFour())
                        .clef(Clef.treble())
                        .build())
                .addElement(note(Step.C, 4))
                .addElement(Rest.builder().duration(new Duration(1, 1)).build())
                .addElement(new Chord(List.of(note(Step.C, 4), note(Step.E, 4), note(Step.G, 4))))
                .build();
        Part part = Part.builder("P1").name("Piano").addMeasure(measure).build();
        return Score.builder().workTitle("Test Work").addPart(part).build();
    }

    @Test
    void statsCountsElements() {
        ScoreInspector.Stats stats = ScoreInspector.stats(sampleScore());
        assertEquals(1, stats.parts());
        assertEquals(1, stats.measures());
        assertEquals(1, stats.notes());
        assertEquals(1, stats.rests());
        assertEquals(1, stats.chords());
        assertEquals(3, stats.elements());
    }

    @Test
    void describeIncludesTitleAndCounts() {
        String description = ScoreInspector.describe(sampleScore());
        assertTrue(description.contains("Test Work"), "should contain work title");
        assertTrue(description.contains("Piano"), "should contain part name");
        assertTrue(description.contains("4/4"), "should contain time signature");
        assertTrue(description.contains("G/2"), "should contain treble clef");
    }

    @Test
    void describeHandlesNullScore() {
        assertEquals("No score loaded.", ScoreInspector.describe(null));
    }
}
