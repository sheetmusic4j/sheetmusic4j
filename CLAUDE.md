# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Sheetmusic4J is a Java 21 / JavaFX library for parsing, rendering, and interacting with sheet music (MusicXML + MIDI).
It follows the same layering approach as the author's `lottie4j` project: a clean, framework-agnostic domain model at
the bottom, a pure layout/math layer in the middle, and JavaFX rendering only at the edge.

GroupId `com.sheetmusic4j`, parent artifactId `sheetmusic4j-parent`.

## Inspired by

* https://www.musicxml.com/: The standard open format for exchanging digital sheet music
* https://github.com/opensheetmusicdisplay/opensheetmusicdisplay: OpenSheetMusicDisplay renders sheet music in MusicXML
  format in browsers and headless with NodeJS.

## Modules (build order, each depends only on the ones before it)

1. **`core`** — domain model (`Score`, `Part`, `Measure`, `Attributes`, `Note`, `Chord`, `Rest`, `Pitch`, `Clef`,
   `KeySignature`, `TimeSignature`, etc.) plus MusicXML read/write (`com.sheetmusic4j.core.musicxml`, StAX-based,
   XXE-hardened) and MIDI import/export (`com.sheetmusic4j.core.midi`, `javax.sound.midi`), plus ABC and Guitar Pro.
   `com.sheetmusic4j.core.io.ScoreFile` is the load/save facade; every format implements `ScoreReader`/`ScoreWriter`
   and is registered as a `ScoreFormat` (built-ins plus `ServiceLoader`), and every format exception extends
   `ScoreFormatException`. Notes and rests carry a `staff` and a `voice`; each (staff, voice) is its own timeline and
   readers fill a voice's gaps with invisible rests (`Rest.visible() == false`). `MeasureTiming` computes exact
   element onsets (`Fraction`, never `double`, so tuplets don't drift) and is shared by MIDI/MusicXML export and
   engraving. **No JavaFX dependency.** JPMS module `com.sheetmusic4j.core`.
2. **`engraving`** — pure layout engine. `Engraver.layout(Score, LayoutOptions)` walks parts/measures and produces a
   `LayoutResult` (`SystemLayout` → `StaffLayout` → `MeasureLayout`/`GlyphPlacement`) as plain doubles + a
   renderer-agnostic `Glyph` enum. No JavaFX, no I/O — this is what makes layout unit-testable headlessly. Vertical note
   position is computed by `StaffGeometry.staffStep(Pitch, Clef)`. `Engraver` only orchestrates `layout()` (keep it the
   only class in the root package); the work lives in subpackages: `analysis` (geometry-free score queries: `PartInfo`,
   `MeasureTimeline`, `ScoreAnalysis`), `spacing` (`MeasureColumns` - one set of onset columns per measure across all
   staves/voices, so simultaneous notes align; Gourlay-style weights; per-staff minimum distances incl. lyric widths;
   `MeasureStart`, `HorizontalSpacing`, `RowPlan` row breaking by natural width), `page` (`SystemEngraver` assembles
   each row; titles, part labels, part-group brackets), `staff` (`StaffRowEngraver` lays out one staff of one row, with
   `StemPlan` - stems by voice when a staff is polyphonic - `SpanTracker` for beams/ties/slurs/tuplets per voice,
   `AnnotationLayout`, `NoteAnchors`, `StaffGeometry`), and `glyph.GlyphMapper`/`glyph.GlyphMetrics` (the single
   source of SMuFL glyph widths). Only `engraving`, `glyph`, `layout` and `placement` are exported (JPMS module
   `com.sheetmusic4j.engraving`). `LayoutOptions` carries `TextMetrics` (text measurement; default estimate) and
   `hiddenCategories` (hidden markings are not laid out and take no space). `LayoutSnapshotTest` hashes the full
   layout of every sample score; any engraving change shows up there — regenerate with
   `-Dsheetmusic4j.snapshots.update=true` only when the change is intentional.
3. **`render`** — toolkit-neutral drawing. `ScorePainter` (`com.sheetmusic4j.render`) draws a `LayoutResult` through
   the `RenderSurface` interface (stroke/fill/oval/text primitives) with the bundled Bravura font (`SmuflGlyphs`,
   `fonts/Bravura.otf`). `com.sheetmusic4j.render.awt` has `AwtRenderSurface` and `HeadlessScoreImage` for headless
   rendering to images. **No JavaFX dependency.** JPMS module `com.sheetmusic4j.render`.
4. **`fxviewer`** — JavaFX binding. `FxRenderSurface` implements `RenderSurface` for a JavaFX `GraphicsContext`,
   `FxTextMetrics` measures text with the JavaFX font; `SheetView` (page) and `StripSheetView` (one-line, play-along)
   are the `Region`/`Canvas` controls that tie `Engraver` + `ScorePainter` + `FxRenderSurface` together, sharing their
   per-note overlays through `NoteDecorations`. `NodeSheetView` is the retained-mode variant: `NodeRenderSurface` builds a
   scene graph with one `Group` per note/rest/chord (via `RenderSurface.beginElement/endElement`, which `ScorePainter`
   calls around each element's glyphs/stem), with hover/selection and click callbacks; `LayoutResult.hitTest` does
   layout-level picking for any view. `FlyOverSheetView` is the 3D variant of the strip (JavaFX `SubScene`):
   the strip's ink is painted once into transparent image tiles (`ScorePainter.setPageColor(null)`) over a coloured
   paper plane with a travelling light, a perspective camera follows the cursor, a glowing ball per (part, staff, voice)
   hops from note to note landing at each onset, and sounding noteheads light up as glowing copies
   (`ScorePainter.paintNotehead`); hops and glow are `JumpCurve`, pure functions of time. All colours and
   sizes are properties. Gradient-filled shapes don't render reliably inside the 3D `SubScene`; bake them into images.
5. **`fxdemo`** — standalone JavaFX demo app (`SheetDemoApp`, launched via `SheetDemoLauncher` to avoid classpath
   JavaFX-runtime errors). Lets you open a MusicXML/MIDI file, shows a debug pane (`ScoreInspector`), and shows a
   companion PDF side-by-side via `com.dlsc.pdfviewfx:pdfviewfx` when one exists next to the loaded file (same basename,
   `.pdf`; resolved by `PdfSibling`). `StripDemoApp` (`-Pstrip`) and `FlyOverDemoApp` (`-Pflyover`) showcase the strip
   and 3D fly-over views.

**Key architectural rule**: the same `ScorePainter` logic is reused for both on-screen JavaFX rendering
(`FxRenderSurface`) and headless AWT rendering (`AwtRenderSurface` in `render`), by depending
only on the `RenderSurface` abstraction. Never draw directly against `GraphicsContext` or `Graphics2D` outside of a
`RenderSurface` implementation — new drawing logic belongs in `ScorePainter`.

Visual regression testing (`CompareFxViewWithReferenceTest`) is documented in `fxdemo/CLAUDE.md`.

## Build

CI (`.github/workflows/ci.yml`) runs `mvn verify` on JDK 26 (Temurin). The library still targets
`maven.compiler.release=21`.

## Conventions

- `core`, `engraving` and `render` must stay JavaFX-free — this is what keeps layout and model logic unit-testable without a
  display and reusable outside JavaFX. When adding rendering features, add the drawing primitive to `RenderSurface`
  first, then implement it in both `FxRenderSurface` and `AwtRenderSurface`, and use it from `ScorePainter`.
