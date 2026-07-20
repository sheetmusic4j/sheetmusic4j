# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Sheetmusic4J is a Java 21 / JavaFX library for parsing, rendering, and interacting with sheet music (MusicXML + MIDI). It follows the same layering approach as the author's `lottie4j` project: a clean, framework-agnostic domain model at the bottom, a pure layout/math layer in the middle, and JavaFX rendering only at the edge.

GroupId `com.sheetmusic4j`, parent artifactId `sheetmusic4j-parent`.

## Modules (build order, each depends only on the ones before it)

1. **`core`** — domain model (`Score`, `Part`, `Measure`, `Attributes`, `Note`, `Chord`, `Rest`, `Pitch`, `Clef`, `KeySignature`, `TimeSignature`, etc.) plus MusicXML read/write (`com.sheetmusic4j.core.musicxml`, StAX-based, XXE-hardened) and MIDI import/export (`com.sheetmusic4j.core.midi`, `javax.sound.midi`). `com.sheetmusic4j.core.io.ScoreFile` is the load/save facade that dispatches by file extension (`.musicxml`/`.xml`/`.mxl` → MusicXML, `.mid`/`.midi` → MIDI). **No JavaFX dependency.**
2. **`engraving`** — pure layout engine. `Engraver.layout(Score, LayoutOptions)` walks parts/measures and produces a `LayoutResult` (`SystemLayout` → `StaffLayout` → `MeasureLayout`/`GlyphPlacement`) as plain doubles + a renderer-agnostic `Glyph` enum. No JavaFX, no I/O — this is what makes layout unit-testable headlessly. Vertical note position is computed by `Engraver.staffStep(Pitch, Clef)`.
3. **`fxviewer`** — rendering. The drawing logic lives in `ScorePainter`, which draws a `LayoutResult` through the `RenderSurface` interface (stroke/fill/oval/text primitives) rather than directly against JavaFX. `FxRenderSurface` implements `RenderSurface` for a JavaFX `GraphicsContext`; `SheetView` is the `Region`/`Canvas` control that ties `Engraver` + `ScorePainter` + `FxRenderSurface` together for on-screen display.
4. **`fxdemo`** — standalone JavaFX demo app (`SheetDemoApp`, launched via `DemoLauncher` to avoid classpath JavaFX-runtime errors). Lets you open a MusicXML/MIDI file, shows a debug pane (`ScoreInspector`), and shows a companion PDF side-by-side via `com.dlsc.pdfviewfx:pdfviewfx` when one exists next to the loaded file (same basename, `.pdf`; resolved by `PdfSibling`).

**Key architectural rule**: the same `ScorePainter` logic is reused for both on-screen JavaFX rendering (`FxRenderSurface`) and headless AWT rendering in tests (`AwtRenderSurface` in `fxdemo`'s test sources), by depending only on the `RenderSurface` abstraction. Never draw directly against `GraphicsContext` or `Graphics2D` outside of a `RenderSurface` implementation — new drawing logic belongs in `ScorePainter`.

## Visual regression testing

`fxdemo`'s `CompareFxViewWithPdfTest` renders a score headlessly (`HeadlessScoreImage`, using the production `Engraver` + `ScorePainter` + `AwtRenderSurface`) and compares it against a reference image via `ImageSimilarity`:
- A deterministic "produces ink" check always runs (guards against a blank/broken render).
- A strict pixel-similarity check against `/reference/c-major-scale.png` or `.pdf` runs only if that reference resource is present on the test classpath; otherwise it's skipped via `Assumptions.assumeTrue`. To enable it, drop a reference render into `fxdemo/src/test/resources/reference/`.
- PDF references are rasterized via `PdfRasterizer`, which invokes PDFBox reflectively (works with either PDFBox 2.x or 3.x, or skips gracefully if PDFBox isn't on the classpath).
- Similarity threshold is tunable via `-Dsheetmusic4j.compare.threshold=<0..1>` (default `0.6`).

`fxdemo/src/test/resources/xmlsamples/` contains real-world MusicXML/MXL sample scores (with matching reference PDFs/PNGs for several) sourced from https://www.musicxml.com/music-in-musicxml/example-set/ — useful for exercising the reader/engraver against non-trivial scores beyond the minimal `c-major-scale.musicxml` fixture.

## Build & test

Requires Java 21, Maven 3.9+.

```bash
mvn clean install                       # build all modules
mvn test                                # run all tests
mvn -pl core test                       # run tests for a single module
mvn -pl core -Dtest=MusicXmlReaderTest test   # run a single test class
mvn -pl fxdemo javafx:run               # launch the JavaFX demo app
```

CI (`.github/workflows/maven.yml`) runs tests with `mvn test -Pheadless-tests` and a separate build/JavaDoc-validation job with `mvn -Dmaven.test.skip=true install` + `mvn javadoc:javadoc`, on JDK 25 (Zulu) even though `maven.compiler.release` is 21 — don't assume the CI JDK version dictates the language level.

Release is a manual `workflow_dispatch` (`.github/workflows/release.yml`) that bumps the version with `versions:set` and runs `mvn -Prelease clean deploy` to Maven Central.

## Conventions

- Value types in `core.model` are Java records (`Pitch`, `Clef`, `KeySignature`, `TimeSignature`, `Duration`); container types (`Score`, `Part`, `Measure`, `Attributes`) use builders. `MusicElement` is a sealed interface (`Note`, `Rest`, `Chord`).
- `engraving` and `core` must stay JavaFX-free — this is what keeps layout and model logic unit-testable without a display and reusable outside JavaFX. When adding rendering features, add the drawing primitive to `RenderSurface` first, then implement it in both `FxRenderSurface` and `AwtRenderSurface`, and use it from `ScorePainter`.
