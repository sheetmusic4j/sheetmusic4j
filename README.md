# Sheetmusic4J :: A Java (FX) library for rendering and interacting with sheet music.

[![Build](https://img.shields.io/github/actions/workflow/status/sheetmusic4j/sheetmusic4j/ci.yml?branch=main&label=Build&color=28a745)](https://github.com/sheetmusic4j/sheetmusic4j/actions/workflows/ci.yml)
[![Maven Central](https://img.shields.io/maven-central/v/com.sheetmusic4j/core.svg?label=Maven%20Central&color=28a745)](https://central.sonatype.com/artifact/com.sheetmusic4j/core)
[![License](https://img.shields.io/github/license/sheetmusic4j/sheetmusic4j?label=License&color=28a745)](http://www.apache.org/licenses/LICENSE-2.0)
[![Site](https://img.shields.io/badge/Website-sheetmusic4j.com-28a745)](https://sheetmusic4j.com)

Further technical information about this project can be found on [sheetmusic4j.com](https://sheetmusic4j.com).

Sheetmusic4J requires **Java 21** or higher.

## Modules

| Module      | Description                                                                                                                                                     | JavaFX dependency |
|-------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------|
| `core`      | Core music data model (`Score`, `Part`, `Measure`, `Note`, `Chord`, `Rest`, `Clef`, `KeySignature`, `TimeSignature`) plus MusicXML, MIDI, ABC, and LilyPond import/export | No                |
| `engraving` | Framework-agnostic layout engine that turns a `Score` into a positioned `LayoutResult` (staves, measures, glyph placements)                                     | No                |
| `render`    | Toolkit-neutral drawing of a `LayoutResult` with a SMuFL-compliant music font                                                                                  | No                |
| `render-awt` | Headless rendering to images through AWT (`AwtRenderSurface`, `HeadlessScoreImage`)                                                                           | No                |
| `fxviewer`  | JavaFX controls (`SheetView`, `StripSheetView`, `FlyOverSheetView`, `NodeSheetView`) that draw a `LayoutResult` on a `Canvas`, in 3D, or as a node scene graph   | Yes               |
| `fxdemo`    | Standalone JavaFX demo applications for loading and inspecting scores, plus strip, 3D fly-over and node-view showcases                                         | Yes               |

### Sheetmusic Terminology

See [docs/NOTATION_ELEMENTS.md](docs/NOTATION_ELEMENTS.md) for a visual glossary of the notation elements Sheetmusic4J
renders, so you can point at something in a screenshot and know exactly which class/method is responsible for it.

### Core

* Loading and saving of MusicXML 4.0 files, see https://www.musicxml.com/for-developers/
* Importing and exporting MIDI files (`javax.sound.midi`)
* Importing and exporting ABC music notation files (see https://abcnotation.com/)
* `ScoreFile` convenience facade that dispatches to the right reader/writer based on file extension (`.musicxml`,
  `.xml`, `.mxl`, `.mid`, `.midi`, `.abc`)
* Testfiles from https://www.musicxml.com/music-in-musicxml/example-set/

### Engraving

* Pure layout math (no JavaFX) that positions staves, measures, clefs, and notes from a `Score`, so it can be
  unit-tested headlessly and reused by any renderer

### Render

* `ScorePainter` draws a `LayoutResult` through a `RenderSurface` abstraction, so the same drawing logic serves JavaFX
  and headless rendering
* `HeadlessScoreImage` (in `render-awt`) renders a `Score` to a `BufferedImage` without a display

### FX Viewer

* `SheetView`, a JavaFX `Region` that renders a `Score` on a `Canvas`, and `StripSheetView`, a one-line play-along view
* `FlyOverSheetView`, a 3D variant of the strip view: the score lies on a sheet of paper, a perspective camera flies
  along it while it plays, a glowing ball per voice hops from note to note, and sounding noteheads light up
* `NodeSheetView`, a retained-mode variant that builds a JavaFX scene graph with one node group per note, rest and chord,
  so each element can take mouse and keyboard events, effects and selection; `LayoutResult.hitTest` does the same
  picking on the plain layout for the canvas views

### FX Demo

* Opens MusicXML/MIDI/ABC/LilyPond files and displays a debug/inspection pane alongside the rendered score
* Shows a companion PDF side-by-side (via [pdfviewfx](https://github.com/dlsc-software-consulting-gmbh/PDFViewFX)) when
  one exists next to the loaded file with the same base name
* "Report Visual Issue..." button to capture rendering problems, see [CONTRIBUTING.md](CONTRIBUTING.md)
* `StripDemoApp` and `FlyOverDemoApp` showcase the one-line strip view and the 3D fly-over view, with play/pause,
  tempo, and position controls
* `NodeDemoApp` showcases the node view: hover and select elements, drag notes to snap them to staff positions
  (Shift = sharp, Alt = flat), hide and restore them

## Requirements

- Java 21
- Maven 3.9+

## Build

```bash
mvn clean install
```

## Run the demo

```bash
mvn -pl fxdemo javafx:run              # main demo app (SheetDemoApp)
mvn -pl fxdemo -Pstrip javafx:run      # one-line strip view (StripDemoApp)
mvn -pl fxdemo -Pflyover javafx:run    # 3D fly-over view (FlyOverDemoApp)
mvn -pl fxdemo -Pnode javafx:run       # node-based view with draggable notes (NodeDemoApp)
```

See [CONTRIBUTING.md](CONTRIBUTING.md) for a tour of the demo app and how to report rendering issues.

## Status

Module structure, domain model, MusicXML/MIDI/ABC/LilyPond I/O, layout engine, JavaFX rendering, and the demo app are in place;
rendering fidelity and format coverage are still being expanded.
