package com.sheetmusic4j.core.midi;

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

import javax.sound.midi.Sequence;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MidiRoundTripTest {

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
    void exportsSequenceWithExpectedResolution() {
        Sequence sequence = new MidiExporter().toSequence(scale());
        assertEquals(Sequence.PPQ, sequence.getDivisionType());
        assertEquals(4, sequence.getResolution());
        assertTrue(sequence.getTracks().length >= 1);
    }

    @Test
    void importReadsExportedNotes() {
        Score original = scale();
        Sequence sequence = new MidiExporter().toSequence(original);
        Score imported = new MidiImporter().fromSequence(sequence);

        assertFalse(imported.parts().isEmpty());
        Part part = imported.parts().get(0);
        assertFalse(part.measures().isEmpty());

        List<Integer> pitches = new ArrayList<>();
        for (MusicElement element : part.measures().get(0).elements()) {
            if (element instanceof Note note) {
                pitches.add(note.pitch().toMidiNumber());
            }
        }
        assertEquals(List.of(60, 62, 64, 65), pitches);
    }
}
