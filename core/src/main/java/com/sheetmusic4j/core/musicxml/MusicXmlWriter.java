package com.sheetmusic4j.core.musicxml;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import com.sheetmusic4j.core.model.Accidental;
import com.sheetmusic4j.core.model.Attributes;
import com.sheetmusic4j.core.model.Beam;
import com.sheetmusic4j.core.model.Chord;
import com.sheetmusic4j.core.model.Clef;
import com.sheetmusic4j.core.model.KeySignature;
import com.sheetmusic4j.core.model.Measure;
import com.sheetmusic4j.core.model.MusicElement;
import com.sheetmusic4j.core.model.Note;
import com.sheetmusic4j.core.model.Part;
import com.sheetmusic4j.core.model.Pitch;
import com.sheetmusic4j.core.model.Rest;
import com.sheetmusic4j.core.model.Score;
import com.sheetmusic4j.core.model.TimeSignature;

/**
 * Writes a {@link Score} as a MusicXML {@code score-partwise} document.
 * Produces the subset understood by {@link MusicXmlReader} so that a
 * read/write/read round-trip is structurally stable.
 */
public final class MusicXmlWriter {

    private static final String NEWLINE = System.lineSeparator();
    private static final String INDENT = "  ";

    private final XMLOutputFactory factory = XMLOutputFactory.newFactory();

    public void write(Score score, Path path) {
        try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(path))) {
            write(score, out);
        } catch (IOException e) {
            throw new MusicXmlException("Could not write MusicXML file: " + path, e);
        }
    }

    public void write(Score score, OutputStream out) {
        try {
            XMLStreamWriter writer = factory.createXMLStreamWriter(out, StandardCharsets.UTF_8.name());
            writer.writeStartDocument(StandardCharsets.UTF_8.name(), "1.0");
            writer.writeCharacters(NEWLINE);
            writer.writeDTD("<!DOCTYPE score-partwise PUBLIC \"-//Recordare//DTD MusicXML 4.0 Partwise//EN\" "
                    + "\"http://www.musicxml.org/dtds/partwise.dtd\">");
            IndentingWriter w = new IndentingWriter(writer);
            writeScore(w, score);
            w.flush();
            writer.writeEndDocument();
            writer.close();
        } catch (XMLStreamException e) {
            throw new MusicXmlException("Failed to write MusicXML", e);
        }
    }

    private void writeScore(IndentingWriter w, Score score) throws XMLStreamException {
        w.start("score-partwise", "version", "4.0");

        Optional<String> work = score.workTitle();
        if (work.isPresent()) {
            w.start("work");
            w.textElement("work-title", work.get());
            w.end("work");
        }
        score.movementTitle().ifPresent(t -> w.textElementUnchecked("movement-title", t));

        w.start("part-list");
        for (Part part : score.parts()) {
            w.start("score-part", "id", part.id());
            w.textElement("part-name", part.name() != null ? part.name() : part.id());
            w.end("score-part");
        }
        w.end("part-list");

        for (Part part : score.parts()) {
            writePart(w, part);
        }
        w.end("score-partwise");
    }

    private void writePart(IndentingWriter w, Part part) throws XMLStreamException {
        w.start("part", "id", part.id());
        for (Measure measure : part.measures()) {
            writeMeasure(w, measure);
        }
        w.end("part");
    }

    private void writeMeasure(IndentingWriter w, Measure measure) throws XMLStreamException {
        w.start("measure", "number", Integer.toString(measure.number()));
        measure.attributes().ifPresent(a -> writeAttributes(w, a));
        for (MusicElement element : measure.elements()) {
            if (element instanceof Note note) {
                writeNote(w, note, false);
            } else if (element instanceof Rest rest) {
                writeRest(w, rest);
            } else if (element instanceof Chord chord) {
                List<Note> notes = chord.notes();
                for (int i = 0; i < notes.size(); i++) {
                    writeNote(w, notes.get(i), i > 0);
                }
            }
        }
        w.end("measure");
    }

    private void writeAttributes(IndentingWriter w, Attributes attributes) {
        try {
            w.start("attributes");
            if (attributes.divisions().isPresent()) {
                w.textElement("divisions", Integer.toString(attributes.divisions().get()));
            }
            if (attributes.keySignature().isPresent()) {
                KeySignature key = attributes.keySignature().get();
                w.start("key");
                w.textElement("fifths", Integer.toString(key.fifths()));
                w.end("key");
            }
            if (attributes.timeSignature().isPresent()) {
                TimeSignature time = attributes.timeSignature().get();
                w.start("time");
                w.textElement("beats", Integer.toString(time.beats()));
                w.textElement("beat-type", Integer.toString(time.beatType()));
                w.end("time");
            }
            if (attributes.staves().isPresent()) {
                w.textElement("staves", Integer.toString(attributes.staves().get()));
            }
            List<Clef> clefs = attributes.clefs();
            if (!clefs.isEmpty()) {
                for (int i = 0; i < clefs.size(); i++) {
                    Clef clef = clefs.get(i);
                    if (clefs.size() > 1) {
                        w.start("clef", "number", Integer.toString(i + 1));
                    } else {
                        w.start("clef");
                    }
                    w.textElement("sign", clef.sign().xmlValue());
                    w.textElement("line", Integer.toString(clef.line()));
                    w.end("clef");
                }
            } else if (attributes.clef().isPresent()) {
                Clef clef = attributes.clef().get();
                w.start("clef");
                w.textElement("sign", clef.sign().xmlValue());
                w.textElement("line", Integer.toString(clef.line()));
                w.end("clef");
            }
            w.end("attributes");
        } catch (XMLStreamException e) {
            throw new MusicXmlException("Failed to write attributes", e);
        }
    }

    private void writeNote(IndentingWriter w, Note note, boolean chord) {
        try {
            w.start("note");
            if (chord) {
                w.emptyElement("chord");
            }
            Pitch pitch = note.pitch();
            w.start("pitch");
            w.textElement("step", pitch.step().name());
            if (pitch.alter() != 0) {
                w.textElement("alter", Integer.toString(pitch.alter()));
            }
            w.textElement("octave", Integer.toString(pitch.octave()));
            w.end("pitch");
            w.textElement("duration", Integer.toString(note.duration().value()));
            if (note.tieStart()) {
                w.startEmptyWithAttr("tie", "type", "start");
            }
            if (note.tieStop()) {
                w.startEmptyWithAttr("tie", "type", "stop");
            }
            w.textElement("type", note.type().xmlValue());
            for (int i = 0; i < note.dots(); i++) {
                w.emptyElement("dot");
            }
            if (note.displayedAccidental().isPresent()) {
                w.textElement("accidental", accidentalXml(note.displayedAccidental().get()));
            }
            if (note.staff() > 1) {
                w.textElement("staff", Integer.toString(note.staff()));
            }
            for (Beam beam : note.beams()) {
                w.startBeam(beam.number(), beam.state().xmlValue());
            }
            w.end("note");
        } catch (XMLStreamException e) {
            throw new MusicXmlException("Failed to write note", e);
        }
    }

    private void writeRest(IndentingWriter w, Rest rest) {
        try {
            w.start("note");
            w.emptyElement("rest");
            w.textElement("duration", Integer.toString(rest.duration().value()));
            w.textElement("type", rest.type().xmlValue());
            for (int i = 0; i < rest.dots(); i++) {
                w.emptyElement("dot");
            }
            w.end("note");
        } catch (XMLStreamException e) {
            throw new MusicXmlException("Failed to write rest", e);
        }
    }

    /**
     * Small helper around {@link XMLStreamWriter} that adds newlines and indentation
     * so the output is human-readable.
     */
    private static final class IndentingWriter {
        private final XMLStreamWriter writer;
        private int depth;

        IndentingWriter(XMLStreamWriter writer) {
            this.writer = writer;
        }

        void indent() throws XMLStreamException {
            writer.writeCharacters(NEWLINE);
            for (int i = 0; i < depth; i++) {
                writer.writeCharacters(INDENT);
            }
        }

        void start(String name, String... attrs) throws XMLStreamException {
            indent();
            writer.writeStartElement(name);
            for (int i = 0; i + 1 < attrs.length; i += 2) {
                writer.writeAttribute(attrs[i], attrs[i + 1]);
            }
            depth++;
        }

        void end(String name) throws XMLStreamException {
            depth--;
            indent();
            writer.writeEndElement();
        }

        void textElement(String name, String text) throws XMLStreamException {
            indent();
            writer.writeStartElement(name);
            writer.writeCharacters(text);
            writer.writeEndElement();
        }

        void textElementUnchecked(String name, String text) {
            try {
                textElement(name, text);
            } catch (XMLStreamException e) {
                throw new MusicXmlException("Failed to write element " + name, e);
            }
        }

        void emptyElement(String name) throws XMLStreamException {
            indent();
            writer.writeEmptyElement(name);
        }

        void startEmptyWithAttr(String name, String attr, String value) throws XMLStreamException {
            indent();
            writer.writeEmptyElement(name);
            writer.writeAttribute(attr, value);
        }

        void flush() throws XMLStreamException {
            writer.writeCharacters(NEWLINE);
            writer.flush();
        }

        void startBeam(int number, String state) throws XMLStreamException {
            indent();
            writer.writeStartElement("beam");
            writer.writeAttribute("number", Integer.toString(number));
            writer.writeCharacters(state);
            writer.writeEndElement();
        }
        }

        private static String accidentalXml(Accidental accidental) {
        return switch (accidental) {
            case SHARP -> "sharp";
            case FLAT -> "flat";
            case NATURAL -> "natural";
            case DOUBLE_SHARP -> "double-sharp";
            case DOUBLE_FLAT -> "flat-flat";
        };
        }
        }
