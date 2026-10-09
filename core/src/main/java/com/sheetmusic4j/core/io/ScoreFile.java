package com.sheetmusic4j.core.io;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.function.Supplier;

import com.sheetmusic4j.core.abc.AbcReader;
import com.sheetmusic4j.core.abc.AbcWriter;
import com.sheetmusic4j.core.guitarpro.GuitarProImporter;
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
 *   <li>{@code .abc} &rarr; ABC notation</li>
 *   <li>{@code .gp} &rarr; Guitar Pro 8 (load only)</li>
 * </ul>
 * plus any {@link ScoreFormat} found via {@link ServiceLoader}.
 */
public final class ScoreFile {

    private static final List<ScoreFormat> FORMATS = discoverFormats();

    private ScoreFile() {
    }

    public static Score load(Path path) {
        return formatFor(path).reader().read(path);
    }

    public static void save(Score score, Path path) {
        ScoreFormat format = formatFor(path);
        format.writer()
                .orElseThrow(() -> new UnsupportedOperationException(
                        "Saving " + format.name() + " files is not supported"))
                .write(score, path);
    }

    /**
     * All known formats: the built-in ones first, then any contributed via
     * {@link ServiceLoader}.
     */
    public static List<ScoreFormat> formats() {
        return FORMATS;
    }

    /**
     * The format a file's extension maps to.
     *
     * @throws IllegalArgumentException when no format claims the extension
     */
    public static ScoreFormat formatFor(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        String ext = dot >= 0 ? name.substring(dot + 1) : "";
        for (ScoreFormat format : FORMATS) {
            if (format.extensions().contains(ext)) {
                return format;
            }
        }
        throw new IllegalArgumentException("Unsupported file extension: " + name);
    }

    private static List<ScoreFormat> discoverFormats() {
        List<ScoreFormat> formats = new ArrayList<>(List.of(
                new BuiltIn("MusicXML", Set.of("musicxml", "xml", "mxl"), MusicXmlReader::new, MusicXmlWriter::new),
                new BuiltIn("MIDI", Set.of("mid", "midi"), MidiImporter::new, MidiExporter::new),
                new BuiltIn("ABC", Set.of("abc"), AbcReader::new, AbcWriter::new),
                new BuiltIn("Guitar Pro", Set.of("gp"), GuitarProImporter::new, null)));
        ServiceLoader.load(ScoreFormat.class).forEach(formats::add);
        return List.copyOf(formats);
    }

    private record BuiltIn(String name, Set<String> extensions, Supplier<ScoreReader> readerFactory,
                           Supplier<ScoreWriter> writerFactory) implements ScoreFormat {

        @Override
        public ScoreReader reader() {
            return readerFactory.get();
        }

        @Override
        public Optional<ScoreWriter> writer() {
            return Optional.ofNullable(writerFactory).map(Supplier::get);
        }
    }
}
