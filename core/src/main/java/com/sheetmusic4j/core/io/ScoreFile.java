package com.sheetmusic4j.core.io;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import com.sheetmusic4j.core.abc.AbcReader;
import com.sheetmusic4j.core.abc.AbcWriter;
import com.sheetmusic4j.core.guitarpro.GuitarProImporter;
import com.sheetmusic4j.core.lilypond.LilyPondReader;
import com.sheetmusic4j.core.lilypond.LilyPondWriter;
import com.sheetmusic4j.core.midi.MidiExporter;
import com.sheetmusic4j.core.midi.MidiImporter;
import com.sheetmusic4j.core.model.Score;
import com.sheetmusic4j.core.musicxml.MusicXmlReader;
import com.sheetmusic4j.core.musicxml.MusicXmlWriter;

/**
 * Convenience facade that loads and saves a {@link Score} by dispatching on the
 * file extension. The full list of supported extensions is enumerated by
 * {@link Format} and exposed through {@link #supportedExtensions()}, so callers
 * that need to build file filters or list only recognisable files (e.g. a
 * demo's resource tree) can query this facade rather than hardcoding their
 * own duplicate list.
 *
 * <ul>
 *   <li>{@code .musicxml}, {@code .xml}, {@code .mxl} &rarr; MusicXML</li>
 *   <li>{@code .mid}, {@code .midi} &rarr; MIDI</li>
 *   <li>{@code .abc} &rarr; ABC notation</li>
 *   <li>{@code .ly} &rarr; LilyPond</li>
 *   <li>{@code .gp} &rarr; Guitar Pro 8 (load only)</li>
 * </ul>
 */
public final class ScoreFile {

    private ScoreFile() {
    }

    /**
     * Load a {@link Score} from disk, dispatching to the reader that matches
     * the file's extension.
     *
     * @param path file to read
     * @return the parsed score
     * @throws IllegalArgumentException when the file extension is not one of
     *     {@link #supportedExtensions()}
     */
    public static Score load(Path path) {
        return switch (formatOf(path)) {
            case MUSICXML -> new MusicXmlReader().read(path);
            case MIDI -> new MidiImporter().fromMidi(path);
            case ABC -> new AbcReader().read(path);
            case LILYPOND -> new LilyPondReader().read(path);
            case GUITARPRO -> new GuitarProImporter().fromGuitarPro(path);
        };
    }

    /**
     * Save a {@link Score} to disk, dispatching to the writer that matches
     * the file's extension.
     *
     * @param score score to write
     * @param path  destination file
     * @throws IllegalArgumentException when the file extension is not one of
     *     {@link #supportedExtensions()}
     * @throws UnsupportedOperationException when the target format is
     *     {@link Format#writable() read-only} (e.g. Guitar Pro)
     */
    public static void save(Score score, Path path) {
        Format format = formatOf(path);
        if (!format.writable()) {
            throw new UnsupportedOperationException(
                    "Saving " + format + " files is not supported");
        }
        switch (format) {
            case MUSICXML -> new MusicXmlWriter().write(score, path);
            case MIDI -> new MidiExporter().toMidi(score, path);
            case ABC -> new AbcWriter().write(score, path);
            case LILYPOND -> new LilyPondWriter().write(score, path);
            case GUITARPRO -> {
                // Handled by the writable() guard above.
            }
        }
    }

    /**
     * The {@link Format} that owns the given path's extension, or throws
     * when the extension is not recognised.
     *
     * @param path file to classify
     * @return the matching format
     * @throws IllegalArgumentException when no format claims the extension
     */
    public static Format formatOf(Path path) {
        String ext = extensionOf(path);
        for (Format format : Format.values()) {
            if (format.extensions.contains(ext)) {
                return format;
            }
        }
        throw new IllegalArgumentException("Unsupported file extension: " + path.getFileName());
    }

    /**
     * All file extensions the facade knows about, in the order the
     * corresponding {@link Format} constants are declared. Extensions are
     * lowercase, without a leading dot (e.g. {@code "musicxml"},
     * {@code "abc"}, {@code "ly"}).
     *
     * @return an immutable list of supported extensions
     */
    public static List<String> supportedExtensions() {
        return Arrays.stream(Format.values())
                .flatMap(f -> f.extensions.stream())
                .toList();
    }

    private static String extensionOf(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot + 1) : "";
    }

    /**
     * Supported I/O formats and the file extensions they claim. The list of
     * extensions is authoritative — {@link ScoreFile#formatOf(Path)}
     * dispatches by consulting {@link #extensions()}, and
     * {@link ScoreFile#supportedExtensions()} is derived from it, so adding
     * a new format is a matter of adding one enum constant here.
     */
    public enum Format {
        /** MusicXML uncompressed ({@code .musicxml}/{@code .xml}) and compressed ({@code .mxl}). */
        MUSICXML(true, "musicxml", "xml", "mxl"),
        /** Standard MIDI Files. */
        MIDI(true, "mid", "midi"),
        /** ABC music notation. */
        ABC(true, "abc"),
        /** GNU LilyPond source. */
        LILYPOND(true, "ly"),
        /** Guitar Pro 8 (load only — no writer available). */
        GUITARPRO(false, "gp");

        private final boolean writable;
        private final List<String> extensions;

        Format(boolean writable, String... extensions) {
            this.writable = writable;
            this.extensions = List.of(extensions);
        }

        /**
         * Whether the facade can produce files of this format. Formats
         * without a writer (currently only {@link #GUITARPRO}) return
         * {@code false}; {@link ScoreFile#save(Score, Path)} rejects a save
         * targeting one of them with an
         * {@link UnsupportedOperationException}.
         *
         * @return {@code true} when both a reader and a writer are wired up
         */
        public boolean writable() {
            return writable;
        }

        /**
         * The lowercase file extensions (no leading dot) claimed by this
         * format. The first entry is the canonical/preferred extension.
         *
         * @return an immutable list of extensions
         */
        public List<String> extensions() {
            return extensions;
        }
    }
}
