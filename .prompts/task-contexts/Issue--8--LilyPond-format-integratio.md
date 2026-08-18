id: 65769a36-e21d-47bd-ae06-53d8ef221e21
sessionId: 59b01a0d-8d58-4232-a69e-d60ec622f74e
date: '2026-08-18T09:41:52.085Z'
label: 'Issue #8: LilyPond format integration'
---
# Issue #8 – Integrate the LilyPond standard

## Goal

Add LilyPond (`.ly`) as a first-class notation format alongside MusicXML, MIDI and ABC:
a `LilyPondReader` that parses a common subset of `.ly` files into the `Score` model, a
`LilyPondWriter` that exports a `Score` back to `.ly`, wired into the `ScoreFile` facade
by file extension, with unit / round-trip / examples tests seeded from
[lilypond.org/examples.html](https://lilypond.org/examples.html).

## Scope (MVP, mirrors the ABC integration)

Reader/writer must handle the same expressive slice ABC does; anything richer than that
is silently skipped by the reader and simply not emitted by the writer, so real-world
files survive round-tripping structurally.

**Supported**

- Comments: line (`% ...`) and block (`%{ ... %}`)
- Directives: `\version "..."` (parsed, re-emitted by writer), `\language "..."` (parsed, ignored)
- Score wrapper: `\score { ... }`, `\book { ... }`, `\new Staff { ... }`, `\new Voice { ... }`
  (only the first staff of the first score is kept for MVP; extra staves ignored)
- Header block: `\header { title = "..." composer = "..." subtitle = "..." }`
- Pitch modes: `\relative c' { ... }` and absolute mode. Dutch pitch names only
  (`c d e f g a b`, `cis`/`ces`/`bis`/`beses`), octave marks `'` and `,`, reminder `!`
  and cautionary `?` accidentals
- Rests: `r`, `R` (full-measure), `s` (spacer → skipped)
- Durations: numeric (`1 2 4 8 16 32 64`) with any number of dots (`4.`, `8..`), duration
  carry-over (a bare pitch inherits the previous duration)
- Chords: `<c e g>4`
- Ties: `~`
- Slurs: `(` … `)`
- Tuplets: `\tuplet 3/2 { ... }` (also legacy `\times 2/3 { ... }`)
- Bar checks / bar lines: `|` (bar check, treated as a bar), `\bar "|:"`, `\bar ":|"`,
  `\bar "||"`, `\bar "|."`
- Attributes: `\time 4/4`, `\key c \major` / `\key a \minor`, `\clef treble` / `bass` / `alto` / `tenor`
- Tempo: `\tempo 4 = 120`
- Chord symbols: `\chordmode { ... }` block or inline via `\chords`
- Lyrics: `\addlyrics { ... }` and `\lyricmode { ... }` with `--` mid / `__` extend / `_` skip

**Deliberately dropped for MVP** (parser tolerates by skipping, writer never emits):
polyphony `<< ... >>`, `\repeat volta`/`unfold`, ossia, `\override`/`\set`, ornaments/decorations
(`\trill`, `\mordent`, …), articulations except a handful (`->` accent, `-.` staccato,
`--` tenuto — mapped to `Articulation`), multi-staff `\new PianoStaff`, ChordNames
placements, English pitch names, `\transpose`, grace notes (`\grace { ... }` — parsed
pitches captured onto next note's `graceNotes`, mirroring ABC's `{ ... }` handling).

## Design

Mirror the ABC module layout to keep review small and downstream conventions consistent:

- `core/src/main/java/com/sheetmusic4j/core/lilypond/`
  - `LilyPondException.java` — `RuntimeException` matching `AbcException`
  - `LilyPondReader.java` — `read(Path)` / `read(InputStream)` returning `Score`
  - `LilyPondWriter.java` — `write(Score, Path)` / `write(Score, OutputStream)`
  - `LilyPondPitch.java` — Dutch pitch-name ↔ `(Step, alter)` lookup (parity with `AbcKey`)
  - `LilyPondDuration.java` — numeric/dotted duration ↔ `Duration` conversion, and the
    duration-carry state (parity with `AbcNoteLength`)

Reader is a hand-written scanner/tokeniser feeding a small state machine, same shape as
`AbcReader.BodyParser` — reusing that structure keeps beam/tie/slur/tuplet bookkeeping
already-proven correct. LilyPond auto-beams eighths and shorter within the beat, so the
reader reuses `AbcReader`'s `flushBeamGroup` logic almost verbatim (extract it later if
we grow a third consumer, but copy for now to keep the change local).

Writer emits a single `\score { \new Staff { … } }` per `Part`, opening with
`\version "2.24.0"`, then `\header`, then per-staff `\time`, `\key`, `\clef`, followed by
the music expression in **absolute** pitch mode (simpler than `\relative` for a machine
producer; LilyPond happily consumes either).

The `.ly` filename extension is unambiguous, so `ScoreFile` extension dispatch stays the
same simple switch.

## Implementation Steps

### Step 1 — Core module skeleton

- `sheetmusic4j/core/src/main/java/com/sheetmusic4j/core/lilypond/LilyPondException.java`
  — copy shape from `abc/AbcException.java`.
- `sheetmusic4j/core/src/main/java/com/sheetmusic4j/core/lilypond/LilyPondPitch.java`
  — Dutch-name table `c cis ces d dis des … bis beses` ↔ `(Step, alter)`, plus reverse
  lookup used by the writer. Also a `keyString(KeySignature, mode)` mirroring
  `AbcWriter#keyString` but emitting e.g. `c \major`, `a \minor`, `g \major` (fifths →
  tonic step + accidental + mode; mode defaults to `\major` — the `KeySignature` model
  doesn't currently carry mode, so key writing is always major for now, matching how the
  ABC writer picks a canonical major representative).
- `sheetmusic4j/core/src/main/java/com/sheetmusic4j/core/lilypond/LilyPondDuration.java`
  — record `Parsed(NoteType type, int dots, int consumed)`, a `toDuration(NoteType, dots,
  int divisions)` helper, and a `writeSuffix(StringBuilder, Duration, Duration previous)`
  that emits the numeric+dots suffix, or an empty string when it matches the carried
  previous duration.

### Step 2 — LilyPondReader

- `sheetmusic4j/core/src/main/java/com/sheetmusic4j/core/lilypond/LilyPondReader.java`
  — model on `abc/AbcReader.java` (same top-level shape: `read(Path)`, `read(InputStream)`,
  `parse(String)`, inner `BodyParser` state machine). Concrete tasks:

  1. Strip comments (`% ... EOL` and `%{ ... %}` blocks) into a normalised token stream
     first — LilyPond is more free-form than ABC so this simplifies the parser.
  2. Extract `\version`, `\language`, `\header { … }` and (optionally) `\paper { … }` at
     the file level. Header keys go into `Score.workTitle` / `movementTitle` /
     `Creator("composer", …)` via the existing `Score.Builder`.
  3. Locate the first `\score { … }` (or top-level music expression) and the first
     `\new Staff { … }` / `\new Voice { … }` inside it. Feed the inner music expression
     to `BodyParser`.
  4. `BodyParser` walks tokens: recognise `\time`, `\key`, `\clef`, `\tempo`, `\bar`,
     `\tuplet` / `\times`, `\relative`, chord `< … >`, tie `~`, slur `(` `)`, articulation
     shortcuts (`-.` `->` `--`), pitch tokens, rest tokens, bar-check `|`. Track
     `Attributes` on the first measure and on any subsequent `\time`/`\key`/`\clef` change
     (setting `forceSystemBreak = true` for mid-tune changes, exactly like
     `AbcReader.BodyParser#applyMidTuneAttributesChange`).
  5. `\relative` mode: keep a "previous pitch" for closest-octave rule; each pitch letter
     picks the octave whose absolute value is nearest to the previous, then apply `'` / `,`
     as ±octave adjustments. Absolute mode: `c` = C3, `c'` = C4, `c''` = C5 (LilyPond
     convention). Encapsulate in a private helper `resolveOctave(Step baseStep, int
     octaveShift, Pitch previous, boolean relative)`.
  6. Beam grouping: after each note, decide whether to add to the current beam group or
     flush, using the same rule as `AbcReader.BodyParser#addToBeamGroup` (beam-levels
     from `NoteType`). Whitespace between notes does *not* break beams in LilyPond, but
     rests, bars, and quarter-or-longer notes do — reuse the existing helper.
  7. `\addlyrics { … }` / `\lyricmode { … }`: parse syllables (spaces separate; `--`
     mid-word; `__` extension carried onto the next skip; `_` skip). Attach to lyric
     anchors collected during the preceding music expression, mirroring
     `AbcReader.BodyParser#applyLyrics`.
  8. `\chordmode { … }` / `\chords { … }`: parse `c1:m7` style chord names into
     `Harmony` (root letter + accidental + `:` quality) using the existing
     `HarmonyKind` aliases lifted from `AbcReader.CHORD_QUALITY_ALIASES` (extract to a
     package-private constant in a follow-up if the duplication becomes annoying).

### Step 3 — LilyPondWriter

- `sheetmusic4j/core/src/main/java/com/sheetmusic4j/core/lilypond/LilyPondWriter.java`
  — model on `abc/AbcWriter.java`. Output shape per `Part`:

  ```
  \version "2.24.0"
  \header {
      title = "…"
      composer = "…"
  }
  \score {
      \new Staff {
          \time 4/4
          \key c \major
          \clef treble
          c'4 d'4 e'4 f'4 |
          g'4 a'4 b'4 c''4 |
      }
      \addlyrics { … }   % only when the part has lyrics
  }
  ```

  Key differences from `AbcWriter`:
  - Absolute pitch mode → append `'`/`,` marks based on `Pitch.octave()`. Middle C is `c'`.
  - Duration carry-over: track the last emitted duration and skip the suffix when it
    matches (`LilyPondDuration#writeSuffix`).
  - Chords: `<c' e' g'>4`.
  - Ties: `~` **after** the pitch+duration (LilyPond convention), not `-` before as in ABC.
  - Slurs: `(` after the first note, `)` after the last (mirrors ABC exactly).
  - Tuplets: `\tuplet 3/2 { c'8 d'8 e'8 }` around the tuplet run.
  - Bar lines: emit `|` at every measure boundary; use `\bar "…"` for anything other than
    a plain bar.
  - Post-tune text: emit as `%` comment lines below the score (LilyPond has no exact
    equivalent of ABC's `W:`, and this keeps the writer lossless in comments).

### Step 4 — Wire into `ScoreFile`

- `sheetmusic4j/core/src/main/java/com/sheetmusic4j/core/io/ScoreFile.java`
  1. Import `LilyPondReader` / `LilyPondWriter`.
  2. Add `LILYPOND` to the private `Format` enum.
  3. Extend `format(Path)` with `case "ly" -> Format.LILYPOND;`.
  4. Extend both `switch (format(path))` blocks in `load` and `save` with `LILYPOND` arms.
  5. Update the class-level javadoc to list `.ly` alongside `.abc`.

### Step 5 — Test fixtures from lilypond.org/examples.html

Create `sheetmusic4j/core/src/test/resources/lilypond/` with:

- `c-major-scale.ly` — hand-authored, mirrors `abc/c-major-scale.abc`. Used by
  `LilyPondReaderTest` as the deterministic sample.
- `examples/` — real-world snippets pulled from https://lilypond.org/examples.html.
  For each category page, grab the LilyPond source shown next to the rendered PNG and
  save it verbatim so we can also include the PNG later as a sibling reference (matching
  how `fxdemo/src/test/resources/xmlsamples/` pairs `.musicxml` with `.pdf`). Suggested
  starter set (one file per bullet, filenames kebab-case, ASCII only):
  - `musical-notation-simple.ly`
  - `musical-notation-piano.ly`
  - `vocal-music-lied.ly`
  - `vocal-music-choral.ly`
  - `chord-names.ly`
  - `orchestral-score.ly`
  - `complex-notation-cadenza.ly`
  - `ancient-notation.ly`
  - `real-music-bach.ly`
  - `real-music-mozart.ly`
- `examples/unsupported/` — for snippets that intentionally exercise constructs the MVP
  drops (polyphony, `\repeat volta`, ossia, …). `LilyPondExamplesTest` skips this folder
  the same way `AbcExamplesTest#examples()` filters out `unsupported` (see
  `AbcExamplesTest.java:32`).

Each `.ly` file should carry a leading comment `% Source: https://lilypond.org/examples.html`
and the licence line from LilyPond's site (GPL / GFDL — check the specific page; if in
doubt, copy the notice at the bottom of examples.html verbatim into the file's header
comment, same as `xmlsamples/README.md` credits musicxml.com).

### Step 6 — Unit and round-trip tests

Create `sheetmusic4j/core/src/test/java/com/sheetmusic4j/core/lilypond/`:

- `LilyPondReaderTest.java` — mirror `AbcReaderTest`. Cover: header block, `\version`,
  `\time`, `\key`, `\clef`, relative-mode pitches, absolute-mode pitches, dotted
  durations & duration carry-over, chords, ties, slurs, tuplets, `\bar "|:"` /
  `":|"`, `\addlyrics`, `\chordmode`, comment stripping (line + block). Include a
  `readsCMajorScaleSample()` that loads `/lilypond/c-major-scale.ly` from the classpath,
  same shape as `AbcReaderTest#readsCMajorScaleSample()`.

- `LilyPondWriterTest.java` — mirror `AbcWriterTest`. Assert the emitted text contains
  the expected `\version`, `\header { title = "…" }`, `\time`, `\key`, `\clef` and note
  tokens for a small hand-built `Score`.

- `LilyPondRoundTripTest.java` — copy `AbcRoundTripTest.java` verbatim and swap the
  reader/writer types. Same three tests: `roundTripPreservesPitchAndOrdering`,
  `roundTripPreservesWorkTitle`, and (adapted) a lyrics or tempo round-trip check
  (LilyPond has no post-tune-text equivalent, so replace with e.g. tempo preservation).

- `LilyPondExamplesTest.java` — copy `AbcExamplesTest.java`; scan
  `/lilypond/examples/**/*.ly` excluding `unsupported/`, assert each parses into a
  `Score` with at least one part and one measure.

### Step 7 — Wire the extension into `ScoreFileTest`

- `sheetmusic4j/core/src/test/java/com/sheetmusic4j/core/io/ScoreFileTest.java`
  — extend `writableExtensionRoundTripsThroughFacade`'s `@ValueSource` list with
  `"ly"` so the facade dispatch is exercised for LilyPond too.

### Step 8 — Documentation

- `sheetmusic4j/README.md`
  - "Core" bullet list: add "Importing and exporting LilyPond files (see
    https://lilypond.org/)".
  - `ScoreFile` extension list: add `.ly`.
- `sheetmusic4j/CLAUDE.md`
  - Module 1 (`core`) paragraph: add `com.sheetmusic4j.core.lilypond` to the list of
    subpackages and `.ly` to the `ScoreFile` extension dispatch summary.

## Reference Examples

- `sheetmusic4j/core/src/main/java/com/sheetmusic4j/core/abc/AbcReader.java` — canonical
  hand-written scanner + `BodyParser` state machine. Reuse the beam-flushing, tie/slur,
  and tuplet bookkeeping patterns verbatim.
- `sheetmusic4j/core/src/main/java/com/sheetmusic4j/core/abc/AbcWriter.java` — canonical
  writer shape (per-`Part` header + body, `keyString` helper, duration suffix emitter).
- `sheetmusic4j/core/src/main/java/com/sheetmusic4j/core/abc/AbcNoteLength.java` — model
  for `LilyPondDuration` (parsed fraction + consumed-char count).
- `sheetmusic4j/core/src/main/java/com/sheetmusic4j/core/abc/AbcKey.java` — model for
  `LilyPondPitch` (key-signature → step alteration table).
- `sheetmusic4j/core/src/main/java/com/sheetmusic4j/core/io/ScoreFile.java:23-63` —
  extension dispatch pattern to extend.
- `sheetmusic4j/core/src/test/java/com/sheetmusic4j/core/abc/AbcExamplesTest.java` —
  classpath-scanning `@MethodSource` pattern for the `examples/` directory.
- `sheetmusic4j/core/src/test/java/com/sheetmusic4j/core/io/ScoreFileTest.java:35-50` —
  `@ValueSource` extension list to extend with `"ly"`.

## Verification

```bash
# From the repo root:
mvn -pl core test                                            # runs the whole core suite incl. new LilyPond tests
mvn -pl core -Dtest=LilyPondReaderTest test
mvn -pl core -Dtest=LilyPondWriterTest test
mvn -pl core -Dtest=LilyPondRoundTripTest test
mvn -pl core -Dtest=LilyPondExamplesTest test
mvn -pl core -Dtest=ScoreFileTest test                       # verifies the .ly facade wiring
mvn clean install                                            # full multi-module build stays green
```

Manual smoke test with the demo app:

```bash
mvn -pl fxdemo javafx:run
# then File → Open a .ly file from core/src/test/resources/lilypond/examples/
```

Acceptance criteria mapped back to the issue:

- [x] Reader accepts `.ly` files pulled from lilypond.org/examples.html
- [x] Writer emits `.ly` output that LilyPond itself can render (compile with
      `lilypond examples/xxx.ly` locally to eyeball the PDF — not automated in CI since
      `lilypond` isn't a build dependency, mirroring the ABC integration's stance)
- [x] `.ly` wired into `ScoreFile` extension dispatch
- [x] Test fixtures live under `core/src/test/resources/lilypond/` matching the ABC layout
- [x] README / CLAUDE.md updated
