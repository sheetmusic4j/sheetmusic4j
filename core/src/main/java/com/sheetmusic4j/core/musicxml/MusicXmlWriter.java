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
import com.sheetmusic4j.core.model.Creator;
import com.sheetmusic4j.core.model.Direction;
import com.sheetmusic4j.core.model.DirectionType;
import com.sheetmusic4j.core.model.GroupSymbol;
import com.sheetmusic4j.core.model.Harmony;
import com.sheetmusic4j.core.model.KeySignature;
import com.sheetmusic4j.core.model.Lyric;
import com.sheetmusic4j.core.model.Measure;
import com.sheetmusic4j.core.model.MusicElement;
import com.sheetmusic4j.core.model.Note;
import com.sheetmusic4j.core.model.Part;
import com.sheetmusic4j.core.model.PartGroup;
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

        List<Creator> creators = score.creators();
        if (!creators.isEmpty()) {
            w.start("identification");
            for (Creator creator : creators) {
                w.textElementWithAttr("creator", "type", creator.role(), creator.name());
            }
            w.end("identification");
        }

        writePartList(w, score);

        for (Part part : score.parts()) {
            writePart(w, part);
        }
        w.end("score-partwise");
    }

    /**
     * Emit the {@code <part-list>} block, interleaving {@code <part-group>}
     * start/stop sentinels around the {@code <score-part>} entries so the
     * nesting captured in {@link Score#partGroups()} round-trips through the
     * writer. Groups are emitted in document order (outer first); stops
     * fire in reverse start-order so nesting closes LIFO.
     */
    private void writePartList(IndentingWriter w, Score score) throws XMLStreamException {
        w.start("part-list");
        // Sort defensively by (startPartIndex ascending, endPartIndex
        // descending) so nested inner groups always follow their
        // enclosing outer group.
        List<PartGroup> groups = new java.util.ArrayList<>(score.partGroups());
        groups.sort((a, b) -> {
            int cmp = Integer.compare(a.startPartIndex(), b.startPartIndex());
            if (cmp != 0) {
                return cmp;
            }
            return Integer.compare(b.endPartIndex(), a.endPartIndex());
        });
        List<Part> parts = score.parts();
        for (int i = 0; i < parts.size(); i++) {
            for (PartGroup group : groups) {
                if (group.startPartIndex() == i) {
                    writePartGroupStart(w, group);
                }
            }
            Part part = parts.get(i);
            w.start("score-part", "id", part.id());
            w.textElement("part-name", part.name() != null ? part.name() : part.id());
            if (part.abbreviation() != null) {
                w.textElement("part-abbreviation", part.abbreviation());
            }
            w.end("score-part");
            // Close inner groups first: iterate in reverse start-order.
            for (int j = groups.size() - 1; j >= 0; j--) {
                PartGroup group = groups.get(j);
                if (group.endPartIndex() == i) {
                    writePartGroupStop(w, group);
                }
            }
        }
        w.end("part-list");
    }

    /**
     * Emit a single {@code <part-group type="start">} block with any
     * declared {@code <group-name>}, {@code <group-abbreviation>},
     * {@code <group-symbol>}, and {@code <group-barline>} children.
     */
    private void writePartGroupStart(IndentingWriter w, PartGroup group) throws XMLStreamException {
        w.start("part-group",
                "number", Integer.toString(group.number()),
                "type", "start");
        if (group.name() != null && !group.name().isBlank()) {
            w.textElement("group-name", group.name());
        }
        if (group.abbreviation() != null && !group.abbreviation().isBlank()) {
            w.textElement("group-abbreviation", group.abbreviation());
        }
        if (group.symbol() != GroupSymbol.NONE) {
            w.textElement("group-symbol", group.symbol().xmlValue());
        }
        w.textElement("group-barline", group.groupBarline() ? "yes" : "no");
        w.end("part-group");
    }

    /**
     * Emit a single self-closed {@code <part-group type="stop"/>} tag.
     */
    private void writePartGroupStop(IndentingWriter w, PartGroup group) throws XMLStreamException {
        w.indent();
        w.writer.writeEmptyElement("part-group");
        w.writer.writeAttribute("number", Integer.toString(group.number()));
        w.writer.writeAttribute("type", "stop");
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
            } else if (element instanceof Direction direction) {
                writeDirection(w, direction);
            } else if (element instanceof Harmony harmony) {
                writeHarmony(w, harmony);
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
            for (Lyric lyric : note.lyrics()) {
                w.start("lyric", "number", Integer.toString(lyric.verse()));
                w.textElement("syllabic", lyric.syllabic().xmlValue());
                w.textElement("text", lyric.text());
                w.end("lyric");
            }
            w.end("note");
        } catch (XMLStreamException e) {
            throw new MusicXmlException("Failed to write note", e);
        }
    }

    private void writeDirection(IndentingWriter w, Direction direction) {
        try {
            String placementAttr = direction.placement() != null ? direction.placement().xmlValue() : null;
            if (placementAttr != null) {
                w.start("direction", "placement", placementAttr);
            } else {
                w.start("direction");
            }
            w.start("direction-type");
            DirectionType type = direction.type();
            if (type instanceof DirectionType.Words words) {
                writeWords(w, words);
            } else if (type instanceof DirectionType.Metronome metronome) {
                writeMetronome(w, metronome);
            } else if (type instanceof DirectionType.Dynamic dynamic) {
                writeDynamics(w, dynamic);
            } else if (type instanceof DirectionType.Rehearsal rehearsal) {
                writeRehearsal(w, rehearsal);
            }
            w.end("direction-type");
            w.end("direction");
        } catch (XMLStreamException e) {
            throw new MusicXmlException("Failed to write direction", e);
        }
    }

    private void writeWords(IndentingWriter w, DirectionType.Words words) throws XMLStreamException {
        List<String> attrs = new java.util.ArrayList<>(4);
        if (words.italic()) {
            attrs.add("font-style");
            attrs.add("italic");
        }
        if (words.bold()) {
            attrs.add("font-weight");
            attrs.add("bold");
        }
        w.textElementWithAttrs("words", attrs, words.text());
    }

    private void writeMetronome(IndentingWriter w, DirectionType.Metronome metronome) throws XMLStreamException {
        w.start("metronome");
        w.textElement("beat-unit", metronome.beatUnit().xmlValue());
        if (metronome.dotted()) {
            w.emptyElement("beat-unit-dot");
        }
        w.textElement("per-minute", Integer.toString(metronome.perMinute()));
        w.end("metronome");
    }

    private void writeDynamics(IndentingWriter w, DirectionType.Dynamic dynamic) throws XMLStreamException {
        w.start("dynamics");
        w.emptyElement(dynamic.mark().xmlValue());
        w.end("dynamics");
    }

    /**
     * Emit a {@code <rehearsal>label</rehearsal>} element. Font styling is
     * an engraving convention and not persisted at the model level.
     */
    private void writeRehearsal(IndentingWriter w, DirectionType.Rehearsal rehearsal) throws XMLStreamException {
        w.textElement("rehearsal", rehearsal.label());
    }

    /**
     * Emit a MusicXML {@code <harmony>} block for the given {@link Harmony}.
     * Follows the DTD-mandated {@code <root>} → {@code <kind>} →
     * {@code <bass>} child order; {@code <root-alter>} / {@code <bass-alter>}
     * are omitted when the alter is zero (canonical form).
     */
    private void writeHarmony(IndentingWriter w, Harmony harmony) {
        try {
            w.start("harmony");
            w.start("root");
            w.textElement("root-step", harmony.root().step().name());
            if (harmony.root().alter() != 0) {
                w.textElement("root-alter", Integer.toString(harmony.root().alter()));
            }
            w.end("root");
            String textOverride = harmony.textOverride().orElse(null);
            String kindXml = harmony.kind().xmlValue();
            if (textOverride != null) {
                w.textElementWithAttr("kind", "text", textOverride, kindXml);
            } else {
                w.textElement("kind", kindXml);
            }
            if (harmony.bass().isPresent()) {
                Harmony.Bass bass = harmony.bass().get();
                w.start("bass");
                w.textElement("bass-step", bass.step().name());
                if (bass.alter() != 0) {
                    w.textElement("bass-alter", Integer.toString(bass.alter()));
                }
                w.end("bass");
            }
            w.end("harmony");
        } catch (XMLStreamException e) {
            throw new MusicXmlException("Failed to write harmony", e);
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

        void textElementWithAttr(String name, String attr, String attrValue, String text) {
            try {
                indent();
                writer.writeStartElement(name);
                writer.writeAttribute(attr, attrValue);
                writer.writeCharacters(text);
                writer.writeEndElement();
            } catch (XMLStreamException e) {
                throw new MusicXmlException("Failed to write element " + name, e);
            }
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

        /**
         * Write a text element with a variable number of attribute pairs.
         * {@code attrs} must contain an even number of entries: name, value,
         * name, value, ...
         */
        void textElementWithAttrs(String name, List<String> attrs, String text) throws XMLStreamException {
            indent();
            writer.writeStartElement(name);
            for (int i = 0; i + 1 < attrs.size(); i += 2) {
                writer.writeAttribute(attrs.get(i), attrs.get(i + 1));
            }
            writer.writeCharacters(text);
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
