id: 0366b81f-377c-423c-bff3-a24b3f4dea93
sessionId: e96886f4-1b2f-4bdd-a6f0-190b762b8356
date: '2026-07-21T13:00:01.942Z'
label: 'Task 5: Chord symbols'
---
# Task 5: Chord symbols

## Goal
Read MusicXML `<harmony>` elements (a *sibling* of `<direction>` at
the measure level) into the model as a new `Harmony` `MusicElement`,
render each as a text label above the staff at the beat where it
appears (e.g. `BMaj7`, `D♭m9`, `G7sus4/D`), and expose visibility
under `MarkingCategory.CHORD_SYMBOL`.

**Depends on Task 3** — reuses the shared `MarkingCategory` filter,
the "above the staff" placement slot, the two-pass measure walk that
anchors non-note elements to the next note's x, and the
`Duration.ZERO` constant. Rehearsal marks (Task 4) are not required
but the two tasks share the boxed-text follow-ups.

## Design

### JavaDoc

All classes and methods must have valid JavaDoc.

### runTask

Use runTask with caution is this blocks regularly. Don't wait longer than a minute and terminate it if it takes longer.

### 5.1 — `<harmony>` as its own `MusicElement`, not a `DirectionType`
Chosen over folding it under `Direction` because:
- MusicXML places `<harmony>` at the same nesting level as
  `<note>`/`<direction>`, not inside `<direction-type>`.
- Its data shape (root + kind + optional bass) is structurally
  different from any `DirectionType`. Reusing `Direction` would force
  a "chord symbol" variant on `DirectionType` that ignores placement.
- `Harmony` staying a top-level element in `Measure.elements()` gives
  us the same document-order semantics we get for `Direction`.

`MusicElement`'s sealed permits list grows again:
```java
sealed interface MusicElement permits Note, Rest, Chord, Direction, Harmony
```
`Harmony.duration()` returns `Duration.ZERO` (added in Task 3).

### 5.2 — Model shape

```java
public record Harmony(
    Root root,
    HarmonyKind kind,
    Optional<Bass> bass,
    Optional<String> textOverride) implements MusicElement
```

- `Root(Step step, int alter)` — mirrors `Pitch` but with no octave,
  since chord symbols are pitch-class-only.
- `Bass(Step step, int alter)` — same shape as `Root`; present only
  for slash chords (`G/B`).
- `HarmonyKind` is an enum with MVP values covering the common cases
  seen in `BrookeWestSample.musicxml`:
  `MAJOR, MINOR, DOMINANT, DIMINISHED, AUGMENTED,
   MAJOR_SEVENTH, MINOR_SEVENTH, DOMINANT_SEVENTH,
   HALF_DIMINISHED, DIMINISHED_SEVENTH,
   MAJOR_SIXTH, MINOR_SIXTH,
   MAJOR_NINTH, MINOR_NINTH, DOMINANT_NINTH,
   SUSPENDED_FOURTH, SUSPENDED_SECOND,
   POWER, NONE, OTHER`
  Each carries an `xmlValue()` (the hyphenated MusicXML spelling) and
  a `shortLabel()` (`""` for major, `"m"` for minor, `"7"` for
  dominant, `"maj7"`, `"m7"`, `"7"`, `"ø"`, `"°"`, `"sus4"` …). A
  static `HarmonyKind.fromXml(String)` handles the reverse mapping
  with a lenient fallback to `OTHER`.
- `textOverride` captures MusicXML's `<kind text="Maj7">` attribute:
  when present it wins over `shortLabel()` in rendering (real scores
  frequently set idiosyncratic shorthands — see `BrookeWestSample`).

### 5.3 — Rendering label composition

`String Harmony.displayLabel()` computes the printable string:
1. Root: `step + accidental` (using `♯`/`♭`/`𝄪`/`𝄫` for alter
   ±1/±2; plain letter for alter 0).
2. Kind: `textOverride.orElse(kind.shortLabel())`.
3. Slash bass: when present, append `"/" + bass.step + bassAccidental`.

Examples:
- `Root(B, 0) + MAJOR_SEVENTH` (text override `"Maj7"`) →
  `"BMaj7"`.
- `Root(D, 1) + MINOR_NINTH` → `"D♯m9"`.
- `Root(G, 0) + DOMINANT_SEVENTH + Bass(D, 0)` → `"G7/D"`.

Ligatures / superscripts are ignored for MVP — plain concatenation.

### 5.4 — Reader

`MusicXmlReader.readMeasure`: new case:
```java
case "harmony" -> {
    Harmony harmony = readHarmony(reader);
    if (harmony != null) {
        flushChord(measure, pendingChord);
        measure.addElement(harmony);
    }
}
```

`readHarmony(reader)`:
- Loop over children until `END_ELEMENT("harmony")`:
  - `<root>` → nested loop for `<root-step>` (mandatory, `Step.valueOf`)
    and `<root-alter>` (optional, default 0). Build `Root`.
  - `<kind>` → attribute `text` captured as `textOverride`; body text
    passed through `HarmonyKind.fromXml(...)`.
  - `<bass>` → nested loop for `<bass-step>` and `<bass-alter>`. Build
    `Bass`.
  - `<degree>` and `<function>` → ignored for MVP (both are
    modifiers; captured in a follow-up).
- Return `null` when neither root nor kind was captured (defensive).

### 5.5 — Writer

`MusicXmlWriter.writeMeasure`: add a `Harmony` branch that emits:
```xml
<harmony>
  <root>
    <root-step>B</root-step>
    <root-alter>1</root-alter>       <!-- omitted when alter == 0 -->
  </root>
  <kind text="Maj7">major-seventh</kind>
  <bass>                              <!-- omitted when absent -->
    <bass-step>D</bass-step>
    <bass-alter>1</bass-alter>
  </bass>
</harmony>
```
Ordering must match MusicXML DTD: `<root>` → `<kind>` → `<bass>`.

### 5.6 — Engraver placement

Chord symbols anchor to the *next* note's x (same two-pass anchoring
Task 3 introduced for `Direction`). Since MusicXML forbids `<harmony>`
after the last note in a measure, we don't need the "last-note
fallback" branch, but we keep it for safety.

- New Engraver switch branch inside the second pass:
  ```java
  case Harmony harmony -> placeHarmony(harmony, x, staffY, ...);
  ```
- `placeHarmony` emits:
  ```java
  new TextPlacement(harmony.displayLabel(),
          x, staffY - gap * 3.5,
          gap * 1.6,
          Align.LEFT,
          MarkingCategory.CHORD_SYMBOL,
          /*boxed*/ false)
  ```
  The `y` sits one row *higher* than words directions so chord
  symbols and tempo/text markings on the same measure don't overlap
  (chord symbols by convention sit closest to the staff for
  performers; publishers vary — this is a reasonable default).
- `PartInfo` gains `boolean hasHarmony`; when true, the same
  above-staff reserve mechanism from Task 3 kicks in. If both
  `hasDirectionsAbove` and `hasHarmony` are set, reserve
  `gap * 5` (one row per marking category) instead of `gap * 3`.

### 5.7 — Viewer

- `SheetDemoApp.buildMenuBar`: add `CheckMenuItem` "Show chord
  symbols" bound to `MarkingCategory.CHORD_SYMBOL`.

## Implementation Steps

### Step 1 — Model
- `com.sheetmusic4j.core.model.HarmonyKind` (new) — enum per §5.2 with
  `xmlValue()` and `shortLabel()` accessors and a static
  `fromXml(String)`.
- `com.sheetmusic4j.core.model.Harmony` (new) — record per §5.2
  implementing `MusicElement`. `duration()` returns `Duration.ZERO`.
  Include the `displayLabel()` helper per §5.3.
- `com.sheetmusic4j.core.model.MusicElement` — widen the sealed
  permits list to include `Harmony`.

### Step 2 — Reader
- `MusicXmlReader.readMeasure`: add `case "harmony"` calling
  `readHarmony(reader)`.
- Add `readHarmony`, `readRoot`, `readBass`, `readKind` helpers per
  §5.4. `readKind` returns a small struct capturing both
  `HarmonyKind` and `textOverride`.

### Step 3 — Writer
- `MusicXmlWriter.writeMeasure`: add a `Harmony` branch calling
  `writeHarmony`.
- `writeHarmony` emits per §5.5 using `IndentingWriter`. Preserve the
  `<root>` → `<kind>` → `<bass>` order.

### Step 4 — MIDI exporter
- `MidiExporter` element switch: no-op `Harmony` (chord symbols have
  no MIDI representation on their own).

### Step 5 — Engraver
- `Engraver.PartInfo.of`: compute `hasHarmony` alongside
  `hasDirectionsAbove` and `hasLyrics`.
- Row above-reserve logic: bump reserve to `gap * 5` when both above
  categories are present in the row.
- `Engraver.layoutStaffRow` second pass: add
  `case Harmony harmony -> placeHarmony(...)`.
- `Engraver.placeHarmony`: emit the `TextPlacement` per §5.6.

### Step 6 — Demo
- `SheetDemoApp`: "Show chord symbols" toggle.

### Step 7 — Tests

**Core (reader + writer)**
- `MusicXmlReaderTest.readsMajorSeventhHarmony` — inline XML with
  `<harmony><root><root-step>B</root-step></root>
  <kind text="Maj7">major-seventh</kind></harmony>`. Assert measure
  carries a `Harmony(Root(B, 0), MAJOR_SEVENTH, empty,
  Optional.of("Maj7"))`.
- `readsSlashChordHarmony` — root + kind + `<bass>` with
  `bass-alter`. Assert bass populated correctly.
- `readsHarmonyWithoutKindText` — asserts `textOverride.isEmpty()`
  and `displayLabel()` falls back to `shortLabel()`.
- `unknownHarmonyKindMapsToOther` — value not in the enum → `OTHER`.
- `MusicXmlWriterTest.roundTripPreservesHarmony` — build a Score with
  three chord symbols (major, minor-ninth, slash), round-trip, assert
  the reparsed harmonies match. Extend `assertSameElement`.

**Model**
- `HarmonyTest`:
  - `displayLabelPrefersTextOverride` — `Maj7` beats `maj7`.
  - `displayLabelUsesUnicodeAccidentals` — `Root(D, 1)` → starts with
    `"D♯"`, `Root(E, -1)` → `"E♭"`.
  - `displayLabelIncludesSlashBass` — `G7/D` case.

**Engraving**
- New `EngraverHarmonyTest.java`:
  - `harmonyEmitsTextAboveStaff` — assert one `TextPlacement`,
    `category = CHORD_SYMBOL`, y sits above staff.
  - `harmonyAnchorsToNextNoteX` — Harmony followed by a Note; assert
    the label x equals the note's x.
  - `harmonyBoostsRowAboveReserve` — layout with vs. without harmony;
    assert row y increases.
  - `harmonyAndDirectionAboveStack` — Score with both; assert their
    y values differ (chord symbol closer to staff by design).

**Viewer**
- `ScorePainterTextVisibilityTest.hidingChordSymbolCategorySkipsLabel`.

**Real-world sanity**
- Optional: read `xmlsamples/BrookeWestSample.musicxml`; assert
  first `Harmony` is `B` + `MAJOR_SEVENTH` + bass `D♯` +
  textOverride `"Maj7"`. Guarded by resource availability.

## Reference Examples
- `xmlsamples/BrookeWestSample.musicxml:1186-1194` — canonical
  `<harmony>` example, exercises root, kind text-override, and slash
  bass.
- `xmlsamples/BrookeWestSample.musicxml:2092` — `m9` (minor-ninth)
  variant.
- `xmlsamples/BrookeWestSample.musicxml:2975` — `7sus4` variant.
- Task 3's `readDirection` — parallel StAX pattern for `readHarmony`.
- Task 3's `placeDirection` — parallel placement pattern for
  `placeHarmony`.

## Verification
1. `cd sheetmusic4j && ./gradlew :core:test :engraving:test :fxviewer:test`.
2. Manual smoke: `SheetDemoApp` with
   `xmlsamples/BrookeWestSample.musicxml`. Expect `BMaj7/D♯`, `Cm9`,
   `G7sus4`, `A` (etc.) above the staff at the correct beats;
   View → Text → uncheck "Show chord symbols" hides them all.
3. Regression: any score without `<harmony>` renders identically to
   before Task 5 landed.

## Deferred / Follow-ups
- **`<degree>` modifications** (e.g. `add9`, `no3`, `alt`). Reader
  currently ignores them; store as `List<Degree>` when needed.
- **`<function>` roman-numeral analysis** (`I`, `V6/4`, …). Rare but
  present in analytical scores.
- **Superscript rendering** — real chord-symbol engraving raises
  extension numbers and quality glyphs (`ø` for half-diminished,
  triangle for major). MVP concatenates plain text.
- **Font.** Chord symbols traditionally use a slightly larger, non-
  music serif or sans-serif font; MVP reuses the default text font.
- **Alignment / kerning.** MVP left-aligns to the note x; publishers
  often center or shift so the accidental clears the notehead.
- **Guitar frames / fret diagrams.** Some `<harmony>` elements carry
  a `<frame>` diagram. Out of scope.
- **Chord symbols in MIDI.** Optional velocity/harmony-track output
  for accompaniment generation.
