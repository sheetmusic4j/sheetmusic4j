package com.sheetmusic4j.core.musicxml;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import com.sheetmusic4j.core.model.Accidental;
import com.sheetmusic4j.core.model.Attributes;
import com.sheetmusic4j.core.model.Beam;
import com.sheetmusic4j.core.model.Chord;
import com.sheetmusic4j.core.model.Clef;
import com.sheetmusic4j.core.model.ClefSign;
import com.sheetmusic4j.core.model.Duration;
import com.sheetmusic4j.core.model.KeySignature;
import com.sheetmusic4j.core.model.Measure;
import com.sheetmusic4j.core.model.Note;
import com.sheetmusic4j.core.model.NoteType;
import com.sheetmusic4j.core.model.Part;
import com.sheetmusic4j.core.model.Pitch;
import com.sheetmusic4j.core.model.Rest;
import com.sheetmusic4j.core.model.Score;
import com.sheetmusic4j.core.model.Step;
import com.sheetmusic4j.core.model.TimeSignature;

/**
 * Reads a MusicXML {@code score-partwise} document into a {@link Score}.
 *
 * <p>Only a renderable subset is handled: part list, measures, attributes
 * (divisions, key, time, clef), and notes (pitch, rest, chord, duration, type,
 * dots, ties). Unknown elements are ignored leniently.
 */
public final class MusicXmlReader {

    private final XMLInputFactory factory;

    public MusicXmlReader() {
        this.factory = XMLInputFactory.newFactory();
        // Harden against XXE: disable external entities and DTD loading.
        this.factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, Boolean.FALSE);
        this.factory.setProperty(XMLInputFactory.SUPPORT_DTD, Boolean.FALSE);
    }

    public Score read(Path path) {
        try (InputStream in = Files.newInputStream(path)) {
            return read(in);
        } catch (IOException e) {
            throw new MusicXmlException("Could not read MusicXML file: " + path, e);
        }
    }

    public Score read(InputStream in) {
        XMLStreamReader reader = null;
        try {
            InputStream xml = unwrapIfCompressed(in);
            reader = factory.createXMLStreamReader(xml);
            return parseDocument(reader);
        } catch (IOException | XMLStreamException e) {
            throw new MusicXmlException("Failed to parse MusicXML", e);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (XMLStreamException ignored) {
                    // ignore
                }
            }
        }
    }

    /**
     * Compressed MusicXML ({@code .mxl}) is a ZIP archive whose
     * {@code META-INF/container.xml} points at the actual score document;
     * detect the ZIP signature and unpack it before handing the stream to StAX.
     */
    private InputStream unwrapIfCompressed(InputStream in) throws IOException {
        BufferedInputStream buffered = new BufferedInputStream(in);
        buffered.mark(4);
        byte[] signature = buffered.readNBytes(4);
        buffered.reset();
        if (!isZipSignature(signature)) {
            return buffered;
        }

        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(buffered)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    entries.put(entry.getName(), zip.readAllBytes());
                }
            }
        }

        String rootFile = findRootFile(entries.get("META-INF/container.xml"));
        byte[] xmlBytes = rootFile != null ? entries.get(rootFile) : null;
        if (xmlBytes == null) {
            xmlBytes = entries.entrySet().stream()
                    .filter(e -> !e.getKey().startsWith("META-INF/")
                            && e.getKey().toLowerCase(Locale.ROOT).endsWith(".xml"))
                    .map(Map.Entry::getValue)
                    .findFirst()
                    .orElseThrow(() -> new MusicXmlException(
                            "No MusicXML root file found in compressed archive"));
        }
        return new ByteArrayInputStream(xmlBytes);
    }

    private static boolean isZipSignature(byte[] signature) {
        return signature.length >= 4
                && signature[0] == 'P' && signature[1] == 'K'
                && signature[2] == 3 && signature[3] == 4;
    }

    private String findRootFile(byte[] containerXml) {
        if (containerXml == null) {
            return null;
        }
        try {
            XMLStreamReader reader = factory.createXMLStreamReader(new ByteArrayInputStream(containerXml));
            try {
                while (reader.hasNext()) {
                    int event = reader.next();
                    if (event == XMLStreamConstants.START_ELEMENT && "rootfile".equals(reader.getLocalName())) {
                        return reader.getAttributeValue(null, "full-path");
                    }
                }
            } finally {
                reader.close();
            }
        } catch (XMLStreamException e) {
            return null;
        }
        return null;
    }

    private Score parseDocument(XMLStreamReader reader) throws XMLStreamException {
        Score.Builder score = Score.builder();
        Map<String, String> partNames = new LinkedHashMap<>();
        Map<String, Part.Builder> partBuilders = new LinkedHashMap<>();
        List<String> partOrder = new ArrayList<>();

        while (reader.hasNext()) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                String name = reader.getLocalName();
                switch (name) {
                    case "work-title" -> score.workTitle(readText(reader));
                    case "movement-title" -> score.movementTitle(readText(reader));
                    case "score-part" -> readScorePart(reader, partNames);
                    case "part" -> {
                        String id = reader.getAttributeValue(null, "id");
                        Part.Builder part = Part.builder(id).name(partNames.get(id));
                        partBuilders.put(id, part);
                        partOrder.add(id);
                        readPart(reader, part);
                    }
                    default -> { /* ignore */ }
                }
            }
        }

        for (String id : partOrder) {
            score.addPart(partBuilders.get(id).build());
        }
        return score.build();
    }

    private void readScorePart(XMLStreamReader reader, Map<String, String> partNames) throws XMLStreamException {
        String id = reader.getAttributeValue(null, "id");
        String name = null;
        while (reader.hasNext()) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                if ("part-name".equals(reader.getLocalName())) {
                    name = readText(reader);
                }
            } else if (event == XMLStreamConstants.END_ELEMENT && "score-part".equals(reader.getLocalName())) {
                break;
            }
        }
        if (id != null) {
            partNames.put(id, name);
        }
    }

    private void readPart(XMLStreamReader reader, Part.Builder part) throws XMLStreamException {
        // divisions carry over between measures within a part
        int[] divisions = {1};
        while (reader.hasNext()) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                if ("measure".equals(reader.getLocalName())) {
                    int number = parseIntOr(reader.getAttributeValue(null, "number"), 0);
                    part.addMeasure(readMeasure(reader, number, divisions));
                }
            } else if (event == XMLStreamConstants.END_ELEMENT && "part".equals(reader.getLocalName())) {
                break;
            }
        }
    }

    private Measure readMeasure(XMLStreamReader reader, int number, int[] divisions) throws XMLStreamException {
        Measure.Builder measure = Measure.builder(number);
        List<Note> pendingChord = new ArrayList<>();

        while (reader.hasNext()) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                switch (reader.getLocalName()) {
                    case "attributes" -> {
                        Attributes attributes = readAttributes(reader, divisions);
                        if (!attributes.isEmpty()) {
                            measure.attributes(attributes);
                        }
                    }
                    case "note" -> {
                        ParsedNote parsed = readNote(reader, divisions[0]);
                        if (parsed.chord && !pendingChord.isEmpty()) {
                            pendingChord.add(parsed.note);
                        } else {
                            flushChord(measure, pendingChord);
                            if (parsed.rest) {
                                measure.addElement(parsed.restElement);
                            } else {
                                pendingChord.add(parsed.note);
                            }
                        }
                    }
                    default -> { /* backup/forward/other ignored */ }
                }
            } else if (event == XMLStreamConstants.END_ELEMENT && "measure".equals(reader.getLocalName())) {
                break;
            }
        }
        flushChord(measure, pendingChord);
        return measure.build();
    }

    private void flushChord(Measure.Builder measure, List<Note> pendingChord) {
        if (pendingChord.isEmpty()) {
            return;
        }
        if (pendingChord.size() == 1) {
            measure.addElement(pendingChord.get(0));
        } else {
            measure.addElement(new Chord(new ArrayList<>(pendingChord)));
        }
        pendingChord.clear();
    }

    private Attributes readAttributes(XMLStreamReader reader, int[] divisions) throws XMLStreamException {
        Attributes.Builder builder = Attributes.builder();
        Integer beats = null;
        Integer beatType = null;
        Integer fifths = null;

        while (reader.hasNext()) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                switch (reader.getLocalName()) {
                    case "divisions" -> {
                        int d = parseIntOr(readText(reader), 1);
                        divisions[0] = d;
                        builder.divisions(d);
                    }
                    case "fifths" -> fifths = parseIntOr(readText(reader), 0);
                    case "beats" -> beats = parseIntOr(readText(reader), 4);
                    case "beat-type" -> beatType = parseIntOr(readText(reader), 4);
                    case "staves" -> builder.staves(parseIntOr(readText(reader), 1));
                    case "clef" -> {
                        Clef clef = readClef(reader);
                        if (clef != null) {
                            builder.addClef(clef);
                        }
                    }
                    default -> { /* ignore */ }
                }
            } else if (event == XMLStreamConstants.END_ELEMENT && "attributes".equals(reader.getLocalName())) {
                break;
            }
        }

        if (fifths != null) {
            builder.keySignature(new KeySignature(fifths));
        }
        if (beats != null && beatType != null) {
            builder.timeSignature(new TimeSignature(beats, beatType));
        }
        return builder.build();
    }

    private Clef readClef(XMLStreamReader reader) throws XMLStreamException {
        ClefSign clefSign = null;
        Integer clefLine = null;
        while (reader.hasNext()) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                switch (reader.getLocalName()) {
                    case "sign" -> clefSign = ClefSign.fromXml(readText(reader));
                    case "line" -> clefLine = parseIntOr(readText(reader), 2);
                    default -> { /* ignore */ }
                }
            } else if (event == XMLStreamConstants.END_ELEMENT && "clef".equals(reader.getLocalName())) {
                break;
            }
        }
        if (clefSign == null) {
            return null;
        }
        int defaultLine = switch (clefSign) {
            case F -> 4;
            case C -> 3;
            case G -> 2;
            default -> 2;
        };
        return new Clef(clefSign, clefLine != null ? clefLine : defaultLine);
    }

    private ParsedNote readNote(XMLStreamReader reader, int divisions) throws XMLStreamException {
        boolean rest = false;
        boolean chord = false;
        Step step = null;
        int octave = 4;
        int alter = 0;
        int duration = 0;
        NoteType type = null;
        int dots = 0;
        boolean tieStart = false;
        boolean tieStop = false;
        Accidental accidental = null;
        int staff = 1;
        java.util.List<Beam> beams = new ArrayList<>();

        while (reader.hasNext()) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                switch (reader.getLocalName()) {
                    case "chord" -> chord = true;
                    case "rest" -> rest = true;
                    case "step" -> step = Step.valueOf(readText(reader).trim().toUpperCase());
                    case "octave" -> octave = parseIntOr(readText(reader), 4);
                    case "alter" -> alter = parseIntOr(readText(reader), 0);
                    case "duration" -> duration = parseIntOr(readText(reader), 0);
                    case "type" -> type = NoteType.fromXml(readText(reader).trim());
                    case "dot" -> dots++;
                    case "staff" -> staff = parseIntOr(readText(reader), 1);
                    case "accidental" -> accidental = parseAccidental(readText(reader));
                    case "beam" -> {
                        int number = parseIntOr(reader.getAttributeValue(null, "number"), 1);
                        Beam.State state = Beam.State.fromXml(readText(reader));
                        beams.add(new Beam(number, state));
                    }
                    case "tie" -> {
                        String tieType = reader.getAttributeValue(null, "type");
                        if ("start".equals(tieType)) {
                            tieStart = true;
                        } else if ("stop".equals(tieType)) {
                            tieStop = true;
                        }
                    }
                    default -> { /* ignore */ }
                }
            } else if (event == XMLStreamConstants.END_ELEMENT && "note".equals(reader.getLocalName())) {
                break;
            }
        }

        Duration dur = new Duration(Math.max(duration, 0), divisions);
        if (rest) {
            Rest.Builder rb = Rest.builder().duration(dur).dots(dots);
            if (type != null) {
                rb.type(type);
            }
            return ParsedNote.rest(rb.build());
        }

        Note.Builder nb = Note.builder()
                .pitch(new Pitch(step != null ? step : Step.C, octave, alter))
                .duration(dur)
                .dots(dots)
                .tieStart(tieStart)
                .tieStop(tieStop)
                .staff(staff)
                .beams(beams);
        if (accidental != null) {
            nb.displayedAccidental(accidental);
        }
        if (type != null) {
            nb.type(type);
        }
        return ParsedNote.note(nb.build(), chord);
    }

    private static Accidental parseAccidental(String value) {
        if (value == null) {
            return null;
        }
        String v = value.trim().toLowerCase(Locale.ROOT);
        return switch (v) {
            case "sharp" -> Accidental.SHARP;
            case "flat" -> Accidental.FLAT;
            case "natural" -> Accidental.NATURAL;
            case "double-sharp", "sharp-sharp" -> Accidental.DOUBLE_SHARP;
            case "flat-flat", "double-flat" -> Accidental.DOUBLE_FLAT;
            default -> null;
        };
    }

    private String readText(XMLStreamReader reader) throws XMLStreamException {
        StringBuilder sb = new StringBuilder();
        while (reader.hasNext()) {
            int event = reader.next();
            if (event == XMLStreamConstants.CHARACTERS || event == XMLStreamConstants.CDATA) {
                sb.append(reader.getText());
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                break;
            }
        }
        return sb.toString().trim();
    }

    private static int parseIntOr(String value, int fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private record ParsedNote(Note note, boolean chord, boolean rest, Rest restElement) {
        static ParsedNote note(Note note, boolean chord) {
            return new ParsedNote(note, chord, false, null);
        }

        static ParsedNote rest(Rest rest) {
            return new ParsedNote(null, false, true, rest);
        }
    }
}
