package com.sheetmusic4j.core.midi;

import com.sheetmusic4j.core.model.Attributes;
import com.sheetmusic4j.core.model.Chord;
import com.sheetmusic4j.core.model.Measure;
import com.sheetmusic4j.core.model.MusicElement;
import com.sheetmusic4j.core.model.Note;
import com.sheetmusic4j.core.model.Part;
import com.sheetmusic4j.core.model.Score;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MetaMessage;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.Sequence;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Track;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;

/**
 * Exports a {@link Score} to a Standard MIDI File (format 1), one track per part.
 *
 * <p>The tick resolution is taken from the first divisions value found in the
 * score (defaulting to 480 PPQ). Note start/duration come from each element's
 * {@link com.sheetmusic4j.core.model.Duration}. A single tempo meta event is emitted.
 */
public final class MidiExporter {

    private static final int DEFAULT_DIVISIONS = 480;
    private static final int DEFAULT_VELOCITY = 80;
    private static final int DEFAULT_TEMPO_BPM = 120;

    public void toMidi(Score score, Path path) {
        Sequence sequence = toSequence(score);
        try {
            MidiSystem.write(sequence, 1, path.toFile());
        } catch (IOException e) {
            throw new MidiException("Could not write MIDI file: " + path, e);
        }
    }

    public void toMidi(Score score, OutputStream out) {
        Sequence sequence = toSequence(score);
        try {
            MidiSystem.write(sequence, 1, out);
        } catch (IOException e) {
            throw new MidiException("Could not write MIDI stream", e);
        }
    }

    public Sequence toSequence(Score score) {
        int divisions = resolveDivisions(score);
        try {
            Sequence sequence = new Sequence(Sequence.PPQ, divisions);
            boolean tempoWritten = false;
            for (Part part : score.parts()) {
                Track track = sequence.createTrack();
                if (!tempoWritten) {
                    track.add(tempoEvent(DEFAULT_TEMPO_BPM));
                    tempoWritten = true;
                }
                long tick = 0;
                for (Measure measure : part.measures()) {
                    for (MusicElement element : measure.elements()) {
                        tick = writeElement(track, element, tick);
                    }
                }
            }
            return sequence;
        } catch (InvalidMidiDataException e) {
            throw new MidiException("Could not build MIDI sequence", e);
        }
    }

    private long writeElement(Track track, MusicElement element, long tick) throws InvalidMidiDataException {
        long duration = element.duration().value();
        if (element instanceof Note note) {
            addNote(track, note.pitch().toMidiNumber(), tick, duration);
        } else if (element instanceof Chord chord) {
            for (Note note : chord.notes()) {
                addNote(track, note.pitch().toMidiNumber(), tick, duration);
            }
        }
        // Rests simply advance the cursor.
        return tick + duration;
    }

    private void addNote(Track track, int midiKey, long startTick, long durationTicks)
            throws InvalidMidiDataException {
        int key = Math.max(0, Math.min(127, midiKey));
        ShortMessage on = new ShortMessage();
        on.setMessage(ShortMessage.NOTE_ON, 0, key, DEFAULT_VELOCITY);
        track.add(new MidiEvent(on, startTick));
        ShortMessage off = new ShortMessage();
        off.setMessage(ShortMessage.NOTE_OFF, 0, key, 0);
        track.add(new MidiEvent(off, startTick + Math.max(1, durationTicks)));
    }

    private MidiEvent tempoEvent(int bpm) throws InvalidMidiDataException {
        int microsecondsPerQuarter = (int) (60_000_000L / bpm);
        byte[] data = {
                (byte) ((microsecondsPerQuarter >> 16) & 0xFF),
                (byte) ((microsecondsPerQuarter >> 8) & 0xFF),
                (byte) (microsecondsPerQuarter & 0xFF)
        };
        MetaMessage message = new MetaMessage();
        message.setMessage(0x51, data, data.length);
        return new MidiEvent(message, 0);
    }

    private int resolveDivisions(Score score) {
        for (Part part : score.parts()) {
            for (Measure measure : part.measures()) {
                if (measure.attributes().isPresent()) {
                    Attributes attributes = measure.attributes().get();
                    if (attributes.divisions().isPresent()) {
                        return attributes.divisions().get();
                    }
                }
            }
        }
        return DEFAULT_DIVISIONS;
    }
}
