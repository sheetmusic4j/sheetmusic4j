id: 74b2edfc-c0c4-4c8b-91c9-5540c6e14d37
sessionId: 86cebfc6-2e8e-467e-a105-460b644b95b6
date: '2026-07-19T20:05:06.546Z'
label: 'Bootstrap Sheetmusic4J: MusicXML + MIDI model, I/O, and JavaFX viewer'
---
# Bootstrap Sheetmusic4J: MusicXML + MIDI model, I/O, and JavaFX viewer

## Goal
Turn the empty Sheetmusic4J scaffold into a working foundation that can: load a MusicXML file into a clean Java model, import a MIDI file into that same model, save the model back to MusicXML and MIDI, and render the model in a JavaFX component. This mirrors the lottie4j approach (clean `core` model with reader/writer handlers + a separate JavaFX viewer module).

## Current State (verified)
- Modules exist as scaffolding only: `sheetmusic4j/core` and `sheetmusic4j/fxviewer`, each with empty `src/main/java`.
- **No Java sources exist yet.**
- Inconsistencies to fix first:
  - Parent `pom.xml` version is `1.0.0-SNAPSHOT`; child POMs (`core`, `fxviewer`) reference parent `0.1.0-SNAPSHOT`. Build will fail.
  - `fxviewer/pom.xml` depends on `com.sheetmusic4j:sheetmusic4j-engraving`, which is not yet declared as a module nor in `dependencyManagement`. This plan adds an `engraving` module and updates the fxviewer dependency's artifactId to `engraving`.
  - No MIDI concern reflected anywhere; README mentions MusicXML only.

## Decisions (confirmed)
1. **MusicXML approach**: Hand-rolled clean domain model + StAX/JAXB, scoped to `score-partwise` first (no external MusicXML binding library).
2. **Engraving layer**: Create a dedicated `engraving` module (artifactId `engraving`, short convention matching `core`/`fxviewer`). It sits between `core` and `fxviewer`, converting a `Score` into a framework-agnostic positioned layout (`LayoutResult`) that the viewer renders. The existing `fxviewer` dependency on `sheetmusic4j-engraving` will be updated to `engraving`.

## Design

### Module layout (initial)
- `core` (no JavaFX):
  - `com.sheetmusic4j.core.model` — immutable-ish domain model (records/POJOs): `Score`, `Part`, `Measure`, `Attributes`, `Clef`, `KeySignature`, `TimeSignature`, `Note`, `Pitch`, `Chord`, `Rest`, `Duration`, enums (`Step`, `NoteType`, `ClefSign`, `Accidental`).
  - `com.sheetmusic4j.core.musicxml` — `MusicXmlReader`, `MusicXmlWriter` (map XML <-> model).
  - `com.sheetmusic4j.core.midi` — `MidiImporter` (MIDI -> `Score`), `MidiExporter` (`Score` -> MIDI) using JDK `javax.sound.midi`.
  - `com.sheetmusic4j.core.io` — `ScoreFileLoader` / `ScoreFileSaver` convenience facade that dispatches by extension (`.musicxml`, `.xml`, `.mxl`, `.mid`).
- `engraving` (no JavaFX) — **new module**: pure layout math that turns a `Score` into positioned, renderer-agnostic geometry.
  - `com.sheetmusic4j.engraving` — `Engraver` (entry point: `LayoutResult layout(Score, LayoutOptions)`), `LayoutResult`, `SystemLayout`, `MeasureLayout`, `GlyphPlacement`, `StaffLayout`, `LayoutOptions` (staff spacing, staff-line gap, page/system width). Coordinates are plain doubles + SMuFL glyph identifiers; no JavaFX types.
- `fxviewer` (JavaFX): `com.sheetmusic4j.fxviewer.SheetView` — a `Region`/`Canvas`-based control that consumes a `LayoutResult` from `engraving` and draws it. Depends on `core` + `engraving`.

### Data model philosophy (mirrors lottie4j)
- Clean, framework-agnostic model classes that do NOT expose XML/MIDI details.
- Readers/writers convert between external formats and the model, so the model is the single source of truth for the viewer.
- Start with `score-partwise` MusicXML; support both uncompressed (`.musicxml`/`.xml`) and compressed (`.mxl` = ZIP containing `META-INF/container.xml` + score) as a follow-up.

### Scope for first iteration (keep it renderable, not exhaustive)
MusicXML elements handled initially: `score-partwise`, `part-list`/`score-part`, `part`, `measure`, `attributes` (`divisions`, `key/fifths`, `time/beats`+`beat-type`, `clef/sign`+`line`), `note` (`pitch/step`+`octave`+`alter`, `duration`, `type`, `rest`, `chord`, `dot`), `backup`, `forward`. Everything else parsed leniently / ignored initially.

## Implementation Steps

### Step 1: Fix POM scaffolding and add the engraving module so the reactor builds
- Align all POMs to one version — use `1.0.0-SNAPSHOT`:
  - `sheetmusic4j/core/pom.xml` — change parent `<version>` `0.1.0-SNAPSHOT` -> `1.0.0-SNAPSHOT`.
  - `sheetmusic4j/fxviewer/pom.xml` — same parent version fix.
- `sheetmusic4j/pom.xml` (parent):
  - Add `<module>engraving</module>` to `<modules>` (order: `core`, `engraving`, `fxviewer`).
  - Add `engraving` to `<dependencyManagement>`: `com.sheetmusic4j:engraving:${project.version}` (short artifactId convention).
- `fxviewer/pom.xml` — **update** the existing dependency artifactId from `sheetmusic4j-engraving` to `engraving`; keep `core` + `javafx-graphics` + `javafx-controls`.
- Confirm `maven.compiler.release` = 21 (already set).

### Step 1b: Create the engraving module skeleton
- `sheetmusic4j/engraving/pom.xml` — `<artifactId>engraving</artifactId>`, parent `sheetmusic4j-parent:1.0.0-SNAPSHOT`, `packaging jar`, depends on `core` + `junit-jupiter` (test). **No JavaFX dependency.**
- Create `sheetmusic4j/engraving/src/main/java/` and `sheetmusic4j/engraving/src/test/java/`.
- (Actual layout classes are implemented in Step 6b below.)

### Step 2: Create the core domain model
Under `sheetmusic4j/core/src/main/java/com/sheetmusic4j/core/model/`:
- `Score.java` — title/work info + `List<Part> parts`.
- `Part.java` — id, name, `List<Measure> measures`.
- `Measure.java` — number, optional `Attributes`, ordered `List<MusicElement>` (notes/rests/chords).
- `Attributes.java` — `divisions`, `KeySignature`, `TimeSignature`, `Clef`.
- `Clef.java`, `KeySignature.java`, `TimeSignature.java`.
- `MusicElement.java` (sealed interface) with `Note`, `Rest`, `Chord` implementations.
- `Note.java` — `Pitch`, `Duration`, `NoteType`, dots, tie/chord flags.
- `Pitch.java` — `Step step`, `int octave`, `int alter`; helper `toMidiNumber()`.
- `Duration.java` — divisions-based value + helpers.
- Enums: `Step`, `NoteType`, `ClefSign`, `Accidental`.
- Add `module-info.java` (optional now) or keep classpath; prefer no JPMS initially for simplicity.
- Use Java 21 `record`s where the type is a pure value (`Pitch`, `TimeSignature`, `KeySignature`, `Clef`); use classes with builders for `Score`/`Part`/`Measure`.

### Step 3: MusicXML reader
Under `sheetmusic4j/core/src/main/java/com/sheetmusic4j/core/musicxml/`:
- `MusicXmlReader.java` — `Score read(InputStream)` / `Score read(Path)`.
  - Use `javax.xml.stream` (StAX) `XMLStreamReader` for a dependency-free parse, OR JAXB with internal DTOs.
  - Parse `score-partwise` per Step-1 scope; map into model.
- `MusicXmlException.java` — wraps parse errors.

### Step 4: MusicXML writer
- `MusicXmlWriter.java` — `void write(Score, OutputStream)` / `write(Score, Path)`.
  - Emit `score-partwise` with the DOCTYPE / MusicXML 4.0 namespace header.
  - Round-trip guarantee target: a file read by `MusicXmlReader` and written back should be structurally equivalent for the supported subset.

### Step 5: MIDI import/export
Under `sheetmusic4j/core/src/main/java/com/sheetmusic4j/core/midi/`:
- `MidiImporter.java` — `Score fromMidi(Path)` using `MidiSystem.getSequence(...)`.
  - Read `Sequence` resolution (PPQ), tempo meta events, NOTE_ON/NOTE_OFF; group by track -> `Part`; quantize note start/duration into `divisions`; slice into `Measure`s using a default or detected time signature (default 4/4 initially).
- `MidiExporter.java` — `void toMidi(Score, Path)` building a `Sequence` (one track per `Part`), writing tempo + NOTE_ON/NOTE_OFF from `Pitch.toMidiNumber()` and `Duration`.
- Document quantization assumptions (this is inherently lossy).

### Step 6: IO facade
Under `sheetmusic4j/core/src/main/java/com/sheetmusic4j/core/io/`:
- `ScoreFile.java` (or `ScoreFileLoader`/`ScoreFileSaver`) — dispatch by extension: `.musicxml`/`.xml`/`.mxl` -> MusicXML; `.mid`/`.midi` -> MIDI.

### Step 6b: Engraving (layout) module implementation
Under `sheetmusic4j/engraving/src/main/java/com/sheetmusic4j/engraving/`:
- `LayoutOptions.java` — record: staff-line gap, staff spacing between parts, system/page width, left/right margins, default font size.
- `Engraver.java` — `LayoutResult layout(Score score, LayoutOptions options)`. Walks parts/measures, assigns x positions per measure (simple even spacing initially), computes y for each note from `Pitch` + `Clef`, and produces glyph placements.
- `LayoutResult.java` — top-level result: `List<SystemLayout>` (or a flat list of placed staves + glyphs for the first iteration).
- `SystemLayout.java`, `StaffLayout.java`, `MeasureLayout.java` — positioned containers (x/y/width/height as doubles).
- `GlyphPlacement.java` — record: `double x`, `double y`, `Glyph glyph` (SMuFL id / semantic enum), plus staff-line-relative info.
- `Glyph.java` (enum) — minimal set: staff line, note heads, stems, clefs, time-signature digits, rests. Keep renderer-agnostic (no JavaFX types anywhere in this module).

### Step 7: JavaFX viewer (minimal)
Under `sheetmusic4j/fxviewer/src/main/java/com/sheetmusic4j/fxviewer/`:
- `SheetView.java` — extends `Region`, holds a `Canvas`, exposes `setScore(Score)`. Internally calls `Engraver.layout(...)` to obtain a `LayoutResult`, then draws via `ScoreRenderer`; re-layouts/redraws on resize.
- `ScoreRenderer.java` — consumes a `LayoutResult` and draws it on the `Canvas` `GraphicsContext`: staff lines, clef/time/key hints, and note heads at the positions computed by `engraving`. Start simple with vector shapes (lines/ellipses); SMuFL font integration is a later enhancement noted in README.
- Optional `SheetViewerApp.java` — a tiny `Application` demo `main` to open a file and display it (guarded so it is not required for the library).

### Step 8: Tests and sample data
- `sheetmusic4j/core/src/test/java/com/sheetmusic4j/core/` :
  - `MusicXmlReaderTest`, `MusicXmlWriterTest` (round-trip), `MidiImporterTest`, `MidiExporterTest`.
- `sheetmusic4j/engraving/src/test/java/com/sheetmusic4j/engraving/` :
  - `EngraverTest` — feed a small `Score`, assert the `LayoutResult` has expected staff/measure/glyph counts and monotonically increasing x positions (pure math, no JavaFX so it runs headless).
- `sheetmusic4j/core/src/test/resources/` — add a small `score-partwise` sample (e.g., a two-measure C-major scale) and a small `.mid`. Real-world examples: https://www.musicxml.com/music-in-musicxml/example-set/ (add one small file, respecting licensing).

## Reference Examples
- lottie4j `core` (external, not accessible here) is the conceptual model: POJO model + `handler.Reader`/writer + a viewer module. Follow the same separation of concerns.
- Existing scaffold POMs: `sheetmusic4j/pom.xml` (dependencyManagement + pluginManagement to copy versions from), `sheetmusic4j/core/pom.xml`, `sheetmusic4j/fxviewer/pom.xml`. Model the new `sheetmusic4j/engraving/pom.xml` on `core/pom.xml` (no JavaFX) plus a `core` dependency.
- MusicXML spec / dev resources: https://www.musicxml.com/for-developers/
- JDK MIDI: `javax.sound.midi.MidiSystem`, `Sequence`, `Track`, `ShortMessage`, `MetaMessage`.

## Verification
- `cd sheetmusic4j && mvn clean install` builds all three modules (`core`, `engraving`, `fxviewer`) with POM fixes + new module resolved.
- Unit tests pass: MusicXML round-trip, MIDI import/export smoke tests, and `EngraverTest` layout assertions.
- Manual: run `SheetViewerApp` (if added) against the sample MusicXML and confirm staves + notes render.
- Round-trip check: `read(sample.musicxml)` -> `write(out.musicxml)` -> re-read produces an equal `Score` for the supported subset.
- Verify `engraving` has **no** JavaFX on its classpath (keeps layout reusable/testable headless).