id: e3c3f41e-8fe9-487c-9d54-f45fab73ff1e
sessionId: a331edca-fd8b-466b-a43c-f35417babd77
date: '2026-07-19T20:37:57.573Z'
label: Sheet4j Framework Development
---
# Sheet4j Implementation Session Summary

## Project Overview
Sheet4j is a multi-module Maven Java 21 library for parsing, rendering, and interacting with sheet music. GroupId `com.sheetmusic4j`, parent artifactId `sheetmusic4j-parent`, version `1.0.0-SNAPSHOT`. Root directory: `sheetmusic4j/`. URL: https://github.com/codewriterbv/sheetmusic4j

## Modules (all in parent pom `sheetmusic4j/pom.xml`)
1. **core** — domain model + MusicXML/MIDI IO. No JavaFX.
2. **engraving** — framework-agnostic layout engine (Score → LayoutResult). Depends on core. No JavaFX.
3. **fxviewer** — JavaFX Canvas rendering. Depends on core + engraving + javafx-graphics.
4. **fxdemo** — standalone JavaFX demo app (NEW, added this session). Depends on core, engraving, fxviewer, javafx-controls, javafx-graphics, pdfviewfx.

## Key Versions/Properties (parent pom)
- `maven.compiler.release=21`, `javafx.version=21.0.4`, `junit.version=5.11.0`, `pdfviewfx.version=3.0.4`
- Plugins managed: maven-compiler-plugin 3.13.0, maven-surefire-plugin 3.3.1, javafx-maven-plugin 0.0.8
- **IMPORTANT**: Original scaffolding had child poms referencing wrong parent version `0.1.0-SNAPSHOT`; corrected to `1.0.0-SNAPSHOT` in all child poms.

## Completed Work (First Task - Full Library Implementation)

### core module (`sheetmusic4j/core/src/main/java/com/sheetmusic4j/core/`)
**model/**: `Step` (enum C-B with semitonesFromC), `NoteType` (enum with xmlValue/quarterValue/fromXml/fromQuarterValue), `ClefSign` (enum G/F/C/PERCUSSION/TAB/NONE), `Accidental` (enum by alter), `Pitch` (record step/octave/alter, toMidiNumber/fromMidiNumber/diatonicStepNumber), `Duration` (record value/divisions, inQuarters/ofQuarters), `Clef` (record sign/line, treble()/bass()), `KeySignature` (record fifths), `TimeSignature` (record beats/beatType), `Attributes` (builder: divisions/key/time/clef, all Optional), `MusicElement` (sealed interface permits Note/Rest/Chord), `Note` (builder, implements MusicElement), `Rest` (builder), `Chord` (list of notes), `Measure` (builder with number/attributes/elements), `Part` (builder id/name/measures), `Score` (builder workTitle/movementTitle/parts).

**musicxml/**: `MusicXmlException`, `MusicXmlReader` (StAX-based, XXE-hardened, reads score-partwise), `MusicXmlWriter` (StAX with IndentingWriter helper, uses writeStartDocument/writeDTD).

**midi/**: `MidiException`, `MidiImporter` (javax.sound.midi, PPQ only, resolution→divisions, 4/4 default, one track→one part), `MidiExporter` (format 1, one track per part, tempo meta event).

**io/**: `ScoreFile` (facade: load/save dispatching on extension - .musicxml/.xml/.mxl→MusicXML, .mid/.midi→MIDI).

**Tests** (`sheetmusic4j/core/src/test/`): `PitchTest`, `MusicXmlReaderTest`, `MusicXmlWriterTest` (round-trip), `MidiRoundTripTest`. Resource: `c-major-scale.musicxml` (2-measure C major scale).

### engraving module (`sheetmusic4j/engraving/src/main/java/com/sheetmusic4j/engraving/`)
`Glyph` (enum), `LayoutOptions` (record with defaults()), `GlyphPlacement` (record x/y/glyph/staffStep), `StaffLayout` (record), `MeasureLayout` (record), `SystemLayout` (record), `LayoutResult` (record with staves()), `Engraver` (layout method; key static method `staffStep(Pitch, Clef)` computes vertical position - treble top line=F5=staffStep 0, bottom=E4=staffStep 8, middle C4=staffStep 10).

**Tests**: `EngraverTest`, `StaffStepTest`.

### fxviewer module (`sheetmusic4j/fxviewer/src/main/java/com/sheetmusic4j/fxviewer/`)
`ScoreRenderer` (draws LayoutResult to GraphicsContext), `SheetView` (Region containing Canvas, setScore method), `SheetViewerApp` — **EMPTIED this session** (content set to empty string, app moved to fxdemo).
- fxviewer pom: removed javafx-controls dependency (only needs javafx-graphics now).

## Second Task (Current - fxdemo module with PDF side-by-side)

User requested: (1) Add `fxdemo` module similar to reference `/Users/frankdelporte/Documents/GitHub/lottie4j/fxfileviewer` (NOT accessible - outside workspace) with file load menu, debug info; move SheetViewerApp into it. (2) When a loaded file has a companion PDF (same base name, e.g. `song.musicxml`→`song.pdf`), show it side by side using `com.dlsc.pdfviewfx:pdfviewfx`.

### Completed:
- **Parent pom** (`sheetmusic4j/pom.xml`): added `fxdemo` to `<modules>`, added fxdemo to dependencyManagement, added `pdfviewfx.version=3.0.4` property, added pdfviewfx dependency to dependencyManagement.
- **fxdemo pom** (`sheetmusic4j/fxdemo/pom.xml`): created with deps (core, engraving, fxviewer, pdfviewfx, javafx-controls, javafx-graphics, junit test), javafx-maven-plugin configured with `mainClass=com.sheetmusic4j.fxdemo.SheetDemoApp`, property `exec.mainClass`.
- **fxdemo classes** (`sheetmusic4j/fxdemo/src/main/java/com/sheetmusic4j/fxdemo/`):
  - `ScoreInspector` — testable, produces debug text (Stats record: parts/measures/notes/rests/chords; describe(Score) method).
  - `SheetDemoApp` — JavaFX Application. BorderPane: MenuBar (File: Open Cmd+O/Reload Cmd+R/Close/Exit Cmd+Q; Help: About) top, SplitPane center (scorePane with SheetView in ScrollPane | debugPane with TextArea; pdfPane inserted at index 1 when PDF present), status Label bottom. Uses `PDFView` from `com.dlsc.pdfviewfx.PDFView`, loads via `pdfView.load(InputStream)` in try-with-resources. showPdf/removePdf manage split items dynamically (dividers 0.42/0.80 with PDF, 0.72 without). Uses `PdfSibling.existingPathFor()`.
  - `PdfSibling` — testable helper: `pathFor(Path)` (replaces extension with .pdf, empty if input is .pdf), `existingPathFor(Path)` (filters by Files::isRegularFile).
  - `DemoLauncher` — plain main class (does NOT extend Application) calling `SheetDemoApp.main(args)` to avoid "JavaFX runtime components missing" error when launched from classpath.
- **Tests** (`sheetmusic4j/fxdemo/src/test/java/com/sheetmusic4j/fxdemo/`): `ScoreInspectorTest`, `PdfSiblingTest` (uses @TempDir).
- **Launch config** (`sheetmusic4j/.vscode/launch.json`): "Sheet4j Demo" launching `com.sheetmusic4j.fxdemo.DemoLauncher`, projectName fxdemo.

## Pending/Incomplete
- **Build verification NOT completed**: The final `runTask` for task "sheetmusic4j" (Maven build, workspaceRoot "sheetmusic4j") was CANCELED/interrupted. Must re-run to verify compilation + tests pass, especially:
  - **RISK**: `com.dlsc.pdfviewfx.PDFView.load(InputStream)` API signature is assumed but NOT verified. If it doesn't exist or has different signature, SheetDemoApp won't compile. May need to check actual pdfviewfx 3.0.4 API. Also verify pdfviewfx dependency resolves from Maven Central.
  - Verify no lingering references to the removed `SheetViewerApp`.

## Build/Task Info
- Available task: label "sheetmusic4j", workspaceRoot "sheetmusic4j" (runs Maven build). Also "Build Workspace".
- Run demo via: `mvn -pl fxdemo javafx:run` or the launch config.
- Note: IDE showed "Missing mandatory Classpath entries" diagnostics for engraving/fxdemo files — these are transient IDE project-import issues that resolve after Maven build, NOT real errors.

## Next Steps
1. Re-run the "sheetmusic4j" Maven build task to verify all modules compile and all tests pass.
2. If pdfviewfx API mismatch occurs, correct the `PDFView.load(...)` call in `SheetDemoApp.java`.
3. Confirm final state is clean.