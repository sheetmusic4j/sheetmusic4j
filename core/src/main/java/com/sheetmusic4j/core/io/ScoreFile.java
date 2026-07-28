package com.sheetmusic4j.core.io;

import java.nio.file.Path;
import java.util.Locale;

import com.sheetmusic4j.core.abc.AbcReader;
import com.sheetmusic4j.core.abc.AbcWriter;
import com.sheetmusic4j.core.midi.MidiExporter;
import com.sheetmusic4j.core.midi.MidiImporter;
import com.sheetmusic4j.core.model.Score;
import com.sheetmusic4j.core.musicxml.MusicXmlReader;
import com.sheetmusic4j.core.musicxml.MusicXmlWriter;

/**
 * Convenience facade that loads and saves a {@link Score} by dispatching on the
 * file extension:
 * <ul>
 *   <li>{@code .musicxml}, {@code .xml}, {@code .mxl} &rarr; MusicXML</li>
 *   <li>{@code .mid}, {@code .midi} &rarr; MIDI</li>
 *   <li>{@code .abc} &rarr; ABC music notation</li>
 * </ul>
 */
public final class ScoreFile {

    private ScoreFile() {
    }

    public static Score load(Path path) {
        return switch (format(path)) {
            case MUSICXML -> new MusicXmlReader().read(path);
            case MIDI -> new MidiImporter().fromMidi(path);
            case ABC -> new AbcReader().read(path);
        };
    }

    public static void save(Score score, Path path) {
        switch (format(path)) {
            case MUSICXML -> new MusicXmlWriter().write(score, path);
            case MIDI -> new MidiExporter().toMidi(score, path);
            case ABC -> new AbcWriter().write(score, path);
        }
    }

    private static Format format(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        String ext = dot >= 0 ? name.substring(dot + 1) : "";
        return switch (ext) {
            case "musicxml", "xml", "mxl" -> Format.MUSICXML;
            case "mid", "midi" -> Format.MIDI;
            case "abc" -> Format.ABC;
            default -> throw new IllegalArgumentException("Unsupported file extension: " + name);
        };
    }

    private enum Format {
        MUSICXML,
        MIDI,
        ABC
    }
}
