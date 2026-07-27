id: 209e6b27-92e3-4fd4-a62a-1a95b92ef04a
sessionId: a49335b3-f1b9-4e70-9e03-ee611d6409b5
date: '2026-07-27T16:35:55.153Z'
label: Add GuitarPro file support via alphaTab
---
# Add GuitarPro file support via alphaTab

## Goal
Enable sheetmusic4j to load GuitarPro files (`.gp`, `.gp3`, `.gp4`, `.gp5`, `.gpx`) into the existing `Score` model by adding [alphaTab](https://central.sonatype.com/artifact/net.alphatab/alphaTab) (MPL-2.0, Kotlin/JVM) as a parsing dependency. Import standard-notation pitch content with **full multi-staff (grand-staff) support**. This mirrors the existing MIDI import path.

## Resolved Requirements (from user)
1. **Scope:** Full multi-voice / multi-staff import (not just monophonic).
2. **Tablature:** Standard-notation pitch only — fret/string TAB data is **not** preserved (no model changes for TAB).
3. **Test fixture:** User will supply a sample GuitarPro file.
4. **Version:** Use the latest stable `net.alphatab:alphaTab`, ensuring the newest GuitarPro formats (GP7/GP8 `.gp`) parse.

## Background / Current Architecture
The `core` module already has two importers that produce a `Score` via builders:
- `core/src/main/java/com/sheetmusic4j/core/midi/MidiImporter.java` — builds `Score` from a MIDI `Sequence`; `MidiException` is its dedicated runtime exception.
- `core/src/main/java/com/sheetmusic4j/core/musicxml/MusicXmlReader.java` — MusicXML path.

All importers feed a single facade:
- `core/src/main/java/com/sheetmusic4j/core/io/ScoreFile.java` — `load(Path)` / `save(Path)` dispatch on file extension via a private `Format` enum.

The target model (all in `core/src/main/java/com/sheetmusic4j/core/model/`) uses fluent builders:
- `Score` (workTitle, movementTitle, `Creator` list, `Part` list)
- `Part` (id, name, abbreviation, `Measure` list)
- `Measure` (1-based number, optional `Attributes`, ordered `MusicElement` list)
- `Attributes` (divisions, `KeySignature`, `TimeSignature`, `Clef`(s) via `addClef`, `staves`)
- `Note` / `Chord` / `Rest` (all `MusicElement`), `Pitch` (`Pitch.fromMidiNumber(int, KeySignature)`), `Duration(value, divisions)`
- `KeySignature(fifths)`, `TimeSignature(beats, beatType)`, `Clef.treble()/bass()` / `new Clef(ClefSign.C, line)`, `NoteType.fromQuarterValue(double)`

### How the model represents multiple staves/voices (important constraint)
- **Multi-staff (grand staff) IS supported today.** `Note` and `Rest` carry a 1-based `staff()` field; `Attributes` carries `staves(int)` plus one `Clef` per staff (`addClef`). The engraver splits a measure's flat element list by staff — see `engraving/.../Engraver.java:1400` (`elementsForStaff` / `elementStaff`), which filters elements where `elementStaff(element) == staffNumber` (Note→`note.staff()`, Chord→its first note's staff, Rest→`rest.staff()`). So tagging notes/rests with a staff index and emitting `staves(n)` + per-staff clefs is enough for full grand-staff rendering.
- **There is NO voice concept in the model or engraver.** A `Measure` holds a single flat, sequential `MusicElement` list; the MusicXML reader itself ignores `<backup>`/`<voice>` (see `MusicXmlReader.java:476,586`). Two simultaneous rhythmic voices within one staff cannot be represented without adding a `voice` field to `Note`/`Rest` **and** teaching the engraver to lay voices out concurrently. See "Multi-voice handling" below for the chosen approach.

The `GuitarProImporter` translates alphaTab's model into these builders directly — no MusicXML string intermediary.

## Key Design Decisions

1. **Parse-only, headless.** Use alphaTab purely for GP → model parsing. Ignore alphaTab's rendering/layout entirely (sheetmusic4j's `engraving`/`fxviewer` do that). Keeps the dependency confined to the `core` module.

2. **Use `net.alphatab:alphaTab` (plain Kotlin/JVM), NOT `alphaTab-android`.** The `-android` artifact drags in Android drawing/coroutine deps. **⚠️ Verify (Verification Step 0)** the plain artifact parses GP headlessly with no Android/browser/GraalVM classes required. If unwanted transitive rendering deps appear, add `<exclusions>` (parsing needs only the importer + model packages). Use the latest stable version so GP7/GP8 `.gp` is supported.

3. **Duration via playback ticks.** alphaTab beats expose a playback duration in MIDI ticks (quarter note = 960 ticks in alphaTab's `MidiUtils`). Map onto `Duration(valueTicks, divisions=960)` so existing `NoteType.fromQuarterValue` / rest logic works, exactly like `MidiImporter`. Set `Attributes.divisions(960)` on each part's first measure.

4. **Pitch via MIDI value (standard notation only).** alphaTab `Note.realValue` is the sounding MIDI pitch (fret + tuning + capo already applied). Convert with `Pitch.fromMidiNumber(realValue, keySignature)`, reusing the enharmonic-spelling logic MIDI import uses. Fret/string TAB data is intentionally discarded.

5. **Track → Part, Bar → Measure.** One alphaTab `Track` → one `Part` (`P1`, `P2`, …), using `track.getName()` / `getShortName()` for name/abbreviation. Each alphaTab `Bar` (per master bar) → one `Measure`.

6. **Full multi-staff mapping.** Import **all** staves of a track into a single `Part`:
   - Tag every `Note`/`Rest` with `staff(staffIndex + 1)`.
   - On the part's first measure, emit `Attributes.staves(numberOfStaves)` and one `addClef(...)` per staff (mapped from each `Bar.getClef()`), plus divisions / key / time.
   - Append each staff's elements into the same `Measure` element list (the engraver filters by `staff()`), matching how MusicXML grand staves are modelled.

7. **Multi-voice handling.** GuitarPro bars can contain multiple voices per staff. Because the model/engraver have no voice concept, choose ONE of:
   - **(A) Recommended, no model change:** Import the **primary sounding voice** per staff (first non-empty voice; alphaTab exposes `bar.getVoices()`, and empty voices via `voice.isEmpty()`). Document that secondary voices are dropped. This keeps each staff's timeline coherent and renders correctly today.
   - **(B) Full fidelity, larger scope:** Add an optional `voice` field to `Note`/`Rest` (and thread it through builders), import every non-empty voice tagged by voice number, and extend the `Engraver` to lay concurrent voices out on the same staff (stem-direction split, shared x-positions). This is a substantial engraving change and should be a separate, explicitly-scoped effort.
   - **Plan of record (confirmed with user):** implement (A) now for a correct, shippable importer; (B) is a separate follow-up milestone (see below). Do NOT include 7B in this change.

8. **Load-only.** No GP export. `ScoreFile.save` throws `UnsupportedOperationException` for the GuitarPro format.

## Implementation Steps

### Step 1: Add the alphaTab dependency
- `pom.xml` (parent):
  - Add `<alphatab.version>LATEST_STABLE</alphatab.version>` under `<!-- Dependencies -->` (look up the newest release on Maven Central).
  - Add a `net.alphatab:alphaTab` entry to `<dependencyManagement>` near the other third-party deps.
- `core/pom.xml` — add the `net.alphatab:alphaTab` `<dependency>` (no version; inherited). Add `<exclusions>` only if Step 0 shows unwanted transitive Android/rendering deps.
- alphaTab's transitive `org.jetbrains.kotlin:kotlin-stdlib` is pulled in automatically — fine for a JVM library.

### Step 2: Add the GuitarPro exception
- Create `core/src/main/java/com/sheetmusic4j/core/guitarpro/GuitarProException.java`
  - Copy the shape of `core/src/main/java/com/sheetmusic4j/core/midi/MidiException.java` (extends `RuntimeException`; message + message/cause constructors).

### Step 3: Implement the importer
- Create `core/src/main/java/com/sheetmusic4j/core/guitarpro/GuitarProImporter.java`
  - Public API modelled on `MidiImporter`:
    - `Score fromGuitarPro(Path path)` — read bytes, call the alphaTab loader, wrap `IOException`/parse errors in `GuitarProException`.
    - `Score fromGuitarPro(InputStream in)` — same via `in.readAllBytes()`.
    - `Score fromAlphaTabScore(alphaTab.model.Score atScore)` — the core translation (analogous to `MidiImporter.fromSequence`), unit-testable.
  - Loading: `alphaTab.importer.ScoreLoader.loadScoreFromBytes(byte[])` → `alphaTab.model.Score`. **⚠️ Confirm exact class/method names + package casing against the resolved artifact (Kotlin→Java interop typically exposes getters like `getTitle()`).**
  - Translation outline:
    1. `Score.Builder` ← `atScore.getTitle()` → `workTitle`; add `Creator.of("composer", atScore.getMusic())` and `Creator.of("lyricist", atScore.getWords())` (both null-safe; `Creator.of` drops blanks; `addCreator` ignores null).
    2. For each `Track` (index `i`): `Part.Builder part = Part.builder("P" + (i+1)).name(track.getName()).abbreviation(track.getShortName())`.
    3. Determine `numberOfStaves = track.getStaves().size()`; record the part-level `KeySignature` from the first master bar (for consistent enharmonic spelling).
    4. Iterate master bars by index `b` (`atScore.getMasterBars()`), building one `Measure.builder(b + 1)`:
       - On the **first** measure of the part, attach `Attributes.builder()` with: `divisions(960)`, `keySignature(keyFrom(masterBar))`, `timeSignature(new TimeSignature(masterBar.getTimeSignatureNumerator(), masterBar.getTimeSignatureDenominator()))`, `staves(numberOfStaves)`, and one `addClef(clefFrom(bar))` per staff (in staff order).
         - `keyFrom`: `masterBar.getKeySignature()` → fifths (-7..7) → `new KeySignature(fifths)`. **⚠️ Verify it returns a numeric fifths value (int or enum with `.getValue()`).**
         - `clefFrom`: map alphaTab `Bar.getClef()` (`G2`, `F4`, `C3`, `C4`, `Neutral`) → `Clef.treble()` / `Clef.bass()` / `new Clef(ClefSign.C, line)`; default `Clef.treble()`.
       - For each staff `s` in `track.getStaves()`, take `bar = staff.getBars().get(b)`:
         - Pick the primary voice per Decision 7A: first `voice` in `bar.getVoices()` where `!voice.isEmpty()` (fallback to index 0).
         - For each `beat` in voice order:
           - `dur = new Duration((int) beat.getPlaybackDuration(), 960)`. **⚠️ Confirm getter; fallback: derive from `beat.getDuration()` enum + `beat.getDots()`.**
           - If `beat.isRest()` → `Rest.builder().duration(dur).staff(s + 1).build()`.
           - Else map `beat.getNotes()` → `Note.builder().pitch(Pitch.fromMidiNumber(note.getRealValue(), keySignature)).duration(dur).staff(s + 1).build()`. One note → add the `Note`; multiple → `new Chord(notes)` (all notes already carry the same staff).
       - `part.addMeasure(measure.build())`.
    5. `score.addPart(part.build())`.
  - Javadoc: state the mapping, the 960-divisions choice, standard-notation-only (TAB dropped), full multi-staff support, and that secondary voices are currently dropped (Decision 7A).

### Step 4: Wire into the ScoreFile facade
- `core/src/main/java/com/sheetmusic4j/core/io/ScoreFile.java`
  - Add `GUITARPRO` to the private `Format` enum.
  - In `format(Path)`, map `"gp", "gp3", "gp4", "gp5", "gpx"` → `Format.GUITARPRO`.
  - In `load`, add `case GUITARPRO -> new GuitarProImporter().fromGuitarPro(path);`.
  - In `save`, add `case GUITARPRO -> throw new UnsupportedOperationException("Saving GuitarPro files is not supported");` (switch is exhaustive over `Format`).
  - Update the class Javadoc extension list.

### Step 5: Tests
- Add the user-provided sample under `core/src/test/resources/guitarpro/` (prefer a modern `.gp` plus, if easy, a `.gp5` to exercise both loaders).
- Create `core/src/test/java/com/sheetmusic4j/core/guitarpro/GuitarProImporterTest.java` (JUnit 5, `MidiRoundTripTest` style):
  - Load the fixture via `GuitarProImporter.fromGuitarPro(...)`.
  - Assert: score has ≥1 part; first part has measures; first measure `Attributes` carries the expected `TimeSignature` and, for a grand-staff track, `staves() == 2` with two clefs; expected `Note`/`Chord`/`Rest` elements; a parsed `Pitch.toMidiNumber()` matches expectation.
  - For a multi-staff fixture, assert elements exist tagged with `staff() == 1` and `staff() == 2`.
  - `assertThrows(GuitarProException.class, () -> importer.fromGuitarPro(new ByteArrayInputStream(new byte[]{0,1,2})))`.
- Optionally add a `ScoreFile` dispatch test for a GuitarPro extension.

## Follow-up (optional, larger scope — Decision 7B)
Full concurrent multi-voice rendering:
- Add optional `voice` (int) to `Note`/`Rest` builders + accessors.
- Import every non-empty voice tagged by voice number in `GuitarProImporter`.
- Extend `Engraver` to render multiple voices per staff (stem-direction split by voice, shared horizontal positions). Update `elementStaff`/layout accordingly and add engraving tests.

## Reference Examples
- `core/src/main/java/com/sheetmusic4j/core/midi/MidiImporter.java` — closest template: `fromXxx(Path)` / `fromXxx(InputStream)` / core translation, measure-building with divisions, per-part first-measure `Attributes`.
- `core/src/main/java/com/sheetmusic4j/core/midi/MidiException.java` — exception shape for `GuitarProException`.
- `core/src/main/java/com/sheetmusic4j/core/io/ScoreFile.java:22-52` — facade dispatch + `Format` enum to extend.
- `core/src/main/java/com/sheetmusic4j/core/musicxml/MusicXmlReader.java` — `flushChord` / chord accumulation pattern; `readAttributes` shows `addClef` + `staves` usage for grand staves.
- `engraving/src/main/java/com/sheetmusic4j/engraving/Engraver.java:1400` (`elementsForStaff` / `elementStaff`) — confirms staff-based filtering the importer must satisfy.
- `engraving/src/test/java/com/sheetmusic4j/engraving/RealSamplesTest.java` — expectations for Voice + Piano grand-staff scores (good assertion patterns for staff counts).
- `core/src/test/java/com/sheetmusic4j/core/midi/MidiRoundTripTest.java` — test style and builder usage.
- `pom.xml` (parent) `dependencyManagement` + `<properties>` — pattern for a managed third-party dependency; `core/pom.xml` shows version-less module deps.

## Verification
- **Step 0 (do first, may change the plan):** Add only `net.alphatab:alphaTab` and run `mvn -pl core dependency:tree`; confirm the graph is headless (no Android/browser/GraalVM). Confirm importer + model classes exist (`alphaTab.importer.ScoreLoader`, `alphaTab.model.Score/Track/Staff/Bar/Voice/Beat/Note/MasterBar`) and verify the getter names / `MasterBar.getKeySignature()` return type / `beat.getPlaybackDuration()` used above. Adjust exclusions and getter names accordingly.
- Build: `mvn -pl core -am test` (from `sheetmusic4j/`).
- Run `GuitarProImporterTest`.
- Manual smoke test: `ScoreFile.load(Path.of("sample.gp"))` returns a populated multi-staff `Score`; feed it through `engraving`/`fxdemo` to visually confirm both staves render.

## Scope (confirmed)
This change delivers Decision 7A (primary voice per staff, full multi-staff). Concurrent multi-voice rendering (7B) is explicitly out of scope and tracked as the follow-up above.

## Risks
- **API surface:** exact alphaTab Java-interop getter names and the loader entry point must be confirmed against the resolved artifact (Verification Step 0) before coding Step 3.