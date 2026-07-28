# Sheetmusic4J :: A Java (FX) library for rendering and interacting with sheet music.

[![Build](https://img.shields.io/github/actions/workflow/status/sheetmusic4j/sheetmusic4j/ci.yml?branch=main&label=Build&color=28a745)](https://github.com/sheetmusic4j/sheetmusic4j/actions/workflows/ci.yml)
[![Maven Central](https://img.shields.io/maven-central/v/com.sheetmusic4j/core.svg?label=Maven%20Central&color=28a745)](https://central.sonatype.com/artifact/com.sheetmusic4j/core)
[![License](https://img.shields.io/github/license/sheetmusic4j/sheetmusic4j?label=License&color=28a745)](http://www.apache.org/licenses/LICENSE-2.0)
[![Site](https://img.shields.io/badge/Website-sheetmusic4j.com-28a745)](https://sheetmusic4j.com)

Further technical information about this project can be found on [sheetmusic4j.com](https://sheetmusic4j.com).

Sheetmusic4J requires **Java 21** or higher.

## Modules

| Module      | Description                                                                                                                                               | JavaFX dependency |
|-------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------|
| `core`      | Core music data model (`Score`, `Part`, `Measure`, `Note`, `Chord`, `Rest`, `Clef`, `KeySignature`, `TimeSignature`) plus MusicXML, MIDI, and ABC import/export | No                |
| `engraving` | Framework-agnostic layout engine that turns a `Score` into a positioned `LayoutResult` (staves, measures, glyph placements)                               | No                |
| `fxviewer`  | JavaFX Canvas rendering of a `LayoutResult` using a SMuFL-compliant music font                                                                            | Yes               |
| `fxdemo`    | Standalone JavaFX demo application for loading and inspecting scores                                                                                      | Yes               |

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

### FX Viewer

* `SheetView`, a JavaFX `Region` that renders a `Score` on a `Canvas`
* Drawing logic is shared with headless test rendering through a `RenderSurface` abstraction

### FX Demo

* Opens MusicXML/MIDI/ABC files and displays a debug/inspection pane alongside the rendered score
* Shows a companion PDF side-by-side (via [pdfviewfx](https://github.com/dlsc-software-consulting-gmbh/PDFViewFX)) when
  one exists next to the loaded file with the same base name

## Requirements

- Java 21
- Maven 3.9+

## Build

```bash
mvn clean install
```

## Run the demo

```bash
mvn -pl fxdemo javafx:run
```

## Status

Module structure, domain model, MusicXML/MIDI/ABC I/O, layout engine, JavaFX rendering, and the demo app are in place;
rendering fidelity and format coverage are still being expanded.
