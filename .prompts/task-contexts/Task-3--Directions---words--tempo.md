id: 72240e1d-1ed4-4a33-9f64-dba7daa8c2e0
sessionId: e96886f4-1b2f-4bdd-a6f0-190b762b8356
date: '2026-07-21T12:55:25.383Z'
label: 'Task 3: Directions — words, tempo, dynamics'
---
# Task 3: Directions — words, tempo, dynamics

## Goal
Read MusicXML `<direction>` elements (with `<words>`, `<metronome>`, and
`<dynamics>` children) into the model as a new `Direction` `MusicElement`,
place them at the x of the associated note, above or below the staff per
the `placement` attribute, and expose visibility toggles per marking
category. Wedges/hairpins, sound-only directions, and coda/segno symbols
are deferred.

**Depends on Task 1** (introduces the visibility category enum) and
**Task 2** (introduces the below-staff vertical reserve pattern).

## Design decisions (defaults — please override if you disagree)

### 3.3 — `Direction` as an ordered `MusicElement` inside `Measure.elements()`
Chosen over a sibling `Map<beat, Direction>` collection because:
- It faithfully preserves the document order MusicXML emits.
- It requires no additional beat-offset bookkeeping in the reader.
- The `sealed permits` list on `MusicElement` grows by one type; every
  existing consumer (writer, engraver, MIDI exporter, tests) sees a
  compile error until it's handled — a good forcing function.
- `Direction.duration()` returns `Duration.ZERO` (new static constant on
  `Duration`) so `measureWeight` and MIDI timing stay unaffected.

### 3.4 — Dynamics as `GlyphPlacement` with new SMuFL dynamic `Glyph`s
Chosen over a new `DynamicPlacement` because:
- The rendering pipeline for a dynamic glyph is identical to a notehead
  or rest (SMuFL codepoint via `drawSmuflGlyph`, primitive fallback).
- Sharing `GlyphPlacement` means one visibility filter (see below)
  and one Bravura mapping (`SmuflGlyphs.codepoint`).
- The follow-up hairpin/wedge task will legitimately need a new
  `WedgePlacement` (start x/y → end x/y like `TiePlacement`), so we
  don't preemptively pay that cost here.

### Visibility categorization — unify `TextPlacement.Category` and glyph categorization
Task 1 introduced `TextPlacement.Category`. This task promotes it into
a shared `MarkingCategory` enum (in `com.sheetmusic4j.engraving`) with
values:
`NOTE, TITLE, SUBTITLE, CREATOR, LYRIC, DIRECTION, TEMPO, DYNAMIC` —
plus the reserved slots `REHEARSAL, CHORD_SYMBOL` for Tasks 4/5.
- `TextPlacement.category` becomes `MarkingCategory`.
- `GlyphPlacement` grows a `category` component with a compatibility
  constructor defaulting to `MarkingCategory.NOTE` (records support
  compact + secondary constructors — existing call sites don't change).
- `ScorePainter.paint` filters both `TextPlacement` and `GlyphPlacement`
  by the shared `hiddenCategories` set. Since existing glyphs default
  to `NOTE` (never hidden by category), current behaviour is preserved.

## Model

### `Direction`
- New sealed sub-type of `MusicElement`:
  ```java
  public sealed interface MusicElement permits Note, Rest, Chord, Direction
  ```
- Record `Direction(DirectionType type, Placement placement)` where:
  - `Placement` is `Enum { ABOVE, BELOW, DEFAULT }` — DEFAULT means
    "the engraver picks a sensible side" (words → above, dynamics →
    below by convention).
  - `DirectionType` is a sealed interface with three permitted records:
    - `Words(String text, boolean italic, boolean bold)` — from
      `<words>`. `italic`/`bold` derived from `font-style="italic"` /
      `font-weight="bold"` when present.
    - `Metronome(NoteType beatUnit, boolean dotted, int perMinute)` —
      from `<metronome><beat-unit>quarter</beat-unit><per-minute>60</per-minute></metronome>`.
    - `Dynamic(DynamicMark mark)` — from `<dynamics><f/></dynamics>`.
  - `DynamicMark` is a new enum in `com.sheetmusic4j.core.model` with
    values matching the SMuFL set we cover:
    `PPP, PP, P, MP, MF, F, FF, FFF, SF, SFZ, FZ, FP, RF, RFZ, N`
    (n = niente). Others in the XML but not in the enum are logged and
    dropped (or, if easier, mapped to a best-effort neighbour — noted
    in the follow-up).
- `Direction.duration()` returns `Duration.ZERO`.

### `Duration.ZERO`
- Static constant `public static final Duration ZERO = new Duration(0, 1);`
  on `Duration`, plus a `boolean isZero()` predicate. `Duration(0, 1)` is
  already valid (see `Duration.value >= 0`).
- `Duration.inQuarters()` on ZERO returns 0.0 — consumed by
  `measureWeight` which already skips zero-duration elements via
  `sum > 0 ? sum : 1.0` fallback.

## Reader

`MusicXmlReader.readMeasure` — new case:
```java
case "direction" -> {
    Direction dir = readDirection(reader);
    if (dir != null) {
        flushChord(measure, pendingChord);
        measure.addElement(dir);
    }
}
```

New `readDirection` helper:
- Read `placement` attribute → `ABOVE`/`BELOW`/`DEFAULT`.
- Loop until `END_ELEMENT("direction")`:
  - `<direction-type>` — enter, loop until `END_ELEMENT("direction-type")`:
    - `<words>` → capture text + `font-style`/`font-weight` attributes;
      build `Words`. Multiple `<words>` (unusual) concatenate.
    - `<metronome>` → nested loop reads `<beat-unit>`, optional
      `<beat-unit-dot/>`, `<per-minute>`. Build `Metronome`.
    - `<dynamics>` → nested loop; first recognized child element name
      (`p`, `f`, `mf`, `pp`, …) maps to `DynamicMark`. Build `Dynamic`.
    - Everything else (`<wedge>`, `<rehearsal>`, `<segno>`, `<coda>`,
      `<octave-shift>`, …) skipped and logged.
  - `<sound>` and `<offset>` skipped for MVP.
- Return `null` when no `DirectionType` was recognized; the reader
  drops it silently so unfamiliar directions don't inject phantom
  elements into the measure.

## Writer

`MusicXmlWriter.writeMeasure` — add a branch for `Direction`:
```java
else if (element instanceof Direction dir) {
    writeDirection(w, dir);
}
```

`writeDirection`:
```xml
<direction placement="above">
  <direction-type>
    <words font-weight="bold" font-style="italic">Andantino</words>
    <!-- or -->
    <metronome><beat-unit>quarter</beat-unit><per-minute>60</per-minute></metronome>
    <!-- or -->
    <dynamics><f/></dynamics>
  </direction-type>
</direction>
```
- `placement="default"` written as no `placement` attribute (canonical).

## Engraver

### `MarkingCategory` + `GlyphPlacement.category`
- Move `TextPlacement.Category` (from Task 1) to a top-level
  `MarkingCategory` enum. `TextPlacement` gets `MarkingCategory category`.
- `GlyphPlacement` gains a fifth component `MarkingCategory category`;
  a secondary constructor `GlyphPlacement(double, double, Glyph, int)`
  delegates to `(…, MarkingCategory.NOTE)` so existing call sites
  (thousands of them) compile unchanged.

### `Glyph` additions
Add dynamic glyphs to the `Glyph` enum:
`DYNAMIC_P, DYNAMIC_PP, DYNAMIC_PPP, DYNAMIC_MP, DYNAMIC_MF, DYNAMIC_F,
DYNAMIC_FF, DYNAMIC_FFF, DYNAMIC_SF, DYNAMIC_SFZ, DYNAMIC_FZ,
DYNAMIC_FP, DYNAMIC_RF, DYNAMIC_RFZ, DYNAMIC_NIENTE`.

`SmuflGlyphs.codepoint` extended:
- `DYNAMIC_P → U+E520`, `DYNAMIC_MEZZO=E521`, `DYNAMIC_F → U+E522`,
  `DYNAMIC_R → U+E523`, `DYNAMIC_S → U+E524`, `DYNAMIC_Z → U+E525`,
  `DYNAMIC_NIENTE → U+E526`,
  `DYNAMIC_PPP → U+E52A`, `DYNAMIC_PP → U+E52B`,
  `DYNAMIC_MP → U+E52C`, `DYNAMIC_MF → U+E52D`,
  `DYNAMIC_FF → U+E52F`, `DYNAMIC_FFF → U+E530`,
  `DYNAMIC_FP → U+E534`, `DYNAMIC_FZ → U+E535`,
  `DYNAMIC_SF → U+E536`, `DYNAMIC_SFZ → U+E539`,
  `DYNAMIC_RF → U+E53C`, `DYNAMIC_RFZ → U+E53D`.
- `halfAdvanceWidth` gets sensible defaults for the new codepoints
  (~1.5 staff spaces for single letters, scaling linearly with letter
  count for compound marks).

### Placement rules

When rendering a measure's elements:
1. **Pre-pass** — compute the noteX for each Note/Chord/Rest exactly
   as today, plus a parallel list mapping element-index → noteX. For
   Direction elements, associate them with the *next* Note/Chord/Rest
   (fall back to the *previous* if the Direction is the last element).
2. **Emission** — for each Direction, emit at its associated x:
   - `Placement.ABOVE` (or `DEFAULT` for `Words`/`Metronome`):
     `y = staffY - gap * 1.5 - itemHeight`
   - `Placement.BELOW` (or `DEFAULT` for `Dynamic`):
     `y = staffY + staffHeight + gap * 1.5 + itemHeight`
   - `Words`: `TextPlacement(text, x, y, gap * 1.6, Align.LEFT,
     Category.DIRECTION)` — italic/bold flags stashed in the text (we
     don't yet plumb style attributes through `TextPlacement`; a
     follow-up adds them).
   - `Metronome`: rendered as a single string `"♩ = 60"` (`♩` = U+2669,
     or the beat-unit-appropriate note character) via `TextPlacement`
     with `Category.TEMPO`. A properly engraved metronome (SMuFL note
     glyph + `=` + digit glyphs) is a follow-up.
   - `Dynamic`: `GlyphPlacement(x, y, DYNAMIC_MARK, staffStep,
     Category.DYNAMIC)` where `DYNAMIC_MARK` is the SMuFL glyph
     picked by `dynamicGlyph(DynamicMark)`.

### Space reservation

- Extend `PartInfo` with three additional booleans built during
  `PartInfo.of`:
  - `hasDirectionsAbove` — any `Direction` with resolved placement
    `ABOVE` (i.e. explicit ABOVE, or DEFAULT for `Words`/`Metronome`).
  - `hasDirectionsBelow` — any Direction resolved to `BELOW`.
- Reserve extra vertical space:
  - Above: bump `y` at the top of each *system* by `gap * 3` when any
    part in that row has above-directions. Handled inside the row loop
    in `Engraver.layout`.
  - Below: after the last staff of a part with `hasDirectionsBelow` or
    `hasLyrics` (Task 2), advance `staffTop` by `max(lyricReserve,
    directionReserve)`. Directions below and lyrics can coexist —
    documented ordering: lyrics closer to staff, dynamics further. But
    for MVP they share the same slot at `gap * 1.5`; overlaps for a
    lyric-bearing vocal part are noted as a known limitation.

## Viewer

Task 1's plumbing on `ScorePainter` / `SheetView` already carries
arbitrary categories. Extension only:
- `ScorePainter.paint`: also filter `staff.glyphs()` by the hidden
  categories set (skip `GlyphPlacement`s whose category is hidden).
  Since existing glyphs are `NOTE`, no behaviour change for pre-Task-3
  scores.
- `SheetDemoApp`: add `CheckMenuItem`s to the View → Text submenu:
  "Show tempo", "Show directions", "Show dynamics". All ticked by
  default.

## Implementation Steps

### Step 1 — Model additions
- `sheetmusic4j/core/src/main/java/com/sheetmusic4j/core/model/Duration.java`
  - Add `public static final Duration ZERO = new Duration(0, 1);`
  - Add `public boolean isZero() { return value == 0; }`
- `sheetmusic4j/core/src/main/java/com/sheetmusic4j/core/model/MusicElement.java`
  - Widen `permits` to include `Direction`.
- `sheetmusic4j/core/src/main/java/com/sheetmusic4j/core/model/DynamicMark.java`
  (new) — enum with `xmlValue()` (lowercase) and `fromXml(String)`.
- `sheetmusic4j/core/src/main/java/com/sheetmusic4j/core/model/Placement.java`
  (new) — enum `ABOVE, BELOW, DEFAULT` with `fromXml(String)`.
- `sheetmusic4j/core/src/main/java/com/sheetmusic4j/core/model/DirectionType.java`
  (new) — sealed interface with permitted records `Words`, `Metronome`,
  `Dynamic` (each in the same file or in three sibling files).
- `sheetmusic4j/core/src/main/java/com/sheetmusic4j/core/model/Direction.java`
  (new) — record implementing `MusicElement`; `duration()` returns
  `Duration.ZERO`.

### Step 2 — Reader
- `MusicXmlReader.readMeasure`: add `case "direction"` calling a new
  `readDirection(reader)` helper.
- `readDirection`: parse per the design section above. Uses the
  existing StAX loop pattern (see `readAttributes`).
- Add `readMetronome`, `readDynamics`, `readWords` helpers.

### Step 3 — Writer
- `MusicXmlWriter.writeMeasure` — add the `Direction` branch.
- `writeDirection` per the design section. Uses `IndentingWriter`
  helpers for the nested XML.

### Step 4 — MIDI exporter safety
- `sheetmusic4j/core/src/main/java/com/sheetmusic4j/core/midi/MidiExporter.java`
  - Adding `Direction` to the sealed permits list will surface as an
    unhandled type in the exporter's element switch. Handle it as
    "no-op" (Direction has zero duration and no MIDI event in this
    task; wedges/dynamics-to-velocity conversion is a follow-up).

### Step 5 — Engraving core: MarkingCategory + glyph category
- `sheetmusic4j/engraving/src/main/java/com/sheetmusic4j/engraving/MarkingCategory.java`
  (new) — enum with the full value set from the "Visibility
  categorization" section.
- `TextPlacement`: replace inner `Category` with import of
  `MarkingCategory`. Retain a deprecated inner `Category` type-alias
  if it eases the Task 1 → Task 3 transition (or, if Task 1 lands
  first, mechanically rename it — small enough diff).
- `GlyphPlacement`: add `MarkingCategory category` component with the
  backwards-compat 4-arg constructor.

### Step 6 — Engraver
- `Engraver.PartInfo.of`: compute `hasDirectionsAbove` /
  `hasDirectionsBelow` alongside the existing `hasLyrics`.
- `Engraver.layout` row loop: add `gap * 3` above-reserve when any
  part in the row has above-directions.
- `Engraver.layoutStaffRow`:
  - Two-pass over `elements`:
    - Pass 1: compute noteX for Note/Chord/Rest exactly as today;
      also remember, per Direction, the associated x (next-note-x or
      last-note-x).
    - Pass 2: emit glyphs / beams / ties for notes as today, then
      emit direction placements.
  - Delegate to `placeDirection(direction, x, staffY, options, glyphs,
    texts)` per Direction; that helper builds the correct
    TextPlacement or GlyphPlacement.
- `dynamicGlyph(DynamicMark)`: static mapping from mark → Glyph.

### Step 7 — SMuFL mapping
- `SmuflGlyphs.codepoint` — add cases for all new `DYNAMIC_*` glyphs.
- `SmuflGlyphs.halfAdvanceWidth` — add sensible advance widths (start
  with `1.5 * staffSpaces` for single-letter marks; refine later).

### Step 8 — Painter visibility filter for glyphs
- `ScorePainter.paint` inner loop that walks `staff.glyphs()`:
  skip glyphs whose `category()` is in `hiddenCategories`. Add a test
  to prove pre-Task-3 glyphs (category `NOTE`) are unaffected.

### Step 9 — Demo menu
- `SheetDemoApp.buildMenuBar`: add three `CheckMenuItem`s bound to
  `MarkingCategory.TEMPO`, `MarkingCategory.DIRECTION`,
  `MarkingCategory.DYNAMIC` in `sheetView.hiddenTextCategoriesProperty()`.

### Step 10 — Tests

**Core (reader + writer)**
- `MusicXmlReaderTest`
  - `readsWordsDirection` — inline XML with `<direction placement="above">
    <direction-type><words font-weight="bold">Andantino</words>
    </direction-type></direction>` before a note. Assert the first
    element of the measure is a `Direction` with `Placement.ABOVE`,
    `Words("Andantino", false, true)`.
  - `readsMetronomeDirection` — quarter = 60. Assert
    `Metronome(NoteType.QUARTER, false, 60)`.
  - `readsDynamicsDirection` — `<dynamics><p/></dynamics>` →
    `Dynamic(DynamicMark.P)`.
  - `unknownDirectionTypeIgnored` — `<direction><direction-type>
    <wedge type="crescendo"/></direction-type></direction>` → no
    Direction added to the measure (returns null).
- `MusicXmlWriterTest.roundTripPreservesDirections` — build a Score
  with a Words, Metronome, and Dynamic direction; round-trip; assert
  types + placements survive. Extend `assertSameElement` to compare
  Directions.

**Engraving**
- New `EngraverDirectionsTest.java`:
  - `wordsDirectionEmitsTextAboveStaff` — Score with an ABOVE Words
    direction; assert a `TextPlacement` with `Category.DIRECTION`,
    `y < staffY`, x equal to the associated note's x.
  - `metronomeDirectionEmitsTempoText` — Words `"♩ = 60"` (or the
    equivalent unicode), `Category.TEMPO`, above staff.
  - `dynamicDirectionEmitsGlyphBelowStaff` — Score with a BELOW
    Dynamic(F); assert a `GlyphPlacement` with glyph `DYNAMIC_F`,
    category `DYNAMIC`, `y > staffY + staffHeight`.
  - `defaultPlacementForWordsIsAbove` and
    `defaultPlacementForDynamicsIsBelow` — assert the sensible
    default when `<direction>` has no `placement` attribute.
  - `directionAttachesToNextNoteX` — Direction immediately before
    Note1, then Note2; assert direction's x equals Note1's x.
  - `directionAtEndOfMeasureAttachesToLastNoteX` — Direction as the
    last element.
  - `directionsAbovePushStavesDown` — layout with vs. without an
    above-direction; assert the first staff `y` moves.
- Extend `EngraverTextBlockTest` if the title y baseline changes.

**Viewer**
- Extend `ScorePainterTextVisibilityTest` (from Task 1/2):
  - `hidingDynamicCategorySkipsDynamicGlyph` — build a LayoutResult
    with one Note glyph (category NOTE) and one dynamic
    GlyphPlacement (category DYNAMIC), paint with hidden={DYNAMIC},
    assert the note was still drawn and the dynamic was not.
  - `hidingDirectionCategorySkipsDirectionText`
  - `hidingTempoCategorySkipsTempoText`

**Round-trip via a real sample**
- Optional: read `xmlsamples/FaurReveSample.musicxml` (contains
  `Andantino`, `dolce`, dynamics), assert `parts().get(0).measures()`
  contains at least one Words direction with the expected text.
  Guarded behind `Assumptions.assumeTrue(resource != null)` for
  classpath portability.

## Reference Examples
- `MusicXmlReader.readAttributes` (`MusicXmlReader.java:230`) — StAX
  nested-loop pattern for `readDirection`.
- `MusicXmlReader.readNote` for the multi-child accumulator idiom
  (used by `readMetronome` and `readDynamics`).
- `MusicXmlWriter.writeAttributes` — pattern for the nested
  `<direction-type>` block.
- `Engraver.placeNote` — the "emit glyph at x with a staffStep for
  ledger-line hinting" pattern that the dynamic placement mirrors
  (dynamics use `staffStep = 8` or so, below the bottom line, so the
  ledger-line loop skips them).
- `Engraver.PartInfo.of` — pattern for precomputing per-part booleans
  during the initial part scan.
- `xmlsamples/FaurReveSample.musicxml` — dense real-world example of
  all three direction sub-types.
- `xmlsamples/MozartTrio.musicxml` — dynamics-heavy sample for the
  BELOW placement path.

## Verification
1. `cd sheetmusic4j && ./gradlew :core:test :engraving:test :fxviewer:test :fxdemo:test`
   — all suites pass, including the new tests.
2. Existing sealed-permits usages (writer switch, MIDI exporter switch)
   now handle `Direction` — a full compile confirms no missed sites.
3. Round-trip: `MusicXmlWriterTest.roundTripPreservesStructure` still
   passes for `c-major-scale.musicxml`.
4. Manual smoke: `SheetDemoApp` with `FaurReveSample.musicxml`.
   Expect:
   - "Andantino" bold above measure 1.
   - "dolce" italic above measure 2.
   - View → Text → uncheck "Show directions" hides the above two.
5. Manual smoke: `SheetDemoApp` with `MozartTrio.musicxml`. Expect
   `p` dynamic glyph rendered below the staff at the correct beat;
   toggle "Show dynamics" off and it disappears.
6. Manual smoke: any score without directions renders identically to
   before Task 3 (regression check for the category refactor).

## Deferred / Follow-ups
- **Hairpins (wedges).** `<wedge type="crescendo|diminuendo|stop">`
  becomes a new `WedgeSpan` with a `WedgePlacement` in the layout
  (start x, end x, y, direction). Requires cross-measure state à la
  ties/beams.
- **Italic / bold text styling.** `TextPlacement` currently has no
  style flag; either add `TextStyle(italic, bold, weight)` or a
  bit-flag component. Words directions currently ignore their style.
- **Rehearsal marks.** Task 4 — cheap on top of this task
  (`<rehearsal>` under `<direction-type>` → new category
  `MarkingCategory.REHEARSAL`, boxed text).
- **Chord symbols.** Task 5 — MusicXML `<harmony>` element, sibling
  of `<direction>` at the measure level; rendered as text above the
  staff. Category `CHORD_SYMBOL` already reserved.
- **Segno, coda, dal-segno, octave-shift.** Emit each as its own
  `MarkingCategory` (or fold into `DIRECTION`) in a later pass.
- **Properly engraved metronome mark.** SMuFL note glyphs + digit
  glyphs instead of the text hack.
- **Dynamics-to-velocity in MIDI exporter.** Currently ignored;
  `Dynamic` → track velocity is a natural extension.
- **Above/below spacing collisions with lyrics.** MVP shares the
  below-staff slot; refine when a real vocal + dynamics score
  surfaces the collision.
