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
import java.util.Optional;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import com.sheetmusic4j.core.model.Accidental;
import com.sheetmusic4j.core.model.Articulation;
import com.sheetmusic4j.core.model.Attributes;
import com.sheetmusic4j.core.model.Beam;
import com.sheetmusic4j.core.model.Chord;
import com.sheetmusic4j.core.model.Clef;
import com.sheetmusic4j.core.model.ClefSign;
import com.sheetmusic4j.core.model.Creator;
import com.sheetmusic4j.core.model.Direction;
import com.sheetmusic4j.core.model.DirectionType;
import com.sheetmusic4j.core.model.Duration;
import com.sheetmusic4j.core.model.DynamicMark;
import com.sheetmusic4j.core.model.GroupSymbol;
import com.sheetmusic4j.core.model.Harmony;
import com.sheetmusic4j.core.model.HarmonyKind;
import com.sheetmusic4j.core.model.KeySignature;
import com.sheetmusic4j.core.model.Lyric;
import com.sheetmusic4j.core.model.Measure;
import com.sheetmusic4j.core.model.Note;
import com.sheetmusic4j.core.model.NoteType;
import com.sheetmusic4j.core.model.Part;
import com.sheetmusic4j.core.model.PartGroup;
import com.sheetmusic4j.core.model.Pitch;
import com.sheetmusic4j.core.model.Placement;
import com.sheetmusic4j.core.model.Rest;
import com.sheetmusic4j.core.model.Score;
import com.sheetmusic4j.core.model.Slur;
import com.sheetmusic4j.core.model.Step;
import com.sheetmusic4j.core.model.Syllabic;
import com.sheetmusic4j.core.model.TimeModification;
import com.sheetmusic4j.core.model.TimeSignature;
import com.sheetmusic4j.core.model.Tuplet;

/**
 * Reads a MusicXML {@code score-partwise} document into a {@link Score}.
 *
 * <p>Only a renderable subset is handled: part list, measures, attributes
 * (divisions, key, time, clef), and notes (pitch, rest, chord, duration, type,
 * dots, ties). Unknown elements are ignored leniently.
 */
public final class MusicXmlReader {

    /**
     * Set of {@code <credit-type>} values recognised as creator roles when we
     * fall back to {@code <credit>} elements (only used if the corresponding
     * role is not already in {@code <identification>}).
     */
    private static final Set<String> KNOWN_CREATOR_ROLES = Set.of(
            "composer", "lyricist", "arranger", "poet", "translator", "transcriber");

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
        Map<String, ScorePartInfo> scoreParts = new LinkedHashMap<>();
        Map<String, Part.Builder> partBuilders = new LinkedHashMap<>();
        List<String> partOrder = new ArrayList<>();
        // Groups pending a matching type="stop" sentinel. In canonical
        // formatting <part-group start> appears immediately before the
        // <score-part>s it wraps and <part-group stop> immediately after,
        // so "startIndex = current partOrder.size()" and "endIndex =
        // partOrder.size() - 1 at stop" together yield inclusive part
        // ranges. Malformed groups (stop without start, unclosed at EOF)
        // are silently dropped.
        Map<Integer, PendingGroup> openGroups = new LinkedHashMap<>();
        // Number of <score-part> entries seen so far inside <part-list>.
        // Drives inclusive part-index bookkeeping for <part-group> sentinels
        // — <part> elements themselves appear only after <part-list>, so we
        // cannot use partOrder.size() here.
        int[] scorePartCount = {0};

        while (reader.hasNext()) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                String name = reader.getLocalName();
                switch (name) {
                    case "work-title" -> score.workTitle(readText(reader));
                    case "movement-title" -> score.movementTitle(readText(reader));
                    case "identification" -> readIdentification(reader, score);
                    case "credit" -> readCredit(reader, score);
                    case "score-part" -> {
                        readScorePart(reader, scoreParts);
                        scorePartCount[0]++;
                    }
                    case "part-group" -> handlePartGroup(reader, score, openGroups, scorePartCount[0]);
                    case "part" -> {
                        String id = reader.getAttributeValue(null, "id");
                        ScorePartInfo info = scoreParts.get(id);
                        Part.Builder part = Part.builder(id)
                                .name(info != null ? info.name() : null)
                                .abbreviation(info != null ? info.abbreviation() : null);
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

    /**
     * Handle a single {@code <part-group>} element. On {@code type="start"}
     * records a {@link PendingGroup} keyed by the group number; on
     * {@code type="stop"} closes the matching pending group and appends the
     * resulting {@link PartGroup} to the score. Malformed cases (stop
     * without start, missing type attribute, unknown type) are dropped
     * without throwing.
     *
     * @param reader       the XML reader positioned on the start element
     * @param score        the target score builder
     * @param openGroups   currently pending group starts, keyed by number
     * @param currentIndex the index of the next {@code <score-part>} that
     *                     will follow (i.e. {@code partOrder.size()})
     */
    private void handlePartGroup(XMLStreamReader reader, Score.Builder score,
                                 Map<Integer, PendingGroup> openGroups, int currentIndex)
            throws XMLStreamException {
        int number = parseIntOr(reader.getAttributeValue(null, "number"), 1);
        String type = reader.getAttributeValue(null, "type");
        ParsedPartGroup parsed = readPartGroup(reader);
        if ("start".equalsIgnoreCase(type)) {
            openGroups.put(number, new PendingGroup(
                    number, currentIndex, parsed.symbol, parsed.barline,
                    parsed.name, parsed.abbreviation));
        } else if ("stop".equalsIgnoreCase(type)) {
            PendingGroup pending = openGroups.remove(number);
            if (pending == null) {
                // Malformed input — drop silently.
                return;
            }
            int endIndex = currentIndex - 1;
            if (endIndex < pending.startIndex) {
                // Empty group (no score-parts in between) — drop.
                return;
            }
            score.addPartGroup(new PartGroup(
                    pending.number, pending.startIndex, endIndex,
                    pending.symbol, pending.barline, pending.name, pending.abbreviation));
        }
    }

    /**
     * Read the body of a {@code <part-group>} element up to its matching
     * end tag, collecting the optional child elements the model cares
     * about.
     */
    private ParsedPartGroup readPartGroup(XMLStreamReader reader) throws XMLStreamException {
        String name = null;
        String abbreviation = null;
        GroupSymbol symbol = GroupSymbol.NONE;
        boolean barline = false;
        while (reader.hasNext()) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                switch (reader.getLocalName()) {
                    case "group-name" -> name = readText(reader);
                    case "group-abbreviation" -> abbreviation = readText(reader);
                    case "group-symbol" -> symbol = GroupSymbol.fromXml(readText(reader));
                    case "group-barline" -> {
                        String text = readText(reader);
                        barline = text != null && text.trim().equalsIgnoreCase("yes");
                    }
                    default -> skipElement(reader);
                }
            } else if (event == XMLStreamConstants.END_ELEMENT
                    && "part-group".equals(reader.getLocalName())) {
                break;
            }
        }
        return new ParsedPartGroup(symbol, barline, name, abbreviation);
    }

    /**
     * Transient record capturing the payload of a single {@code <part-group>}
     * element (without its number/type attributes, which drive the
     * pairing logic).
     */
    private record ParsedPartGroup(GroupSymbol symbol, boolean barline,
                                   String name, String abbreviation) {
    }

    /**
     * Bookkeeping for a {@code <part-group type="start"/>} awaiting its
     * matching stop sentinel.
     */
    private record PendingGroup(int number, int startIndex, GroupSymbol symbol,
                                boolean barline, String name, String abbreviation) {
    }

    /**
     * Immutable pair of a part's full name and abbreviation, gathered from
     * a single {@code <score-part>} entry in the {@code <part-list>}.
     */
    private record ScorePartInfo(String name, String abbreviation) {
    }

    private void readIdentification(XMLStreamReader reader, Score.Builder score) throws XMLStreamException {
        while (reader.hasNext()) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                if ("creator".equals(reader.getLocalName())) {
                    String type = reader.getAttributeValue(null, "type");
                    String text = readText(reader);
                    Creator creator = Creator.of(type, text);
                    if (creator != null && !score.hasCreatorRole(creator.role())) {
                        score.addCreator(creator);
                    }
                } else {
                    skipElement(reader);
                }
            } else if (event == XMLStreamConstants.END_ELEMENT && "identification".equals(reader.getLocalName())) {
                break;
            }
        }
    }

    private void readCredit(XMLStreamReader reader, Score.Builder score) throws XMLStreamException {
        String creditType = null;
        String creditWords = null;
        while (reader.hasNext()) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                switch (reader.getLocalName()) {
                    case "credit-type" -> {
                        String text = readText(reader);
                        if (creditType == null && text != null && !text.isBlank()) {
                            creditType = text.trim().toLowerCase(Locale.ROOT);
                        }
                    }
                    case "credit-words" -> {
                        String text = readText(reader);
                        if (creditWords == null && text != null && !text.isBlank()) {
                            creditWords = text;
                        }
                    }
                    default -> skipElement(reader);
                }
            } else if (event == XMLStreamConstants.END_ELEMENT && "credit".equals(reader.getLocalName())) {
                break;
            }
        }
        if (creditType != null && creditWords != null
                && KNOWN_CREATOR_ROLES.contains(creditType)
                && !score.hasCreatorRole(creditType)) {
            Creator creator = Creator.of(creditType, creditWords);
            if (creator != null) {
                score.addCreator(creator);
            }
        }
    }

    /**
     * Consume the current element including all nested content. Used to
     * safely skip unknown children when scanning {@code <identification>} /
     * {@code <credit>}.
     */
    private void skipElement(XMLStreamReader reader) throws XMLStreamException {
        int depth = 1;
        while (reader.hasNext() && depth > 0) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                depth++;
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                depth--;
            }
        }
    }

    private void readScorePart(XMLStreamReader reader, Map<String, ScorePartInfo> scoreParts) throws XMLStreamException {
        String id = reader.getAttributeValue(null, "id");
        String name = null;
        String abbreviation = null;
        while (reader.hasNext()) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                switch (reader.getLocalName()) {
                    case "part-name" -> name = readText(reader);
                    case "part-abbreviation" -> abbreviation = readText(reader);
                    default -> { /* ignore other score-part children */ }
                }
            } else if (event == XMLStreamConstants.END_ELEMENT && "score-part".equals(reader.getLocalName())) {
                break;
            }
        }
        if (id != null) {
            scoreParts.put(id, new ScorePartInfo(name, abbreviation));
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
                    case "direction" -> {
                        Direction direction = readDirection(reader);
                        if (direction != null) {
                            flushChord(measure, pendingChord);
                            measure.addElement(direction);
                        }
                    }
                    case "harmony" -> {
                        Harmony harmony = readHarmony(reader);
                        if (harmony != null) {
                            flushChord(measure, pendingChord);
                            measure.addElement(harmony);
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

    /**
     * Parse a MusicXML {@code <direction>} block. Returns {@code null} when
     * the block contains no recognised {@code <direction-type>} child
     * (segno/coda, octave shifts — still deferred). The
     * {@code placement} attribute drives {@link Placement}; when a
     * {@code <direction>} carries multiple {@code <direction-type>} children
     * only the first recognised one wins.
     */
    private Direction readDirection(XMLStreamReader reader) throws XMLStreamException {
        Placement placement = Placement.fromXml(reader.getAttributeValue(null, "placement"));
        DirectionType type = null;
        while (reader.hasNext()) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                if ("direction-type".equals(reader.getLocalName())) {
                    DirectionType parsed = readDirectionType(reader);
                    if (type == null && parsed != null) {
                        type = parsed;
                    }
                } else {
                    // <sound>, <offset>, <staff>, <voice>, ... — skip for MVP.
                    skipElement(reader);
                }
            } else if (event == XMLStreamConstants.END_ELEMENT && "direction".equals(reader.getLocalName())) {
                break;
            }
        }
        if (type == null) {
            return null;
        }
        return new Direction(type, placement);
    }

    private DirectionType readDirectionType(XMLStreamReader reader) throws XMLStreamException {
        DirectionType result = null;
        StringBuilder wordsText = null;
        boolean italic = false;
        boolean bold = false;
        while (reader.hasNext()) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                switch (reader.getLocalName()) {
                    case "words" -> {
                        String fontStyle = reader.getAttributeValue(null, "font-style");
                        String fontWeight = reader.getAttributeValue(null, "font-weight");
                        if ("italic".equalsIgnoreCase(fontStyle)) {
                            italic = true;
                        }
                        if ("bold".equalsIgnoreCase(fontWeight)) {
                            bold = true;
                        }
                        String chunk = readText(reader);
                        if (chunk != null) {
                            if (wordsText == null) {
                                wordsText = new StringBuilder();
                            } else if (wordsText.length() > 0 && !chunk.isEmpty()) {
                                wordsText.append(' ');
                            }
                            wordsText.append(chunk);
                        }
                    }
                    case "metronome" -> {
                        DirectionType metronome = readMetronome(reader);
                        if (metronome != null && result == null) {
                            result = metronome;
                        }
                    }
                    case "dynamics" -> {
                        DirectionType dynamic = readDynamics(reader);
                        if (dynamic != null && result == null) {
                            result = dynamic;
                        }
                    }
                    case "rehearsal" -> {
                        String label = readText(reader);
                        if (label != null && !label.isEmpty() && result == null) {
                            result = new DirectionType.Rehearsal(label);
                        }
                    }
                    case "wedge" -> {
                        String wedgeTypeAttr = reader.getAttributeValue(null, "type");
                        int number = parseIntOr(reader.getAttributeValue(null, "number"), 1);
                        DirectionType.WedgeType wedgeType = switch (wedgeTypeAttr == null ? "" : wedgeTypeAttr) {
                            case "crescendo" -> DirectionType.WedgeType.CRESCENDO;
                            case "diminuendo" -> DirectionType.WedgeType.DIMINUENDO;
                            case "stop" -> DirectionType.WedgeType.STOP;
                            default -> null;
                        };
                        if (wedgeType != null && result == null) {
                            result = new DirectionType.Wedge(wedgeType, number);
                        }
                    }
                    default -> skipElement(reader);
                }
            } else if (event == XMLStreamConstants.END_ELEMENT
                    && "direction-type".equals(reader.getLocalName())) {
                break;
            }
        }
        if (result != null) {
            return result;
        }
        if (wordsText != null && wordsText.length() > 0) {
            return new DirectionType.Words(wordsText.toString(), italic, bold);
        }
        return null;
    }

    private DirectionType readMetronome(XMLStreamReader reader) throws XMLStreamException {
        NoteType beatUnit = null;
        boolean dotted = false;
        Integer perMinute = null;
        while (reader.hasNext()) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                switch (reader.getLocalName()) {
                    case "beat-unit" -> {
                        try {
                            beatUnit = NoteType.fromXml(readText(reader));
                        } catch (IllegalArgumentException e) {
                            beatUnit = null;
                        }
                    }
                    case "beat-unit-dot" -> {
                        dotted = true;
                        skipElement(reader);
                    }
                    case "per-minute" -> perMinute = parseIntOr(readText(reader), 0);
                    default -> skipElement(reader);
                }
            } else if (event == XMLStreamConstants.END_ELEMENT
                    && "metronome".equals(reader.getLocalName())) {
                break;
            }
        }
        if (beatUnit == null || perMinute == null || perMinute <= 0) {
            return null;
        }
        return new DirectionType.Metronome(beatUnit, dotted, perMinute);
    }

    private DirectionType readDynamics(XMLStreamReader reader) throws XMLStreamException {
        DynamicMark mark = null;
        while (reader.hasNext()) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                if (mark == null) {
                    DynamicMark candidate = DynamicMark.fromXml(reader.getLocalName());
                    if (candidate != null) {
                        mark = candidate;
                    }
                }
                skipElement(reader);
            } else if (event == XMLStreamConstants.END_ELEMENT
                    && "dynamics".equals(reader.getLocalName())) {
                break;
            }
        }
        if (mark == null) {
            return null;
        }
        return new DirectionType.Dynamic(mark);
    }

    /**
     * Parse a MusicXML {@code <harmony>} block into a {@link Harmony}
     * element. Returns {@code null} when the block contains no usable data
     * (missing root and kind). Unsupported children ({@code <degree>},
     * {@code <function>}, {@code <frame>}) are skipped for MVP.
     */
    private Harmony readHarmony(XMLStreamReader reader) throws XMLStreamException {
        Harmony.Root root = null;
        HarmonyKind kind = null;
        String textOverride = null;
        Harmony.Bass bass = null;
        while (reader.hasNext()) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                switch (reader.getLocalName()) {
                    case "root" -> root = readRoot(reader);
                    case "kind" -> {
                        String attr = reader.getAttributeValue(null, "text");
                        String body = readText(reader);
                        kind = HarmonyKind.fromXml(body);
                        if (attr != null && !attr.isBlank()) {
                            textOverride = attr;
                        }
                    }
                    case "bass" -> bass = readBass(reader);
                    default -> skipElement(reader);
                }
            } else if (event == XMLStreamConstants.END_ELEMENT && "harmony".equals(reader.getLocalName())) {
                break;
            }
        }
        if (root == null && kind == null) {
            return null;
        }
        if (root == null) {
            // Defensive: MusicXML requires a root, but if the source omitted
            // it we still keep the kind by anchoring to a synthetic C root.
            root = new Harmony.Root(Step.C, 0);
        }
        return new Harmony(root,
                kind != null ? kind : HarmonyKind.OTHER,
                Optional.ofNullable(bass),
                Optional.ofNullable(textOverride));
    }

    /**
     * Parse the {@code <root>} child of a {@code <harmony>} element.
     * {@code <root-step>} is mandatory; {@code <root-alter>} defaults to 0.
     */
    private Harmony.Root readRoot(XMLStreamReader reader) throws XMLStreamException {
        Step step = null;
        int alter = 0;
        while (reader.hasNext()) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                switch (reader.getLocalName()) {
                    case "root-step" -> {
                        String text = readText(reader);
                        if (text != null && !text.isEmpty()) {
                            try {
                                step = Step.valueOf(text.trim().toUpperCase(Locale.ROOT));
                            } catch (IllegalArgumentException ignored) {
                                step = null;
                            }
                        }
                    }
                    case "root-alter" -> alter = parseIntOr(readText(reader), 0);
                    default -> skipElement(reader);
                }
            } else if (event == XMLStreamConstants.END_ELEMENT && "root".equals(reader.getLocalName())) {
                break;
            }
        }
        if (step == null) {
            return null;
        }
        return new Harmony.Root(step, alter);
    }

    /**
     * Parse the {@code <bass>} child of a {@code <harmony>} element.
     * {@code <bass-step>} is mandatory; {@code <bass-alter>} defaults to 0.
     */
    private Harmony.Bass readBass(XMLStreamReader reader) throws XMLStreamException {
        Step step = null;
        int alter = 0;
        while (reader.hasNext()) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                switch (reader.getLocalName()) {
                    case "bass-step" -> {
                        String text = readText(reader);
                        if (text != null && !text.isEmpty()) {
                            try {
                                step = Step.valueOf(text.trim().toUpperCase(Locale.ROOT));
                            } catch (IllegalArgumentException ignored) {
                                step = null;
                            }
                        }
                    }
                    case "bass-alter" -> alter = parseIntOr(readText(reader), 0);
                    default -> skipElement(reader);
                }
            } else if (event == XMLStreamConstants.END_ELEMENT && "bass".equals(reader.getLocalName())) {
                break;
            }
        }
        if (step == null) {
            return null;
        }
        return new Harmony.Bass(step, alter);
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
        java.util.List<Lyric> lyrics = new ArrayList<>();
        java.util.List<Articulation> articulations = new ArrayList<>();
        java.util.List<Slur> slurs = new ArrayList<>();
        java.util.List<Tuplet> tuplets = new ArrayList<>();
        int actualNotes = 0;
        int normalNotes = 0;

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
                    case "lyric" -> {
                        Lyric lyric = readLyric(reader);
                        if (lyric != null) {
                            lyrics.add(lyric);
                        }
                    }
                    case "tie" -> {
                        String tieType = reader.getAttributeValue(null, "type");
                        if ("start".equals(tieType)) {
                            tieStart = true;
                        } else if ("stop".equals(tieType)) {
                            tieStop = true;
                        }
                    }
                    case "staccato" -> articulations.add(Articulation.STACCATO);
                    case "accent" -> articulations.add(Articulation.ACCENT);
                    case "slur" -> {
                        int number = parseIntOr(reader.getAttributeValue(null, "number"), 1);
                        String slurType = reader.getAttributeValue(null, "type");
                        Placement slurPlacement = Placement.fromXml(reader.getAttributeValue(null, "placement"));
                        if ("start".equals(slurType)) {
                            slurs.add(new Slur(number, Slur.Type.START, slurPlacement));
                        } else if ("stop".equals(slurType)) {
                            slurs.add(new Slur(number, Slur.Type.STOP, slurPlacement));
                        }
                    }
                    case "tuplet" -> {
                        int number = parseIntOr(reader.getAttributeValue(null, "number"), 1);
                        String tupletType = reader.getAttributeValue(null, "type");
                        boolean bracket = !"no".equals(reader.getAttributeValue(null, "bracket"));
                        if ("start".equals(tupletType)) {
                            tuplets.add(new Tuplet(number, Tuplet.Type.START, bracket));
                        } else if ("stop".equals(tupletType)) {
                            tuplets.add(new Tuplet(number, Tuplet.Type.STOP, bracket));
                        }
                    }
                    case "actual-notes" -> actualNotes = parseIntOr(readText(reader), 0);
                    case "normal-notes" -> normalNotes = parseIntOr(readText(reader), 0);
                    default -> { /* ignore */ }
                }
            } else if (event == XMLStreamConstants.END_ELEMENT && "note".equals(reader.getLocalName())) {
                break;
            }
        }

        TimeModification timeModification = (actualNotes > 0 && normalNotes > 0)
                ? new TimeModification(actualNotes, normalNotes)
                : null;

        Duration dur = new Duration(Math.max(duration, 0), divisions);
        if (rest) {
            Rest.Builder rb = Rest.builder().duration(dur).dots(dots).tuplets(tuplets).staff(staff);
            if (type != null) {
                rb.type(type);
            }
            if (timeModification != null) {
                rb.timeModification(timeModification);
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
                .beams(beams)
                .lyrics(lyrics)
                .articulations(articulations)
                .slurs(slurs)
                .tuplets(tuplets);
        if (accidental != null) {
            nb.displayedAccidental(accidental);
        }
        if (type != null) {
            nb.type(type);
        }
        if (timeModification != null) {
            nb.timeModification(timeModification);
        }
        return ParsedNote.note(nb.build(), chord);
    }

    /**
     * Parse a {@code <lyric>} element. Returns {@code null} when the
     * accumulated text is blank so the caller can drop the placeholder
     * entry.
     */
    private Lyric readLyric(XMLStreamReader reader) throws XMLStreamException {
        int verse = parseIntOr(reader.getAttributeValue(null, "number"), 1);
        if (verse < 1) {
            verse = 1;
        }
        Syllabic syllabic = Syllabic.SINGLE;
        StringBuilder text = new StringBuilder();
        while (reader.hasNext()) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                switch (reader.getLocalName()) {
                    case "syllabic" -> syllabic = Syllabic.fromXml(readText(reader));
                    case "text" -> {
                        String chunk = readText(reader);
                        if (chunk != null && !chunk.isEmpty()) {
                            if (text.length() > 0) {
                                text.append(' ');
                            }
                            text.append(chunk);
                        }
                    }
                    default -> skipElement(reader);
                }
            } else if (event == XMLStreamConstants.END_ELEMENT && "lyric".equals(reader.getLocalName())) {
                break;
            }
        }
        String result = text.toString().trim();
        if (result.isEmpty()) {
            return null;
        }
        return new Lyric(result, syllabic, verse);
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
