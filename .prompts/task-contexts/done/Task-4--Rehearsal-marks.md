id: 474bb8d0-f479-41da-bf1a-dd6c0b156207
sessionId: e96886f4-1b2f-4bdd-a6f0-190b762b8356
date: '2026-07-21T12:58:43.315Z'
label: 'Task 4: Rehearsal marks'
---
# Task 4: Rehearsal marks

## Goal
Read `<rehearsal>` (nested inside `<direction><direction-type>`) into
the model as a fourth `DirectionType` sub-record, engrave it as a
**boxed**, bold uppercase text above the staff at the associated
measure's start, and expose visibility toggling under the reserved
`MarkingCategory.REHEARSAL`.

**Depends on Task 3** — reuses the `Direction` / `DirectionType` sealed
hierarchy, the above-staff placement plumbing, and the shared
`MarkingCategory` visibility filter.

## Design

### JavaDoc

All classes and methods must have valid JavaDoc.

### runTask

Use runTask with caution is this blocks regularly. Don't wait longer than a minute and terminate it if it takes longer.

### Model — `DirectionType.Rehearsal`
- Add a new permitted record to the `DirectionType` sealed interface
  introduced in Task 3:
  ```java
  public record Rehearsal(String label) implements DirectionType {}
  ```
  `label` is the trimmed inner text of the `<rehearsal>` element (e.g.
  `"A"`, `"12"`, `"Verse 2"`). No case normalization — MusicXML lets
  the source choose. Bold and box styling are engraving conventions
  applied by the renderer, not model state.
- Task 3 already carries `Placement` on `Direction`; MusicXML almost
  always emits `placement="above"` for rehearsal marks, so the
  `Placement.DEFAULT → ABOVE` resolution from Task 3 fits without
  extra work.

### Reader — extend the `<direction-type>` switch
- `MusicXmlReader.readDirection` inner `<direction-type>` loop
  (added in Task 3): add a `case "rehearsal"` that reads the inner
  text via `readText(reader)` and builds a `Rehearsal(label)`.
- Skip trailing whitespace / drop empty labels (return null Direction
  when no other DirectionType was captured).

### Writer — extend `writeDirection`'s type switch
```xml
<direction placement="above">
  <direction-type>
    <rehearsal>A</rehearsal>
  </direction-type>
</direction>
```

### Engraver — boxed placement above the staff

Rendering rehearsal marks needs a visual **box** around the label —
that's the primary distinguishing feature vs. `Words`. Two shape
options:

- **A: extend `TextPlacement` with a `boxed` boolean**. Cheap.
  Painter draws a rectangle sized from the current
  `~0.55 * fontSize * length` heuristic + a small padding.
- **B: new placement type `BoxedTextPlacement`** with its own record
  in `LayoutResult`. More faithful long-term but adds a new list to
  `LayoutResult`, a new painter branch, and a new `MarkingCategory`
  filter site.

Going with **A**. It's a two-line change to `TextPlacement`, a small
change to `ScorePainter.drawText`, and the painter's estimated-width
heuristic is already good enough for a boxed label (a real
font-metric-aware painter can improve it later).

Details:
- `TextPlacement` grows a `boolean boxed` component. Task 3 introduces
  `MarkingCategory` — add another 6-arg / 5-arg overload pair so
  existing call sites (created in Tasks 0–3) compile unchanged
  (`boxed` defaults to `false`).
- Engraver placement for `Rehearsal`:
  - `fontSize = gap * 1.8` — slightly larger than words.
  - Style flags: bold (see the follow-up in Task 3 on plumbing style
    to painter). MVP: rely on `boxed=true` for visual distinction.
  - `x` = the associated note's x (same rules as other Directions;
    for a rehearsal that's the first element of a measure, this
    yields the measure's `contentStart`).
  - `y` = `staffY - gap * 1.5 - fontSize` (above the staff; same
    slot Task 3 uses for above-directions).
  - `category = MarkingCategory.REHEARSAL`, `align = Align.LEFT`.
- `PartInfo.hasDirectionsAbove` (from Task 3) already covers rehearsal
  marks because they resolve to `ABOVE` — the extra `gap * 3` above
  reserve is inherited for free.

### Painter — draw the box
- `ScorePainter.drawText`: when `text.boxed()`, compute the box
  extents from the same width heuristic used to place the text
  (`0.55 * fontSize * text.length()` for width, `fontSize * 1.2` for
  height), inflate by `fontSize * 0.2` padding on each side, and
  `strokeRect(...)` around it.
- Skip the box when the category is hidden (drawText already returns
  early via the filter added in Task 3 for hidden categories).

### Viewer — one more toggle
- `SheetDemoApp.buildMenuBar`: add `CheckMenuItem` "Show rehearsal
  marks" bound to `MarkingCategory.REHEARSAL` in
  `sheetView.hiddenTextCategoriesProperty()`. Checked by default.

## Implementation Steps

### Step 1 — Model
- `com.sheetmusic4j.core.model.DirectionType` (from Task 3):
  add `Rehearsal(String label)` to the permitted set.

### Step 2 — Reader
- `MusicXmlReader.readDirection` inner switch (Task 3 code): add
  ```java
  case "rehearsal" -> type = new Rehearsal(readText(reader));
  ```

### Step 3 — Writer
- `MusicXmlWriter.writeDirection`'s DirectionType switch: add a
  `Rehearsal` case emitting `<rehearsal>label</rehearsal>` inside
  `<direction-type>`.

### Step 4 — Engraving: `TextPlacement.boxed`
- `com.sheetmusic4j.engraving.TextPlacement`:
  - Add `boolean boxed` component.
  - Provide a secondary constructor without `boxed` (defaults false)
    so existing call sites compile.
- `com.sheetmusic4j.engraving.Engraver.placeDirection` (Task 3):
  add a `Rehearsal` branch that emits
  `new TextPlacement(label, x, y, gap * 1.8, Align.LEFT, MarkingCategory.REHEARSAL, /*boxed*/ true)`.

### Step 5 — Painter
- `com.sheetmusic4j.fxviewer.ScorePainter.drawText`: when `boxed`,
  after the surface `drawText` call, `strokeRect` the estimated
  extents. Use the same category-hidden guard already applied.

### Step 6 — Demo
- `SheetDemoApp`: add "Show rehearsal marks" `CheckMenuItem`.

### Step 7 — Tests

- `MusicXmlReaderTest.readsRehearsalDirection` — inline XML with
  `<direction placement="above"><direction-type>
  <rehearsal>A</rehearsal></direction-type></direction>`. Assert
  the measure carries a `Direction(Rehearsal("A"), Placement.ABOVE)`.
- `MusicXmlWriterTest.roundTripPreservesRehearsalDirection`.
- `EngraverDirectionsTest.rehearsalMarkEmitsBoxedTextAboveStaff` —
  assert one `TextPlacement` with `category = REHEARSAL`, `boxed =
  true`, y above the staff.
- `ScorePainterTextVisibilityTest.hidingRehearsalCategorySkipsMark`.
- Optional integration: read `xmlsamples/ActorPreludeSample.musicxml`
  and assert at least one measure carries a `Rehearsal("A")` (guarded
  by resource availability). If the sample is not accessible from
  the core test classpath, skip.

## Reference Examples
- `xmlsamples/ActorPreludeSample.musicxml:4771` — real-world
  `<rehearsal>A</rehearsal>` example.
- Task 3's `readDirection` inner switch — pattern for the new
  `case "rehearsal"`.
- Task 3's `placeDirection` / `MarkingCategory` — reuse verbatim.

## Verification
1. `cd sheetmusic4j && ./gradlew :core:test :engraving:test :fxviewer:test`
2. Manual smoke: `SheetDemoApp` with `ActorPreludeSample.musicxml`
   (jump to a measure with a rehearsal mark). Expect a boxed "A"
   above the staff at the correct measure start; View → Text →
   uncheck "Show rehearsal marks" hides it.

## Deferred / Follow-ups
- **Bold/italic plumbing.** The MusicXML source carries
  `font-weight="bold"`; MVP relies on the box + larger font for
  visual distinction. When Task 3's `TextStyle` follow-up lands,
  rehearsal marks should be bold by default.
- **Circle vs. rectangle boxing.** Some publishers use ovals or
  circles. Currently we hard-code `strokeRect`.
- **Auto-generated labels.** Some scores omit `<rehearsal>` and
  expect the engraver to number sections automatically. Out of
  scope here.
