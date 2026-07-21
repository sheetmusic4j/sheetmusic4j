id: 0d610d5c-29b6-4816-92d6-5bebcae43eb9
sessionId: cd1d4519-2c00-4fbd-834d-aa81992c9ab3
date: '2026-07-21T15:17:18.866Z'
label: 'Task 7: Part groups — brackets, braces across parts, group barlines'
---
# Task 7: Part groups — brackets, braces across parts, group barlines

## Goal
Read MusicXML `<part-group>` from `<part-list>` into the model, engrave
the corresponding **bracket** / **brace** / **square bracket** at the
left edge of every system spanning the grouped parts, and extend the
system left barline into per-group barlines when the group requests
`<group-barline>yes</group-barline>`.

**Depends on Task 6** — reuses `BracketPlacement`, `SystemBarline`,
`Glyph.BRACE`, the label-reserve mechanism, and the system-driven paint
loop.

## Design

### Model — `PartGroup` on `Score`

`<part-group>` uses paired `<part-group number="N" type="start"/>` and
`<part-group number="N" type="stop"/>` sentinels inside `<part-list>`.
Groups can nest (see `ActorPreludeSample.musicxml`, which has an outer
`bracket` around a whole wind section and inner `brace`s around
`Horns 1/2` etc.).

- New record `PartGroup(int number, int startPartIndex, int endPartIndex,
  GroupSymbol symbol, boolean groupBarline, String name,
  String abbreviation)` in `com.sheetmusic4j.core.model`.
  - `startPartIndex` / `endPartIndex` are inclusive indices into
    `Score.parts()` (post-parse).
  - `GroupSymbol` is a small enum `{ BRACKET, BRACE, SQUARE, LINE, NONE }`
    matching MusicXML's `<group-symbol>` values (`bracket`, `brace`,
    `square`, `line`; unspecified → `NONE`).
- `Score` grows `List<PartGroup> partGroups()`, populated via
  `Score.Builder.addPartGroup(PartGroup)`. Order is document order
  (outer-first / inner-later) — enables predictable nesting when we
  need to lay out multiple bracket columns side by side.

### Reader — `<part-group>` state machine inside `<part-list>`

`MusicXmlReader.parseDocument` currently walks the top-level events and
dispatches `score-part` and `part` cases directly. `<part-group>` lives
*inside* `<part-list>` (a sibling of `<score-part>`), but the current
code doesn't parse the `<part-list>` container at all — it just picks
up `<score-part>` START events wherever they appear. That's fortunate:
we can add a `case "part-group"` at the same level.

Steps:
- New `readPartGroup(reader)` helper that returns a small record
  `ParsedPartGroup(int number, String type, GroupSymbol symbol,
  boolean barline, String name, String abbreviation)`.
- Maintain a mutable `Map<Integer, PendingGroup> openGroups` in
  `parseDocument`, keyed by group `number`.
- On a `type="start"` group, remember `startPartIndex = partOrder.size()`
  and the symbol/name/abbreviation/barline flags.
- On a `type="stop"` group, close the pending group with
  `endPartIndex = partOrder.size() - 1` and add the `PartGroup` to the
  `Score.Builder`.
- Malformed input (stop without start, or unclosed group at EOF): log
  and drop, no exception.

Note on ordering: `<part-group start>` typically appears immediately
before the `<score-part>`s it wraps, and `<part-group stop>` immediately
after. So the "start index = current parts count" rule works for the
canonical formatting used by every real-world sample. This is called
out in a comment.

### Writer — round-trip

`MusicXmlWriter.writeScore` currently emits:
```xml
<part-list>
  <score-part id="P1">…</score-part>
  <score-part id="P2">…</score-part>
</part-list>
```

Rewrite the emit loop to interleave group start/stop events:
- Sort `score.partGroups()` by `startPartIndex` (already the natural
  document order, but be explicit).
- Before emitting `<score-part>` at index `i`, emit any group whose
  `startPartIndex == i`.
- After emitting `<score-part>` at index `i`, emit any group whose
  `endPartIndex == i` in reverse start-order (so nesting closes
  correctly, LIFO).

Each start emits:
```xml
<part-group number="N" type="start">
  <group-name>Horns in F</group-name>            <!-- when present -->
  <group-abbreviation>Hn.</group-abbreviation>   <!-- when present -->
  <group-symbol>brace</group-symbol>              <!-- when != NONE -->
  <group-barline>yes|no</group-barline>
</part-group>
```

Each stop emits `<part-group number="N" type="stop"/>`.

### Engraver — bracket placements and group barlines

Task 6 introduced:
- `BracketPlacement(x, topY, bottomY, shape)` with
  `BracketShape { BRACE, BRACKET, LINE }`.
- `SystemBarline(x, topY, bottomY, style)` with `LineStyle { THIN,
  THICK }`.
- `Glyph.BRACE` mapping to SMuFL `U+E000`.

Extend both:
- `BracketShape` gains `SQUARE` (matches MusicXML `<group-symbol>square</group-symbol>`).
- `Glyph.BRACKET_TOP` (SMuFL `U+E003` = `bracketTop`) and
  `Glyph.BRACKET_BOTTOM` (`U+E004` = `bracketBottom`) added and mapped in
  `SmuflGlyphs`. Half advance width ~ 0.7 staff spaces.
- No new SMuFL glyph needed for `SQUARE` and `LINE` — both are drawn as
  primitive strokes by the painter (see below).

Engraver hooks (all in `Engraver.layout`'s row loop):

1. **Bracket column layout.**
   Multiple nested groups need to sit *side-by-side* on the left, from
   outer (leftmost) to inner (closest to the staff). Given
   `score.partGroups()` in document order, compute each group's nesting
   depth by counting how many groups fully contain it. Assign each
   group an x offset:
   ```
   groupX(depth) = contentLeft - (depth + 1) * options.staffLineGap() * 1.2
   ```
   The implicit grand-staff brace from Task 6 is treated as depth 0
   (closest to the staff) — this task pushes explicit group brackets to
   the left of it when both are present on the same staves.

2. **For each group whose part-range intersects the parts on this row**,
   compute `topY = firstIncludedStaff.lineY(0)`,
   `bottomY = lastIncludedStaff.lineY(4)`, and emit
   `BracketPlacement(groupX, topY, bottomY, shape)` where `shape` maps:
   ```
   GroupSymbol.BRACE   -> BracketShape.BRACE
   GroupSymbol.BRACKET -> BracketShape.BRACKET
   GroupSymbol.SQUARE  -> BracketShape.SQUARE
   GroupSymbol.LINE    -> BracketShape.LINE
   GroupSymbol.NONE    -> (skipped, no placement)
   ```

3. **Group barlines** (`group.groupBarline() == true`): emit an extra
   `SystemBarline` at the same x as the system left barline, spanning
   only the group's staves (top of first, bottom of last). Skip if it
   coincides with an already-emitted barline (e.g. group spans all
   parts of the system).

4. **Label reserve.** Extend `computeLabelReserve` from Task 6 to also
   reserve horizontal room for the bracket column: add
   `(maxDepth + 1) * gap * 1.2` when any group exists.

5. **Group name / abbreviation.** When a group carries `<group-name>`
   (`"Horns in F"` in `ActorPreludeSample.musicxml`), emit a
   `TextPlacement` at the bracket column with
   `Category.PART_LABEL`, centred vertically on the group span,
   right-aligned so it sits between the bracket and the innermost
   labels. Full name on row 0, `<group-abbreviation>` on later rows
   (falls back to name).

### Painter — bracket drawing

Extend the `BracketPlacement` switch added in Task 6:

- **`BRACE`** — unchanged from Task 6 (SMuFL `U+E000` when available,
  else vertical line + serifs).
- **`BRACKET`** — the canonical orchestral square-cornered bracket with
  ornamental tips. Draw:
  - a **thick** vertical line at `x` from `topY - gap * 0.4` to
    `bottomY + gap * 0.4` (a small overshoot at each end is standard),
    line width `gap * 0.4`.
  - a SMuFL `bracketTop` (E003) at `(x, topY)` and `bracketBottom`
    (E004) at `(x, bottomY)` for the ornamental tips.
  - Fallback (no Bravura): also draw two small horizontal serifs at
    each end — same primitive as the brace fallback but square instead
    of curved.
- **`SQUARE`** — plain rectangular bracket without ornaments. Same as
  `BRACKET` minus the SMuFL tips: just a thick vertical line with two
  short horizontal segments at each end (both drawn as
  `strokeLine`, no SMuFL involved).
- **`LINE`** — a single thin vertical line at `x`, no serifs.

### Viewer

- Add a new `TextPlacement.Category` value — reuse `PART_LABEL` from
  Task 6; group names share the same visibility toggle as instrument
  names (they're the same conceptual thing to the reader).
- `SheetDemoApp.buildMenuBar`: add `CheckMenuItem` "Show group brackets"
  bound to a new observable boolean on `SheetView`
  (`bracketsVisibleProperty`) — a **structural** toggle (not text), so
  it needs its own flag independent of `hiddenTextCategoriesProperty`.
  When off, the painter skips *all* `BracketPlacement`s (both the
  implicit grand-staff braces from Task 6 and the explicit group
  brackets from this task).

## Implementation Steps

### Step 1 — Model
- `sheetmusic4j/core/src/main/java/com/sheetmusic4j/core/model/GroupSymbol.java`
  (new) — enum `BRACKET, BRACE, SQUARE, LINE, NONE` with `xmlValue()`
  and `fromXml(String)`.
- `sheetmusic4j/core/src/main/java/com/sheetmusic4j/core/model/PartGroup.java`
  (new) — record per §Model.
- `sheetmusic4j/core/src/main/java/com/sheetmusic4j/core/model/Score.java`
  - Add `List<PartGroup> partGroups`, accessor, and `Builder.addPartGroup`.

### Step 2 — Reader
- `MusicXmlReader.parseDocument`
  - Track `Map<Integer, PendingGroup> openGroups`.
  - Add `case "part-group"` that dispatches by `type` attribute:
    - `start` → build a `PendingGroup` from `readPartGroup(reader)`,
      set `startIndex = partOrder.size()`, put in map.
    - `stop` → pop from map, set `endIndex = partOrder.size() - 1`,
      call `score.addPartGroup(...)`.
- `readPartGroup(reader)` helper: read `<group-name>`,
  `<group-abbreviation>`, `<group-symbol>`, `<group-barline>` (yes/no →
  boolean) up to `END_ELEMENT("part-group")`.

### Step 3 — Writer
- `MusicXmlWriter.writeScore` — replace the flat
  `for (Part part : score.parts())` `<part-list>` emit with an
  interleaved loop per §Writer. Skip empty groups (start == end +1).

### Step 4 — Engraving: shape + glyph additions
- `sheetmusic4j/engraving/src/main/java/com/sheetmusic4j/engraving/BracketPlacement.java`
  - Add `SQUARE` to `BracketShape` (created in Task 6).
- `sheetmusic4j/engraving/src/main/java/com/sheetmusic4j/engraving/Glyph.java`
  - Add `BRACKET_TOP`, `BRACKET_BOTTOM`.
- `sheetmusic4j/fxviewer/src/main/java/com/sheetmusic4j/fxviewer/SmuflGlyphs.java`
  - `codepoint(BRACKET_TOP) -> "\uE003"`, `codepoint(BRACKET_BOTTOM) -> "\uE004"`.
  - `halfAdvanceWidth` for both: `0.7` staff spaces.

### Step 5 — Engraver
- `sheetmusic4j/engraving/src/main/java/com/sheetmusic4j/engraving/Engraver.java`
  - Precompute per-group nesting depth: for each group, count how many
    other groups fully contain its part range.
  - Extend `computeLabelReserve` (Task 6) to add
    `(maxGroupDepth + 1) * gap * 1.2` when `score.partGroups()` is non-empty.
  - Row loop: after emitting per-part barlines/braces (Task 6), also
    emit per-group `BracketPlacement`s and `SystemBarline`s per §Engraver.
  - Emit `TextPlacement` for `<group-name>` / `<group-abbreviation>`
    (`Category.PART_LABEL`, y-centred on the group span).

### Step 6 — Painter
- `sheetmusic4j/fxviewer/src/main/java/com/sheetmusic4j/fxviewer/ScorePainter.java`
  - Extend the `BracketPlacement` switch to handle `BRACKET`, `SQUARE`,
    `LINE`. Reuse the Task 6 fallback pattern.
  - Add `private boolean bracketsVisible = true;` +
    `setBracketsVisible(boolean)`; when false, skip all
    `system.brackets()` painting.

### Step 7 — Viewer / demo
- `SheetView`
  - Add `BooleanProperty bracketsVisibleProperty()` with default true.
  - `rebuild()` calls `renderer.setBracketsVisible(...)` before render.
- `ScoreRenderer` — add pass-through setter to `ScorePainter`.
- `SheetDemoApp.buildMenuBar` — add `CheckMenuItem` "Show group brackets"
  bound to that property.

### Step 8 — Tests

**Core (reader + writer)**
- `MusicXmlReaderTest.readsSimpleBracketGroup` — inline XML with
  ```xml
  <part-list>
    <part-group number="1" type="start">
      <group-symbol>bracket</group-symbol>
      <group-barline>yes</group-barline>
    </part-group>
    <score-part id="P1"><part-name>V1</part-name></score-part>
    <score-part id="P2"><part-name>V2</part-name></score-part>
    <part-group number="1" type="stop"/>
  </part-list>
  ```
  Assert `score.partGroups()` has one entry with `symbol == BRACKET`,
  `groupBarline == true`, `startPartIndex == 0`, `endPartIndex == 1`.
- `readsNestedGroups` — inline XML with an outer `bracket` around 3
  parts and an inner `brace` around parts 1..2. Assert both groups
  present, correct indices, correct nesting.
- `readsGroupName` — outer group with `<group-name>Horns in F</group-name>`.
- `unclosedGroupIsDropped` — a `start` without a matching `stop`; assert
  no group is added and no exception is thrown.
- `MusicXmlWriterTest.roundTripPreservesPartGroups` — build a score
  with a `BRACKET` group over 2 parts (with `groupBarline=true`) and a
  nested `BRACE` group over parts 1..1; round-trip; assert both groups
  survive with identical indices and flags.

**Engraving**
- New `EngraverPartGroupTest.java`:
  - `singleBracketOverTwoPartsEmitsBracketPlacement` — assert one
    `BracketPlacement` with `shape == BRACKET`, spanning top of P1
    staff to bottom of P2 staff.
  - `nestedGroupsSitAtDifferentX` — outer bracket over 4 parts, inner
    brace over parts 1..2. Assert two `BracketPlacement`s with
    different x (outer < inner).
  - `groupBarlineYesAddsExtraSystemBarline` — assert `system.barlines()
    .size() == 2` when one group asks for a barline and the system
    also has the Task-6 left barline.
  - `groupSymbolLineEmitsThinLine` — assert `shape == LINE`.
  - `groupSymbolNoneEmitsNothing`.
  - `groupNameAppearsAsPartLabel` — assert one `PART_LABEL` placement
    with the group name, x < the innermost per-part labels.
- `EngraverGrandStaffBraceTest` (from Task 6): add
  `pianoBraceCoexistsWithOrchestralBracket` — score with `<part-group>
  bracket</part-group>` around 3 parts, one of which is a grand-staff
  piano. Assert:
  - 2 `BracketPlacement`s: one `BRACE` for the piano (Task 6 behaviour),
    one `BRACKET` for the group (Task 7).
  - Their x values differ: bracket is further left.

**Viewer**
- Extend `ScorePainterTextVisibilityTest.java`:
  - `hidingBracketsSkipsAllBracketPlacements` — build a layout with a
    `BRACE` and a `BRACKET`; paint with `setBracketsVisible(false)`;
    assert neither is drawn (record via a stub `RenderSurface`).

**Real-world sanity**
- Extend `EngraverRealSamplesTest`:
  - `mozartTrioHasOrchestralBracket` — reads
    `xmlsamples/MozartTrio.musicxml`; asserts `score.partGroups().size()
    == 1`, symbol `BRACKET`, spanning parts P1..P5, `groupBarline ==
    true`. Layout produces one bracket placement per system spanning
    all 5 staves.
  - `actorPreludeHasNestedGroups` — reads
    `xmlsamples/ActorPreludeSample.musicxml`; asserts the outermost
    bracket spans the wind section and at least one nested `BRACE`
    around a horn/1-2 pair exists in `score.partGroups()`.

## Reference Examples
- `xmlsamples/MozartTrio.musicxml:65-142` — canonical outer-bracket
  string quintet example with `group-barline`.
- `xmlsamples/ActorPreludeSample.musicxml:99-260` — dense nested groups:
  outer `bracket` around the winds, inner `brace` groups around
  Flutes 1/2, Oboes, Clarinets, Bassoons, plus a further `bracket`
  around Horns 1/2 etc. Excellent stress test for the nesting-depth
  computation.
- `MusicXmlReader.parseDocument` — pattern for adding a top-level
  case at the same nesting as `score-part`.
- Task 6's `BracketPlacement` / `SystemBarline` — reused verbatim,
  only new shape values and painter branches added here.
- SMuFL Bravura documentation: `brace` (E000), `bracketTop` (E003),
  `bracketBottom` (E004). No new font asset required — same Bravura.otf
  already handles them.

## Verification
1. `cd sheetmusic4j && ./gradlew :core:test :engraving:test :fxviewer:test :fxdemo:test`
   — all new tests + existing suites pass.
2. Round-trip: `MusicXmlWriterTest.roundTripPreservesStructure` still
   passes for every existing fixture; `roundTripPreservesPartGroups`
   passes for the new group-bearing input.
3. Manual smoke: `SheetDemoApp` with `MozartTrio.musicxml`. Expect:
   - A tall square bracket at the far left of every system spanning
     all 5 strings.
   - Barlines connecting the 5 staves top-to-bottom
     (`<group-barline>yes</group-barline>`).
   - Per-part instrument names (`clarinet in A`, `violino I`, …) to
     the immediate right of the bracket.
   - `View → Show group brackets` toggles the bracket off/on;
     `View → Text → Show instrument labels` toggles the names.
4. Manual smoke: `ActorPreludeSample.musicxml`. Expect several nested
   brackets/braces (winds, brass, horn pairs); side-by-side without
   overlap.
5. Manual smoke: `SchbAvMaSample.musicxml` — appearance unchanged from
   Task 6 (the sample has no `<part-group>`; only the implicit grand-
   staff brace from Task 6 shows).
6. Regression: `c-major-scale.musicxml` and any group-free score renders
   identically to before Task 7 landed.

## Deferred / Follow-ups
- **Stretched bracket / brace glyphs.** Task 6 already flags this; the
  same limitation applies to the SMuFL bracket tips drawn here.
- **Per-group visibility.** MVP has a single "Show group brackets"
  toggle. A finer-grained "Show winds bracket" / "Hide brass bracket"
  UX would need per-group state that we don't yet expose.
- **Group barlines that span nested subgroups differently.** MusicXML
  lets each `<part-group>` independently opt into `<group-barline>` —
  MVP emits one extra `SystemBarline` per group, which stacks correctly
  but can produce visually redundant lines for tightly nested groups.
  Deduplication is deferred.
- **`<group-symbol>square</group-symbol>` and `<group-symbol>line</group-symbol>`
  glyph refinements.** MVP draws these as primitive strokes; real
  publishers use SMuFL `bracketTop`/`bracketBottom` variants for these
  too.
- **Cross-staff beaming inside a grouped part.** Piano LH ↔ RH beams
  are common; this remains deferred (a rendering pass on `BeamPlacement`
  spanning two staves).
- **Interaction with system breaks.** When a group's parts happen to
  fall on different systems (rare — usually groups span all parts on
  every system), we currently just clip the placement to whichever
  staves are present on the row. This is correct but may look sparse
  on the row that only carries the group's tail; a real engraver
  reruns the bracket-column layout per row.
