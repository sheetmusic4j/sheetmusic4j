package com.sheetmusic4j.core.midi;

import com.sheetmusic4j.core.model.Attributes;
import com.sheetmusic4j.core.model.Clef;
import com.sheetmusic4j.core.model.Duration;
import com.sheetmusic4j.core.model.KeySignature;
import com.sheetmusic4j.core.model.Measure;
import com.sheetmusic4j.core.model.Note;
import com.sheetmusic4j.core.model.Part;
import com.sheetmusic4j.core.model.Pitch;
import com.sheetmusic4j.core.model.Rest;
import com.sheetmusic4j.core.model.Score;
import com.sheetmusic4j.core.model.TimeSignature;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.Sequence;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Track;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Imports a Standard MIDI File into a {@link Score}.
 *
 * <p>This mapping is inherently lossy. Assumptions:
 * <ul>
 *   <li>Only PPQ-based sequences are supported; the tick resolution becomes the
 *       MusicXML {@code divisions}.</li>
 *   <li>A default 4/4 time signature is assumed for barring.</li>
 *   <li>Each track that contains notes becomes one {@link Part}.</li>
 *   <li>Notes are treated monophonically within a measure and laid out
 *       sequentially; gaps become rests.</li>
 * </ul>
 */
public final class MidiImporter {

    private final TimeSignature timeSignature;

    public MidiImporter() {
        this(TimeSignature.fourFour());
    }

    public MidiImporter(TimeSignature timeSignature) {
        this.timeSignature = timeSignature;
    }

    public Score fromMidi(Path path) {
        try {
            return fromSequence(MidiSystem.getSequence(path.toFile()));
        } catch (InvalidMidiDataException | IOException e) {
            throw new MidiException("Could not read MIDI file: " + path, e);
        }
    }

    public Score fromMidi(InputStream in) {
        try {
            return fromSequence(MidiSystem.getSequence(in));
        } catch (InvalidMidiDataException | IOException e) {
            throw new MidiException("Could not read MIDI stream", e);
        }
    }

    public Score fromSequence(Sequence sequence) {
        if (sequence.getDivisionType() != Sequence.PPQ) {
            throw new MidiException("Only PPQ-based MIDI sequences are supported");
        }
        int divisions = sequence.getResolution();
        Score.Builder score = Score.builder();

        int partIndex = 1;
        for (Track track : sequence.getTracks()) {
            List<TimedNote> notes = collectNotes(track);
            if (notes.isEmpty()) {
                continue;
            }
            String id = "P" + partIndex;
            Part part = buildPart(id, "Part " + partIndex, notes, divisions);
            score.addPart(part);
            partIndex++;
        }
        return score.build();
    }

    private List<TimedNote> collectNotes(Track track) {
        Map<Integer, Long> onTicks = new HashMap<>();
        List<TimedNote> notes = new ArrayList<>();
        for (int i = 0; i < track.size(); i++) {
            MidiEvent event = track.get(i);
            MidiMessage message = event.getMessage();
            if (message instanceof ShortMessage sm) {
                int command = sm.getCommand();
                int key = sm.getData1();
                int velocity = sm.getData2();
                if (command == ShortMessage.NOTE_ON && velocity > 0) {
                    onTicks.put(key, event.getTick());
                } else if (command == ShortMessage.NOTE_OFF
                        || (command == ShortMessage.NOTE_ON && velocity == 0)) {
                    Long start = onTicks.remove(key);
                    if (start != null) {
                        long duration = event.getTick() - start;
                        if (duration > 0) {
                            notes.add(new TimedNote(key, start, duration));
                        }
                    }
                }
            }
        }
        notes.sort((a, b) -> Long.compare(a.startTick, b.startTick));
        return notes;
    }

    private Part buildPart(String id, String name, List<TimedNote> notes, int divisions) {
        Part.Builder part = Part.builder(id).name(name);
        long measureTicks = Math.round(timeSignature.measureLengthInQuarters() * divisions);

        long totalTicks = 0;
        for (TimedNote note : notes) {
            totalTicks = Math.max(totalTicks, note.startTick + note.durationTicks);
        }
        int measureCount = Math.max(1, (int) Math.ceil((double) totalTicks / measureTicks));

        int noteIndex = 0;
        for (int m = 0; m < measureCount; m++) {
            long measureStart = (long) m * measureTicks;
            long measureEnd = measureStart + measureTicks;
            Measure.Builder measure = Measure.builder(m + 1);
            if (m == 0) {
                measure.attributes(Attributes.builder()
                        .divisions(divisions)
                        .keySignature(KeySignature.cMajor())
                        .timeSignature(timeSignature)
                        .clef(Clef.treble())
                        .build());
            }

            long cursor = measureStart;
            while (noteIndex < notes.size() && notes.get(noteIndex).startTick < measureEnd) {
                TimedNote timed = notes.get(noteIndex);
                if (timed.startTick > cursor) {
                    long restTicks = timed.startTick - cursor;
                    measure.addElement(Rest.builder()
                            .duration(new Duration((int) restTicks, divisions))
                            .build());
                    cursor = timed.startTick;
                }
                long noteEnd = Math.min(timed.startTick + timed.durationTicks, measureEnd);
                long dur = Math.max(1, noteEnd - timed.startTick);
                measure.addElement(Note.builder()
                        .pitch(Pitch.fromMidiNumber(timed.key))
                        .duration(new Duration((int) dur, divisions))
                        .build());
                cursor = timed.startTick + dur;
                if (timed.startTick + timed.durationTicks <= measureEnd) {
                    noteIndex++;
                } else {
                    // note extends past the bar; stop filling this measure
                    break;
                }
            }
            if (cursor < measureEnd) {
                long restTicks = measureEnd - cursor;
                measure.addElement(Rest.builder()
                        .duration(new Duration((int) restTicks, divisions))
                        .build());
            }
            part.addMeasure(measure.build());
        }
        return part.build();
    }

    private record TimedNote(int key, long startTick, long durationTicks) {
    }
}
