package com.sheetmusic4j.core.guitarpro;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.sheetmusic4j.core.model.Attributes;
import com.sheetmusic4j.core.model.Beam;
import com.sheetmusic4j.core.model.Chord;
import com.sheetmusic4j.core.model.Clef;
import com.sheetmusic4j.core.model.ClefSign;
import com.sheetmusic4j.core.model.Creator;
import com.sheetmusic4j.core.model.Duration;
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
 * Imports a Guitar Pro 8 file ({@code .gp}) into a {@link Score}.
 *
 * <p>A {@code .gp} file is a plain ZIP archive whose main payload is a
 * {@code score.gpif} document &mdash; the GPIF XML format shared by Guitar Pro 7
 * and 8. This importer reads it with the JDK only (no third-party
 * dependency): {@link ZipFile} to extract the GPIF entry and DOM to
 * parse it. Older Guitar Pro formats ({@code .gp3}/{@code .gp4}/{@code .gp5}
 * binary, {@code .gpx}) are intentionally not supported.
 *
 * <p>The mapping mirrors {@link com.sheetmusic4j.core.midi.MidiImporter}:
 * <ul>
 *   <li>Each GPIF {@code Track} &rarr; one {@link Part} ({@code P1},
 *       {@code P2}, &hellip;). All of a track's staves are imported into the
 *       same part with full multi-staff (grand-staff) support: notes/rests are
 *       tagged with a 1-based staff index and the first measure emits
 *       {@link Attributes#staves()} plus one clef per staff.</li>
 *   <li>Each {@code MasterBar} &rarr; one {@link Measure}.</li>
 *   <li>Durations use {@value #TICKS_PER_QUARTER} divisions per quarter note,
 *       derived from each beat's GPIF rhythm (note value + augmentation dots +
 *       tuplet).</li>
 *   <li>Pitches are resolved from the tablature: string tuning + fret + capo,
 *       then spelled with {@link Pitch#fromMidiNumber(int, KeySignature)}.
 *       Standard-notation pitch only &mdash; the fret/string tablature itself
 *       is not preserved.</li>
 * </ul>
 *
 * <p>GuitarPro bars can carry several concurrent voices per staff. The model
 * has no voice concept, so only the primary (first present) voice of each staff
 * is imported; secondary voices are dropped.
 *
 * <p>GPIF stores no explicit beam grouping (Guitar Pro beams automatically by
 * beat), so beams are reconstructed here: consecutive beamable beats (eighth or
 * shorter, uninterrupted by a rest or longer note) are grouped within each beat
 * of the measure's time signature and tagged with {@link Beam} entries so the
 * engraver draws beams rather than individual flags.
 *
 * <p>Loading is one-directional: there is no GuitarPro export.
 */
public final class GuitarProImporter {

    private static final Logger logger = LoggerFactory.getLogger(GuitarProImporter.class);

    /** Divisions (ticks) per quarter note used for imported durations. */
    private static final int TICKS_PER_QUARTER = 960;

    /**
     * Read and import a Guitar Pro 8 ({@code .gp}) file.
     *
     * @param path the file to read
     * @return the imported score
     * @throws GuitarProException when the file cannot be read or parsed
     */
    public Score fromGuitarPro(Path path) {
        try {
            return fromGpif(parseXml(extractGpif(path)));
        } catch (IOException e) {
            logger.error("Could not read GuitarPro file: {}", path, e);
            throw new GuitarProException("Could not read GuitarPro file: " + path, e);
        }
    }

    /**
     * Read and import a Guitar Pro 8 ({@code .gp}) stream.
     *
     * @param in the stream to read (fully consumed)
     * @return the imported score
     * @throws GuitarProException when the stream cannot be read or parsed
     */
    public Score fromGuitarPro(InputStream in) {
        Path temp = null;
        try {
            // ZipFile needs a seekable file, so spool the stream to a temp file.
            temp = Files.createTempFile("sheetmusic4j-guitarpro-", ".gp");
            Files.copy(in, temp, StandardCopyOption.REPLACE_EXISTING);
            return fromGpif(parseXml(extractGpif(temp)));
        } catch (IOException e) {
            logger.error("Could not read GuitarPro stream", e);
            throw new GuitarProException("Could not read GuitarPro stream", e);
        } finally {
            if (temp != null) {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException ignored) {
                    // best effort cleanup of the temporary file
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Container + XML plumbing
    // ------------------------------------------------------------------

    private byte[] extractGpif(Path path) throws IOException {
        // Guitar Pro .gp is a standard ZIP. Use ZipFile (central-directory
        // based) rather than ZipInputStream: the streaming reader validates
        // local-header sizes and fails on GP's data-descriptor entries with
        // "invalid entry size".
        try (ZipFile zip = new ZipFile(path.toFile())) {
            ZipEntry entry = findGpifEntry(zip);
            if (entry == null) {
                throw new GuitarProException("Not a Guitar Pro 8 (.gp) file: no score.gpif entry found");
            }
            try (InputStream in = zip.getInputStream(entry)) {
                return in.readAllBytes();
            }
        }
    }

    private ZipEntry findGpifEntry(ZipFile zip) {
        ZipEntry fallback = null;
        Enumeration<? extends ZipEntry> entries = zip.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            if (entry.isDirectory()) {
                continue;
            }
            String name = entry.getName().toLowerCase(Locale.ROOT);
            if (name.endsWith("score.gpif")) {
                return entry;
            }
            if (name.endsWith(".gpif") && fallback == null) {
                fallback = entry;
            }
        }
        return fallback;
    }

    private Element parseXml(byte[] xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(new ByteArrayInputStream(xml));
            return document.getDocumentElement();
        } catch (Exception e) {
            throw new GuitarProException("Could not parse Guitar Pro GPIF XML", e);
        }
    }

    // ------------------------------------------------------------------
    // GPIF -> Score translation
    // ------------------------------------------------------------------

    /**
     * Translate a parsed GPIF document (its {@code <GPIF>} root element) into
     * the sheetmusic4j model. Exposed for unit testing of the mapping in
     * isolation.
     *
     * @param root the GPIF root element
     * @return the imported score
     */
    Score fromGpif(Element root) {
        Map<Integer, Element> bars = indexById(childElements(firstChild(root, "Bars"), "Bar"));
        Map<Integer, Element> voices = indexById(childElements(firstChild(root, "Voices"), "Voice"));
        Map<Integer, Element> beats = indexById(childElements(firstChild(root, "Beats"), "Beat"));
        Map<Integer, Element> notes = indexById(childElements(firstChild(root, "Notes"), "Note"));
        Map<Integer, Element> rhythms = indexById(childElements(firstChild(root, "Rhythms"), "Rhythm"));
        List<Element> masterBars = childElements(firstChild(root, "MasterBars"), "MasterBar");
        List<Element> tracks = childElements(firstChild(root, "Tracks"), "Track");

        // Per-track staves and their offset into each MasterBar's flat bar list.
        List<List<StaffInfo>> trackStaves = new ArrayList<>();
        int[] barOffset = new int[tracks.size()];
        int running = 0;
        for (int t = 0; t < tracks.size(); t++) {
            List<StaffInfo> staves = staffInfos(tracks.get(t));
            trackStaves.add(staves);
            barOffset[t] = running;
            running += staves.size();
        }

        Score.Builder score = Score.builder();
        Element scoreElement = firstChild(root, "Score");
        if (scoreElement != null) {
            String title = textOf(firstChild(scoreElement, "Title"));
            if (title != null && !title.isBlank()) {
                score.workTitle(title);
            }
            score.addCreator(Creator.of("composer", textOf(firstChild(scoreElement, "Music"))));
            score.addCreator(Creator.of("lyricist", textOf(firstChild(scoreElement, "Words"))));
        }

        for (int t = 0; t < tracks.size(); t++) {
            Element track = tracks.get(t);
            List<StaffInfo> staves = trackStaves.get(t);
            int numberOfStaves = staves.size();
            Part.Builder part = Part.builder("P" + (t + 1))
                    .name(textOf(firstChild(track, "Name")))
                    .abbreviation(textOf(firstChild(track, "ShortName")));

            KeySignature partKey = masterBars.isEmpty()
                    ? KeySignature.cMajor()
                    : keyFrom(masterBars.get(0));

            // GP only stores a Time on master bars where it changes; carry the
            // last-seen signature forward so beam grouping stays correct.
            TimeSignature currentTime = TimeSignature.fourFour();

            for (int m = 0; m < masterBars.size(); m++) {
                Element masterBar = masterBars.get(m);
                int[] barIds = intList(textOf(firstChild(masterBar, "Bars")));
                Measure.Builder measure = Measure.builder(m + 1);

                TimeSignature explicit = explicitTime(masterBar);
                if (explicit != null) {
                    currentTime = explicit;
                }

                if (m == 0) {
                    Attributes.Builder attributes = Attributes.builder()
                            .divisions(TICKS_PER_QUARTER)
                            .keySignature(partKey)
                            .timeSignature(currentTime)
                            .staves(numberOfStaves);
                    for (int s = 0; s < numberOfStaves; s++) {
                        attributes.addClef(clefFrom(barAt(bars, barIds, barOffset[t] + s)));
                    }
                    measure.attributes(attributes.build());
                }

                for (int s = 0; s < numberOfStaves; s++) {
                    Element bar = barAt(bars, barIds, barOffset[t] + s);
                    if (bar == null) {
                        continue;
                    }
                    Element voice = primaryVoice(bar, voices);
                    if (voice == null) {
                        continue;
                    }
                    List<BeatItem> items = new ArrayList<>();
                    for (int beatId : intList(textOf(firstChild(voice, "Beats")))) {
                        BeatItem item = beatItem(beats.get(beatId), rhythms, notes,
                                staves.get(s), s + 1, partKey);
                        if (item != null) {
                            items.add(item);
                        }
                    }
                    assignBeams(items, currentTime);
                    for (BeatItem item : items) {
                        measure.addElement(toElement(item));
                    }
                }

                part.addMeasure(measure.build());
            }

            score.addPart(part.build());
        }

        return score.build();
    }

    /**
     * Resolve one GPIF beat into pitches + duration + beam level, without yet
     * committing beam entries (those are assigned once the whole staff's beat
     * sequence is known). Returns {@code null} only for a missing beat.
     */
    private BeatItem beatItem(Element beat, Map<Integer, Element> rhythms,
                              Map<Integer, Element> notesById, StaffInfo staff, int staffNumber,
                              KeySignature key) {
        if (beat == null) {
            return null;
        }
        Element rhythmRef = firstChild(beat, "Rhythm");
        Integer rhythmId = rhythmRef == null ? null : attrInt(rhythmRef, "ref");
        Element rhythm = rhythmId == null ? null : rhythms.get(rhythmId);
        Duration duration = durationFrom(rhythm);
        int beamLevels = beamLevels(rhythm);

        List<Pitch> pitches = new ArrayList<>();
        for (int noteId : intList(textOf(firstChild(beat, "Notes")))) {
            Integer midi = midiOf(notesById.get(noteId), staff);
            if (midi != null) {
                pitches.add(Pitch.fromMidiNumber(clampMidi(midi), key));
            }
        }
        return new BeatItem(pitches, duration, staffNumber, beamLevels);
    }

    /** Build the model element for a resolved beat, attaching any beams. */
    private MusicElement toElement(BeatItem item) {
        if (item.isRest()) {
            return Rest.builder().duration(item.duration).staff(item.staffNumber).build();
        }
        if (item.pitches.size() == 1) {
            return buildNote(item.pitches.get(0), item);
        }
        List<Note> chordNotes = new ArrayList<>(item.pitches.size());
        for (Pitch pitch : item.pitches) {
            chordNotes.add(buildNote(pitch, item));
        }
        return new Chord(chordNotes);
    }

    private Note buildNote(Pitch pitch, BeatItem item) {
        Note.Builder note = Note.builder()
                .pitch(pitch)
                .duration(item.duration)
                .staff(item.staffNumber);
        for (Beam beam : item.beams) {
            note.addBeam(beam);
        }
        return note.build();
    }

    // ------------------------------------------------------------------
    // Beaming (reconstructed: GPIF has no explicit beam grouping)
    // ------------------------------------------------------------------

    /**
     * Assign beam entries to a staff's ordered beats. Consecutive beamable
     * beats that begin within the same beat of the time signature are joined
     * into one beamed group; rests and quarter-or-longer notes break the group.
     */
    private void assignBeams(List<BeatItem> items, TimeSignature time) {
        int groupTicks = beamGroupTicks(time);
        int n = items.size();
        int[] startPos = new int[n];
        int cursor = 0;
        for (int i = 0; i < n; i++) {
            startPos[i] = cursor;
            cursor += items.get(i).duration.value();
        }
        int i = 0;
        while (i < n) {
            if (!items.get(i).beamable()) {
                i++;
                continue;
            }
            int group = startPos[i] / groupTicks;
            int j = i + 1;
            while (j < n && items.get(j).beamable() && startPos[j] / groupTicks == group) {
                j++;
            }
            if (j - i >= 2) {
                applyBeamRun(items.subList(i, j));
            }
            i = j;
        }
    }

    /**
     * Assign begin/continue/end beam states across one beamed run, per beam
     * level. A secondary beam that occurs in isolation (e.g. the sixteenth of a
     * dotted-eighth + sixteenth pair) becomes a hook pointing back toward the
     * run when it has a predecessor, otherwise forward.
     */
    private void applyBeamRun(List<BeatItem> run) {
        int maxLevel = 0;
        for (BeatItem item : run) {
            maxLevel = Math.max(maxLevel, item.beamLevels);
        }
        int size = run.size();
        for (int level = 1; level <= maxLevel; level++) {
            int k = 0;
            while (k < size) {
                if (run.get(k).beamLevels < level) {
                    k++;
                    continue;
                }
                int start = k;
                int end = k;
                while (end + 1 < size && run.get(end + 1).beamLevels >= level) {
                    end++;
                }
                if (end == start) {
                    Beam.State state = start > 0 ? Beam.State.BACKWARD_HOOK : Beam.State.FORWARD_HOOK;
                    run.get(start).beams.add(new Beam(level, state));
                } else {
                    run.get(start).beams.add(new Beam(level, Beam.State.BEGIN));
                    for (int x = start + 1; x < end; x++) {
                        run.get(x).beams.add(new Beam(level, Beam.State.CONTINUE));
                    }
                    run.get(end).beams.add(new Beam(level, Beam.State.END));
                }
                k = end + 1;
            }
        }
    }

    /** Ticks per beam group: one time-signature beat, or a dotted beat in compound meter. */
    private int beamGroupTicks(TimeSignature time) {
        int beatType = time.beatType();
        int quarterUnits = TICKS_PER_QUARTER * 4;
        int denomBeat = beatType > 0 && quarterUnits % beatType == 0
                ? quarterUnits / beatType
                : TICKS_PER_QUARTER;
        if (beatType >= 8 && time.beats() % 3 == 0) {
            return denomBeat * 3;
        }
        return denomBeat;
    }

    /** Number of beams a rhythm's base note value carries (0 for quarter or longer, or a rest). */
    private int beamLevels(Element rhythm) {
        if (rhythm == null) {
            return 0;
        }
        double base = baseTicks(textOf(firstChild(rhythm, "NoteValue")));
        int levels = 0;
        while (base < TICKS_PER_QUARTER) {
            base *= 2;
            levels++;
        }
        return levels;
    }

    private Element barAt(Map<Integer, Element> bars, int[] barIds, int index) {
        if (index < 0 || index >= barIds.length) {
            return null;
        }
        return bars.get(barIds[index]);
    }

    private Element primaryVoice(Element bar, Map<Integer, Element> voices) {
        for (int voiceId : intList(textOf(firstChild(bar, "Voices")))) {
            if (voiceId >= 0) {
                Element voice = voices.get(voiceId);
                if (voice != null) {
                    return voice;
                }
            }
        }
        return null;
    }

    private Integer midiOf(Element note, StaffInfo staff) {
        if (note == null) {
            return null;
        }
        Element properties = firstChild(note, "Properties");
        if (properties == null) {
            return null;
        }
        // Explicit MIDI value (non-fretted instruments) takes precedence.
        Element midiProperty = propertyByName(properties, "Midi");
        if (midiProperty != null) {
            Integer number = parseIntOrNull(textOf(firstChild(midiProperty, "Number")));
            if (number != null) {
                return number;
            }
        }
        // Tablature: open-string tuning + fret + capo.
        Integer string = propertyInt(properties, "String");
        Integer fret = propertyInt(properties, "Fret");
        if (string != null && fret != null && staff.tuning != null
                && string >= 0 && string < staff.tuning.size()) {
            return staff.tuning.get(string) + fret + staff.capo;
        }
        // Concert pitch fallback (non-fretted).
        Element concertPitch = propertyByName(properties, "ConcertPitch");
        if (concertPitch != null) {
            return midiFromPitch(firstChild(concertPitch, "Pitch"));
        }
        return null;
    }

    private Integer midiFromPitch(Element pitch) {
        if (pitch == null) {
            return null;
        }
        String step = textOf(firstChild(pitch, "Step"));
        Integer octave = parseIntOrNull(textOf(firstChild(pitch, "Octave")));
        if (step == null || step.isEmpty() || octave == null) {
            return null;
        }
        int semitone = switch (step.charAt(0)) {
            case 'C' -> 0;
            case 'D' -> 2;
            case 'E' -> 4;
            case 'F' -> 5;
            case 'G' -> 7;
            case 'A' -> 9;
            case 'B' -> 11;
            default -> -1;
        };
        if (semitone < 0) {
            return null;
        }
        String accidental = textOf(firstChild(pitch, "Accidental"));
        int alter = 0;
        if (accidental != null) {
            for (int i = 0; i < accidental.length(); i++) {
                char c = accidental.charAt(i);
                if (c == '#') {
                    alter++;
                } else if (c == 'b') {
                    alter--;
                }
            }
        }
        return (octave + 1) * 12 + semitone + alter;
    }

    // ------------------------------------------------------------------
    // Attribute mapping
    // ------------------------------------------------------------------

    /** The master bar's explicit time signature, or {@code null} when it carries none. */
    private TimeSignature explicitTime(Element masterBar) {
        String time = textOf(firstChild(masterBar, "Time"));
        if (time != null && time.contains("/")) {
            String[] parts = time.split("/");
            Integer beats = parseIntOrNull(parts[0]);
            Integer beatType = parseIntOrNull(parts[1]);
            if (beats != null && beats > 0 && beatType != null && beatType > 0) {
                return new TimeSignature(beats, beatType);
            }
        }
        return null;
    }

    private KeySignature keyFrom(Element masterBar) {
        Element key = firstChild(masterBar, "Key");
        if (key != null) {
            Integer accidentals = parseIntOrNull(textOf(firstChild(key, "AccidentalCount")));
            if (accidentals != null) {
                return new KeySignature(accidentals);
            }
        }
        return KeySignature.cMajor();
    }

    private Clef clefFrom(Element bar) {
        if (bar == null) {
            return Clef.treble();
        }
        String clef = textOf(firstChild(bar, "Clef"));
        if (clef == null) {
            return Clef.treble();
        }
        return switch (clef) {
            case "F4" -> Clef.bass();
            case "C3" -> new Clef(ClefSign.C, 3);
            case "C4" -> new Clef(ClefSign.C, 4);
            default -> Clef.treble();
        };
    }

    private Duration durationFrom(Element rhythm) {
        if (rhythm == null) {
            return new Duration(TICKS_PER_QUARTER, TICKS_PER_QUARTER);
        }
        double ticks = baseTicks(textOf(firstChild(rhythm, "NoteValue")));

        Element dot = firstChild(rhythm, "AugmentationDot");
        int dots = 0;
        if (dot != null) {
            Integer count = attrInt(dot, "count");
            dots = count == null ? 1 : count;
        }
        ticks *= 2 - Math.pow(0.5, dots);

        Element tuplet = firstChild(rhythm, "PrimaryTuplet");
        if (tuplet != null) {
            Integer num = attrInt(tuplet, "num");
            Integer den = attrInt(tuplet, "den");
            if (num != null && num > 0 && den != null && den > 0) {
                ticks = ticks * den / num;
            }
        }
        return new Duration(Math.max(1, (int) Math.round(ticks)), TICKS_PER_QUARTER);
    }

    private double baseTicks(String noteValue) {
        String value = noteValue == null ? "Quarter" : noteValue;
        return switch (value) {
            case "Long" -> 16.0 * TICKS_PER_QUARTER;
            case "DoubleWhole" -> 8.0 * TICKS_PER_QUARTER;
            case "Whole" -> 4.0 * TICKS_PER_QUARTER;
            case "Half" -> 2.0 * TICKS_PER_QUARTER;
            case "Quarter" -> TICKS_PER_QUARTER;
            case "Eighth" -> TICKS_PER_QUARTER / 2.0;
            case "16th" -> TICKS_PER_QUARTER / 4.0;
            case "32nd" -> TICKS_PER_QUARTER / 8.0;
            case "64th" -> TICKS_PER_QUARTER / 16.0;
            case "128th" -> TICKS_PER_QUARTER / 32.0;
            case "256th" -> TICKS_PER_QUARTER / 64.0;
            default -> TICKS_PER_QUARTER;
        };
    }

    private List<StaffInfo> staffInfos(Element track) {
        List<StaffInfo> result = new ArrayList<>();
        Element staves = firstChild(track, "Staves");
        if (staves != null) {
            for (Element staff : childElements(staves, "Staff")) {
                result.add(staffInfo(staff));
            }
        }
        if (result.isEmpty()) {
            // Older GPIF (or single-staff tracks) keep properties on the track.
            result.add(staffInfo(track));
        }
        return result;
    }

    private StaffInfo staffInfo(Element element) {
        Element properties = firstChild(element, "Properties");
        List<Integer> tuning = null;
        int capo = 0;
        if (properties != null) {
            Element tuningProperty = propertyByName(properties, "Tuning");
            if (tuningProperty != null) {
                tuning = intListToList(textOf(firstChild(tuningProperty, "Pitches")));
            }
            Element capoProperty = propertyByName(properties, "CapoFret");
            if (capoProperty != null) {
                Integer fret = parseIntOrNull(textOf(firstChild(capoProperty, "Fret")));
                if (fret != null) {
                    capo = fret;
                }
            }
        }
        return new StaffInfo(tuning, capo);
    }

    // ------------------------------------------------------------------
    // DOM helpers
    // ------------------------------------------------------------------

    private Map<Integer, Element> indexById(List<Element> elements) {
        Map<Integer, Element> map = new HashMap<>();
        for (Element element : elements) {
            Integer id = attrInt(element, "id");
            if (id != null) {
                map.put(id, element);
            }
        }
        return map;
    }

    private Element firstChild(Element parent, String name) {
        if (parent == null) {
            return null;
        }
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE && node.getNodeName().equals(name)) {
                return (Element) node;
            }
        }
        return null;
    }

    private List<Element> childElements(Element parent, String name) {
        List<Element> result = new ArrayList<>();
        if (parent == null) {
            return result;
        }
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE && node.getNodeName().equals(name)) {
                result.add((Element) node);
            }
        }
        return result;
    }

    private Element propertyByName(Element properties, String name) {
        for (Element property : childElements(properties, "Property")) {
            if (name.equals(property.getAttribute("name"))) {
                return property;
            }
        }
        return null;
    }

    private Integer propertyInt(Element properties, String name) {
        Element property = propertyByName(properties, name);
        if (property == null) {
            return null;
        }
        Element value = firstChild(property, name);
        return value != null ? parseIntOrNull(textOf(value)) : null;
    }

    private String textOf(Element element) {
        if (element == null) {
            return null;
        }
        String text = element.getTextContent();
        return text == null ? null : text.trim();
    }

    private Integer attrInt(Element element, String attribute) {
        return parseIntOrNull(element.getAttribute(attribute));
    }

    private int[] intList(String text) {
        if (text == null || text.isBlank()) {
            return new int[0];
        }
        String[] tokens = text.trim().split("\\s+");
        int[] values = new int[tokens.length];
        int count = 0;
        for (String token : tokens) {
            Integer value = parseIntOrNull(token);
            if (value != null) {
                values[count++] = value;
            }
        }
        if (count == values.length) {
            return values;
        }
        int[] trimmed = new int[count];
        System.arraycopy(values, 0, trimmed, 0, count);
        return trimmed;
    }

    private List<Integer> intListToList(String text) {
        int[] values = intList(text);
        List<Integer> list = new ArrayList<>(values.length);
        for (int value : values) {
            list.add(value);
        }
        return list;
    }

    private Integer parseIntOrNull(String text) {
        if (text == null) {
            return null;
        }
        try {
            return Integer.valueOf(text.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private int clampMidi(int midi) {
        return Math.max(0, Math.min(127, midi));
    }

    /** Tuning (open-string MIDI values, per string index) and capo for one staff. */
    private record StaffInfo(List<Integer> tuning, int capo) {
    }

    /**
     * A GPIF beat resolved to model data, awaiting beam assignment. An empty
     * {@link #pitches} list denotes a rest. {@link #beamLevels} is the number of
     * beams the note value carries (0 = quarter or longer); {@link #beams} is
     * filled in by {@link #assignBeams}.
     */
    private static final class BeatItem {
        private final List<Pitch> pitches;
        private final Duration duration;
        private final int staffNumber;
        private final int beamLevels;
        private final List<Beam> beams = new ArrayList<>();

        BeatItem(List<Pitch> pitches, Duration duration, int staffNumber, int beamLevels) {
            this.pitches = pitches;
            this.duration = duration;
            this.staffNumber = staffNumber;
            this.beamLevels = beamLevels;
        }

        boolean isRest() {
            return pitches.isEmpty();
        }

        /** Whether this beat can participate in a beam (a note, eighth or shorter). */
        boolean beamable() {
            return beamLevels > 0 && !isRest();
        }
    }
}
