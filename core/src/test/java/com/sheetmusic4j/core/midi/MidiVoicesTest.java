package com.sheetmusic4j.core.midi;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import javax.sound.midi.MidiEvent;
import javax.sound.midi.Sequence;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Track;

import org.junit.jupiter.api.Test;

import com.sheetmusic4j.core.model.Attributes;
import com.sheetmusic4j.core.model.Duration;
import com.sheetmusic4j.core.model.Measure;
import com.sheetmusic4j.core.model.Note;
import com.sheetmusic4j.core.model.Part;
import com.sheetmusic4j.core.model.Pitch;
import com.sheetmusic4j.core.model.Score;
import com.sheetmusic4j.core.model.Step;

class MidiVoicesTest {

    @Test
    void grandStaffNotesSoundTogether() {
        Measure measure = Measure.builder(1)
                .attributes(Attributes.builder().divisions(1).staves(2).build())
                .addElement(Note.builder().pitch(new Pitch(Step.C, 5)).duration(new Duration(4, 1)).staff(1).build())
                .addElement(Note.builder().pitch(new Pitch(Step.C, 3)).duration(new Duration(4, 1)).staff(2).build())
                .build();
        Score score = Score.builder().addPart(Part.builder("P1").addMeasure(measure).build()).build();

        Sequence sequence = new MidiExporter().toSequence(score);
        List<Long> noteOnTicks = new ArrayList<>();
        for (Track track : sequence.getTracks()) {
            for (int i = 0; i < track.size(); i++) {
                MidiEvent event = track.get(i);
                if (event.getMessage() instanceof ShortMessage msg && msg.getCommand() == ShortMessage.NOTE_ON) {
                    noteOnTicks.add(event.getTick());
                }
            }
        }
        assertEquals(List.of(0L, 0L), noteOnTicks);
    }
}
