id: 39a54a84-1028-467a-9de0-3778812dd11e
sessionId: cd1d4519-2c00-4fbd-834d-aa81992c9ab3
date: '2026-07-21T15:14:43.892Z'
label: 'Task 6: Part labels, system left barline, implicit grand-staff brace'
---
# Task 6: Part labels, system left barline, implicit grand-staff brace

## Goal
Make multi-instrument and grand-staff scores actually *look* like ensemble
scores instead of "stacked, unrelated staves". Concretely:

1. Render each part's `<part-name>` (and `<part-abbreviation>` on continuation
   systems) as a label to the left of its staff block.
2. Replace the current per-staff left barline with a single vertical
   **system barline** that spans from the top-line of the first staff to the
   bottom-line of the last staff of every system.
3. Emit a **brace** at the left edge of any part that occupies more than one
   staff (i.e. every grand-staff Piano-style part) so the two staves are
   visibly grouped.
4. Expose visibility of the new label text under a new
   `MarkingCategory` / `TextPlacement.Category` value `PART_LABEL`.

This task does **not** parse `<part-group>` — explicit brackets/braces
grouping several distinct parts (string quartet, orchestral wind section,
Horns 1/2 sharing a brace, ...) come in Task 7.

Concrete effect on the samples that motivated this work:
- `SchbAvMaSample.musicxml`: Voice / Piano labels appear; the Piano's
  treble+bass staves are joined by a brace and share a vertical left
  barline (the "one fa, two sol" pair reads as one instrument).
- `MozaVeilSample.musicxml`, `FaurReveSample.musicxml`: same treatment
  for the piano grand staff, plus a voice/instrument label above it.
- `MozartTrio.musicxml`, `ActorPreludeSample.musicxml`: get their per-
  part instrument names on the left; the bracket around all five
  strings / all winds is *not* drawn yet (that's Task 7).

## Design

### JavaDoc

All classes and methods must have valid JavaDoc.

### runTask

Use runTask with caution is this blocks regularly. Don't wait longer than a minute and terminate it if it takes longer.

### Model — `Part.abbreviation()`

`<part-abbreviation>` is currently silently dropped. Add a field:

- `Part.abbreviation` — nullable String, mirrors `Part.name`.
- `Part.Builder.abbreviation(String)`.
- `Part.abbreviation()` accessor returning nullable String.

Reader: extend `MusicXmlReader.readScorePart` to also capture
`<part-abbreviation>`. Store both in a small record instead of the current
`Map<String, String> partNames`:

```java
private record ScorePartInfo(String name, String abbreviation) {}
Map<String, ScorePartInfo> scoreParts = new LinkedHashMap<>();
```

Writer: `MusicXmlWriter.writeScore` emits the abbreviation right after
`<part-name>` when non-null.

### Engraving — `PART_LABEL` category

Task 1 introduced `TextPlacement.Category`. Add a new value `PART_LABEL`
to that enum (right next to `LYRIC` / `DIRECTION`). No new file needed.

### Engraving — where the label sits

The engraver already reserves `options.leftMargin()` before the staff.
That margin is *not* enough for a label like "Bass Clarinet in B♭" — but
we don't want to widen `leftMargin()` unconditionally either (single-part
scores like `c-major-scale.musicxml` would suddenly gain empty space).

Solution: add a computed **label reserve** derived from the actual labels
in the score, applied to the leftmost content position.

- `LayoutOptions` unchanged. No new tunables.
- In `Engraver.layout`, before computing `staffWidth`:
  ```java
  double labelReserve = computeLabelReserve(score, options);
  double contentLeft = options.leftMargin() + labelReserve;
  double staffWidth = options.systemWidth() - contentLeft - options.rightMargin();
  ```
  and pass `contentLeft` (not `options.leftMargin()`) into `layoutStaffRow`
  as the staff's `x`.
- `computeLabelReserve` walks `score.parts()`, picks the longest label
  actually rendered (name on first system, abbreviation on later systems —
  take the max of both), estimates its width using the same
  `0.55 * fontSize` heuristic `ScorePainter.drawText` uses, and adds a
  small trailing padding. Returns 0 when every part has a null/blank
  label (so simple test scores don't regress).
- Font size: `gap * 1.4` — same as lyrics. Aligns vertically to the
  centre of the staff group it labels.

### Engraving — one label per part per system (not per staff)

A grand-staff Piano gets **one** "Piano" label centred vertically between
its two staves, not one label per staff. Layout rule:

- After `layoutStaffRow` has finished emitting all staves of a `PartInfo p`,
  compute the vertical span
  `labelY = (firstStaffTopLine + lastStaffBottomLine) / 2 + fontSize/2`,
  then emit
  ```java
  new TextPlacement(labelText, options.leftMargin(), labelY,
      fontSize, TextPlacement.Align.LEFT, TextPlacement.Category.PART_LABEL)
  ```
- `labelText` = `part.name()` on the first row of the whole layout;
  `part.abbreviation()` (falling back to name when null) on later rows.
- Skip when both name and abbreviation are null/blank.

### Engraving — system left barline as a first-class placement

Currently `ScorePainter.drawStaff` draws:
```java
surface.strokeLine(staff.x(), staff.lineY(0), staff.x(), staff.lineY(STAFF_LINES - 1));
```
i.e. a per-staff opening barline. To unify the left edge:

- Add a new record
  `SystemBarline(double x, double topY, double bottomY, LineStyle style)`
  where `LineStyle` is a small enum `{ THIN, THICK }` (only THIN is used
  in this task; THICK is reserved for group barlines in Task 7).
- Extend `SystemLayout` with `List<SystemBarline> barlines`, defensively
  copied like `staves`. Add a compatibility constructor taking no barlines
  (defaults to `List.of()`).
- `Engraver.layout`, at the end of each row: emit one
  `SystemBarline(contentLeft, firstStaff.lineY(0), lastStaff.lineY(4),
  LineStyle.THIN)`. Only if the row contains at least one staff.
- `ScorePainter`:
  - `drawStaff` **removes** its per-staff left barline line.
  - `paint` (after drawing all staves of a system) iterates
    `system.barlines()` and strokes each.

### Engraving — implicit grand-staff brace

For every `PartInfo` where `staveCount > 1`, emit a brace at the very
left of the system spanning that part's staff block.

- New `Glyph.BRACE` (SMuFL codepoint `U+E000` = `brace`) added to the
  `Glyph` enum. `SmuflGlyphs.codepoint(BRACE)` returns `"\uE000"`.
  `halfAdvanceWidth` returns a small value (`0.6` staff spaces).
- New record `BracketPlacement(double x, double topY, double bottomY,
  BracketShape shape)` where `BracketShape` is a small enum
  `{ BRACE, BRACKET, LINE }`. (Task 7 reuses this record for
  `<part-group>`-driven placements — hence the extra shapes now.)
- Extend `SystemLayout` with `List<BracketPlacement> brackets`
  (compat constructor with `List.of()`).
- In `Engraver.layout` row loop, after computing the staff top/bottom of
  each multi-staff part, add
  ```java
  brackets.add(new BracketPlacement(
      contentLeft - options.staffLineGap() * 0.8,
      firstStaffOfPart.lineY(0),
      lastStaffOfPart.lineY(4),
      BracketShape.BRACE));
  ```
- `ScorePainter`:
  - Draw braces via `drawSmuflGlyph` when Bravura is available. SMuFL's
    `brace` (E000) is a single vertical glyph designed to stretch — we
    approximate stretching by drawing it at
    `sizeHint = bottomY - topY`, anchored at
    `(x, (topY + bottomY) / 2 + sizeHint * 0.25)` (rough vertical
    centering; Bravura's brace baseline sits about a quarter em below
    center). Refinement to a true `scale`-based stretched draw is
    called out as a follow-up.
  - Fallback (no Bravura): draw a plain vertical line at `x` from
    `topY` to `bottomY`, with two horizontal serifs at each end (2/3 of
    `gap` wide). This is uglier than a real brace but unambiguously
    signals "these staves are grouped" and matches what we already do
    for missing rest glyphs (primitive fallback).

### Viewer

- `SheetView.hiddenTextCategoriesProperty()` already accepts arbitrary
  categories. No new plumbing.
- `SheetDemoApp.buildMenuBar`: extend the *View → Text* submenu with
  `CheckMenuItem` "Show instrument labels" bound to
  `TextPlacement.Category.PART_LABEL`. Checked by default.
- Braces / system barlines are structural, not "text" — they don't get a
  visibility toggle in this task (a future "Show group brackets" toggle
  in Task 7 will cover both).

## Implementation Steps

### Step 1 — Model: `Part.abbreviation`
- `sheetmusic4j/core/src/main/java/com/sheetmusic4j/core/model/Part.java`
  - Add nullable `String abbreviation` field, accessor, and
    `Builder.abbreviation(String)`.

### Step 2 — Reader/Writer round-trip for abbreviation
- `MusicXmlReader.readScorePart`
  - Replace `partNames` map with `Map<String, ScorePartInfo>` (name +
    abbreviation) locally in `parseDocument`.
  - Inside `readScorePart`, add
    `case "part-abbreviation" -> abbreviation = readText(reader);`.
  - Pass both into `Part.Builder`.
- `MusicXmlWriter.writeScore`
  - After `w.textElement("part-name", ...)`, emit
    `<part-abbreviation>` when non-null.

### Step 3 — Engraving: new category and placements
- `sheetmusic4j/engraving/src/main/java/com/sheetmusic4j/engraving/TextPlacement.java`
  - Add `PART_LABEL` to `Category`.
- `sheetmusic4j/engraving/src/main/java/com/sheetmusic4j/engraving/Glyph.java`
  - Add `BRACE`.
- `sheetmusic4j/engraving/src/main/java/com/sheetmusic4j/engraving/SystemBarline.java`
  (new) — record + `LineStyle { THIN, THICK }` inner enum.
- `sheetmusic4j/engraving/src/main/java/com/sheetmusic4j/engraving/BracketPlacement.java`
  (new) — record + `BracketShape { BRACE, BRACKET, LINE }` inner enum.
- `SystemLayout`
  - Add `List<SystemBarline> barlines` and `List<BracketPlacement> brackets`.
  - Compact constructor `List.copyOf`s both.
  - Backwards-compatible constructor without them defaults to `List.of()`.

### Step 4 — SMuFL mapping
- `sheetmusic4j/fxviewer/src/main/java/com/sheetmusic4j/fxviewer/SmuflGlyphs.java`
  - `codepoint(BRACE) -> "\uE000"`.
  - `halfAdvanceWidth`: `BRACE -> 0.6` staff spaces (used for the
    horizontal offset when centring; the vertical span is what actually
    matters).

### Step 5 — Engraver
- `sheetmusic4j/engraving/src/main/java/com/sheetmusic4j/engraving/Engraver.java`
  - Add `private static double computeLabelReserve(Score score, LayoutOptions options)`.
  - Change `staffWidth` computation and pass `contentLeft = leftMargin +
    labelReserve` into `layoutStaffRow` as the staff `x`.
  - In the row loop, collect the staff top/bottom of each part; after
    processing all parts of a row, produce:
    - one `SystemBarline` spanning first-staff top to last-staff bottom.
    - one `BracketPlacement(shape=BRACE)` per multi-staff part.
  - Emit the part-label `TextPlacement` per part per row (name on
    row 0, abbreviation on subsequent rows). Position: `x = leftMargin`,
    `y = midpoint of the part's staff block + fontSize/2`.
  - Construct the row's `SystemLayout(x, y, width, staves, barlines,
    brackets)` — new 6-arg constructor.
- `Engraver.headerAdvance` unchanged (label reserve is a separate,
  page-global inset applied before the header).

### Step 6 — Painter
- `sheetmusic4j/fxviewer/src/main/java/com/sheetmusic4j/fxviewer/ScorePainter.java`
  - Remove the per-staff left barline line in `drawStaff`.
  - In `paint`, iterate `layout.systems()` (not just `layout.staves()`).
    For each system:
    - Draw staves as today.
    - Draw `system.barlines()` as thin vertical lines.
    - Draw `system.brackets()`:
      - `BRACE`: try `drawSmuflGlyph("\uE000", x, midY, span)`; on
        failure draw the vertical-line + serifs fallback.
      - `BRACKET` / `LINE`: only produced by Task 7 — provide a small
        `strokeLine`-based fallback stub so the switch is exhaustive.
- `LayoutResult.staves()` still returns a flat list — keep it for
  existing callers (tests, diff harness). But the top-level paint loop
  becomes system-driven.

### Step 7 — Demo
- `sheetmusic4j/fxdemo/src/main/java/com/sheetmusic4j/fxdemo/SheetDemoApp.java`
  - `View → Text`: add "Show instrument labels" `CheckMenuItem` for
    `PART_LABEL`. Checked by default.

### Step 8 — Tests

- `sheetmusic4j/core/src/test/java/com/sheetmusic4j/core/musicxml/MusicXmlReaderTest.java`
  - `readsPartAbbreviation` — inline XML with `<part-list><score-part>
    <part-name>Bass Clarinet in B♭</part-name>
    <part-abbreviation>B. Cl.</part-abbreviation></score-part></part-list>`.
    Assert `part.abbreviation().equals("B. Cl.")`.
- `MusicXmlWriterTest.roundTripPreservesPartAbbreviation`.

- New `EngraverPartLabelTest.java`:
  - `emitsPartNameLabelOnFirstSystem` — score with one part named
    "Voice"; assert a `TextPlacement` with `Category.PART_LABEL` and
    text `"Voice"`.
  - `emitsAbbreviationOnLaterSystems` — score forced onto 2 systems
    (large measure count or small `systemWidth`); assert row 0's label
    is the full name, row 1's is the abbreviation.
  - `labelReserveShiftsStaffRight` — with vs. without labels, assert the
    staff's `x` grows by roughly the estimated label width.
  - `noLabelWhenPartNameIsNull` — single-part unnamed score: no
    `PART_LABEL` placement, no label reserve.
  - `partLabelSitsBetweenStavesOfMultiStaffPart` — Piano with 2 staves;
    assert exactly ONE label placement per row, y between the two staves.

- New `EngraverSystemBarlineTest.java`:
  - `emitsOneLeftBarlinePerSystemNotOnePerStaff` — score with 3 parts;
    assert `system.barlines().size() == 1` and its `topY == firstStaff.lineY(0)`
    and `bottomY == lastStaff.lineY(4)`.
  - `noLeftBarlineForEmptyScore`.

- New `EngraverGrandStaffBraceTest.java`:
  - `piano2StavesEmitsBrace` — score with 1 part, `<staves>2</staves>`,
    treble + bass clefs; assert exactly one `BracketPlacement` with
    `shape == BRACE`, spanning the two staves.
  - `singleStaffPartsHaveNoBrace` — voice-only score → empty
    `system.brackets()`.
  - `multiplePartsEachMultiStaff` — two piano parts, both grand-staff;
    assert 2 braces, one per part.

- Extend `ScorePainterTextVisibilityTest.java`:
  - `hidingPartLabelCategorySkipsLabel`.

- Bring the two reference samples in as smoke fixtures (opt-in):
  - `EngraverRealSamplesTest.piano2StavesFromMozaVeilSample` — reads
    `xmlsamples/MozaVeilSample.musicxml` (Voice + Piano), asserts:
    - 2 parts, 3 staves total per system.
    - `system.brackets().size() >= 1` (the Piano brace).
    - `system.barlines().size() == 1` and it spans all 3 staves.
    - 2 `PART_LABEL` placements per row.
  - Same test for `xmlsamples/SchbAvMaSample.musicxml`.
  - Guarded with `assumeTrue(resource != null)` per the existing
    fixture pattern.

## Reference Examples
- `Engraver.java` row loop (`for (PartInfo p : parts)`) — where the
  brace and label emission hooks in.
- `Engraver.layoutTitleBlock` — pattern for emitting `TextPlacement`s
  outside a staff (used verbatim for the part-label placement).
- `ScorePainter.drawStaff` — the per-staff left barline line is what
  goes away in Step 6.
- `SmuflGlyphs.java` — pattern for adding `BRACE` to `codepoint` /
  `halfAdvanceWidth` (`AUG_DOT` is the closest existing single-glyph
  addition).
- `MusicXmlReader.readScorePart` — pattern for adding the
  `<part-abbreviation>` case (`<part-name>` right next door).
- `xmlsamples/SchbAvMaSample.musicxml:98-127` — the two-part list with
  `Voice` + `Piano` (no `<part-group>`, but Piano has
  `<staves>2</staves>` later in the file → target of the implicit
  brace).
- `xmlsamples/ActorPreludeSample.musicxml:99-110` — real-world example
  of `<part-abbreviation>` values (`Picc.`, `Fl.`, `Cl.`, …).

## Verification
1. `cd sheetmusic4j && ./gradlew :core:test :engraving:test :fxviewer:test :fxdemo:test`
   — all new tests + existing suites pass.
2. Round-trip: `MusicXmlWriterTest.roundTripPreservesStructure` still
   passes for `c-major-scale.musicxml` (single unnamed part — no label,
   no brace, no visual change).
3. Manual smoke: `SheetDemoApp` with `SchbAvMaSample.musicxml`.
   Expect:
   - "Voice" label to the left of the single vocal staff.
   - "Piano" label centred between the treble and bass staves of P2.
   - A brace `{` joining the two Piano staves at the left edge.
   - One thin vertical barline at the left edge of every system,
     spanning from the top-line of "Voice" down to the bottom-line of
     the Piano bass staff.
   - `View → Text → Show instrument labels` toggles the two labels
     off/on.
4. Manual smoke: `MozaVeilSample.musicxml` — same, with German lyrics
   still visible under the voice staff (Task 2 regression check).
5. Manual smoke: `c-major-scale.musicxml` — layout looks identical to
   before (no labels present, staff x unchanged, no brace).

## Deferred / Follow-ups
- **Truly stretched brace glyph.** Bravura's `brace` (E000) is a single-
  size glyph; publishers typically use a stretched variant via a
  font-shaping layer or by drawing several stacked instances. MVP scales
  the glyph in size which will look thick on tall spans and thin on
  short ones. A proper implementation replaces this with a stretched
  path or a multi-piece composite (top + straight + bottom).
- **`part-name-display` / `part-abbreviation-display`.** Composite
  names with embedded accidentals (`Clarinets in B♭` from
  `ActorPreludeSample.musicxml`) come from `<part-name-display>` with
  `<accidental-text>flat</accidental-text>`. This task uses the plain
  `<part-name>` text only, so `Clarinets in Bb` shows as-is. Add a
  display-parser follow-up when needed.
- **Instrument labels on continuation systems for very wide labels.**
  Some publishers drop the label entirely after the second system.
  Currently we keep printing the abbreviation on every system.
- **Toggle for braces / system barlines.** Not exposed in the View
  menu in this task — Task 7 adds a general "Show group brackets"
  toggle covering both implicit braces (from this task) and explicit
  `<part-group>` brackets.
