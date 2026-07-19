# Sheet4J

A JavaFX library for rendering and interacting with sheet music.

## Modules

| Module | Description | JavaFX dependency |
|---|---|---|
| `core` | Core music data model (`Score`, `Part`, `Measure`, `Note`, `Chord`, `Rest`, `Clef`, `KeySignature`, `TimeSignature`) | No |
| `fxviewer` | JavaFX Canvas rendering of a layout using a SMuFL-compliant music font | Yes |

### Core

* Loading and saving of MusicXML 4.0 files, see https://www.musicxml.com/for-developers/ 
* Testfiles from https://www.musicxml.com/music-in-musicxml/example-set/

## Requirements

- Java 21
- Maven 3.9+

## Build

```bash
mvn clean install
```

## Status

Early scaffold. Module structure and POMs are in place; implementation is in progress.
