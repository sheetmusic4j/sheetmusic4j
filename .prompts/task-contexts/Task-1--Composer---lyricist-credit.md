id: 8a5ee80e-204f-48e3-844e-733eaa116a9d
sessionId: e96886f4-1b2f-4bdd-a6f0-190b762b8356
date: '2026-07-21T12:45:26.918Z'
label: 'Task 1: Composer / lyricist credits'
---
# Task 1: Composer / lyricist credits

## Goal
Read composer / lyricist / arranger metadata from MusicXML `<identification>`
(with a `<credit>` fallback) into a semantic `Score.creators()` list, emit it
as `TextPlacement`s under the title block, and give fxviewer toggle-able
visibility flags per text category so the user can hide any non-notes text.

## Design

### Model — semantic-only credits
- New model type `Creator(String role, String name)`.
  - `role` is the MusicXML `type` attribute in lowercase (e.g. `composer`,
    `lyricist`, `arranger`, `poet`, `translator`, `transcriber`) — kept as a
    free-form string so we do not have to enumerate every MusicXML role.
  - `name` is the trimmed text content.
- `Score` grows `List<Creator> creators()` (immutable copy, defensively
  ordered) plus a `Builder.addCreator(Creator)` / `Builder.creators(List)`
  accessor — mirrors the existing `parts` handling.
- Positioning attributes on `<credit-words>` (default-x/y, font-size, valign,
  halign, justify) are ignored: this task is semantic-only per the agreed
  design. A follow-up task can add a positioned variant if needed.

### Reader — two sources, normalize into `Creator`
Priority order (semantic sources win over positioned fallbacks):
1. `<identification><creator type="…">Name</creator>` — canonical semantic
   source. Multiple `<creator>` children are all captured.
2. `<credit>` elements that carry `<credit-type>` matching a known creator
   role (`composer`, `lyricist`, `arranger`, `poet`, `translator`,
   `transcriber`, `arranger`) — used **only when the corresponding role is
   not already present** from step 1. The `<credit-words>` text becomes the
   name; positional attributes on `<credit-words>` are discarded.

Both branches feed the same `Score.Builder.addCreator(...)`. Later
duplicates for the same role are dropped (first one wins).

### Writer — round-trip through `<identification>`
`MusicXmlWriter` emits an `<identification>` block right after `<work>`
containing one `<creator type="role">Name</creator>` per stored creator so
the round-trip test in `MusicXmlWriterTest` stays green when we assert on
creators later.

### Engraver — extend the title block
- `TextPlacement` gains a `TextPlacement.Category` enum tagging the source
  (`TITLE`, `SUBTITLE`, `CREATOR`; leave room for `LYRIC`, `DIRECTION`,
  `REHEARSAL`, `CHORD_SYMBOL` in later tasks). The existing single-arg
  record constructor stays available via a compatibility overload
  (`category` defaults to `TITLE`).
- `Engraver.layoutTitleBlock` grows a second pass that, after the movement
  title, places creator lines just below the title block:
  - Composer/arranger → right-aligned at `systemWidth - rightMargin`.
  - Lyricist/poet/translator → left-aligned at `leftMargin`.
  - Anything else → left-aligned, stacked below the lyricist row.
  - Font size: `gap * 1.1` (smaller than the movement title's `gap * 1.6`,
    matches engraving convention).
  - Vertical advance: one `fontSize * 1.4` step. Composer and lyricist on
    the same visual row share the same y baseline (advance counted once);
    additional rows advance normally.
- The advance returned by `layoutTitleBlock` grows by the added rows so the
  first staff is pushed down correctly (already exercised by
  `titleBlockPushesFirstStaffDownward`).

### Viewer — visibility flags
- `ScorePainter.paint` filters `layout.texts()` through a `Set<TextPlacement
  .Category>` "hidden categories" set carried on the painter.
- `ScorePainter` gains a `setHiddenCategories(Set<Category>)` /
  `getHiddenCategories()` accessor (default: empty = show everything).
- `ScoreRenderer` exposes the same setter so `SheetView` can plumb it
  through, and `SheetView` publishes:
  - `ObservableSet<TextPlacement.Category> hiddenTextCategoriesProperty()`
    (JavaFX observable set; changes trigger `rebuild()`).
- `SheetDemoApp` View menu adds a **Show** submenu (or checkable menu
  items) — one `CheckMenuItem` per category currently emitted (`Title`,
  `Composer / Lyricist`). Ticked by default. Toggling flips the category
  in `sheetView.hiddenTextCategoriesProperty()`.
- Note: hidden text still consumes vertical space (engraver already
  reserved it). Reclaiming the gap is a follow-up — noted in a code
  comment on `ScorePainter.paint`.

## Implementation Steps

### Step 1 — Model: `Creator` + `Score.creators()`
- `sheetmusic4j/core/src/main/java/com/sheetmusic4j/core/model/Creator.java`
  (new) — record `Creator(String role, String name)` with a static
  `of(String rawType, String name)` normalizer that lowercases + trims the
  role, treats null/blank role as `"other"`.
- `sheetmusic4j/core/src/main/java/com/sheetmusic4j/core/model/Score.java`
  - add `private final List<Creator> creators;`
  - add `public List<Creator> creators()`
  - add `Builder.addCreator(Creator)` and `Builder.creators(List<Creator>)`
    following the `parts` pattern (`ArrayList` backing, `List.copyOf` in
    the constructor).

### Step 2 — Reader: parse `<identification>` + `<credit>`
- `sheetmusic4j/core/src/main/java/com/sheetmusic4j/core/musicxml/MusicXmlReader.java`
  - In `parseDocument` switch, add two new cases:
    - `"identification"` → `readIdentification(reader, score)`
    - `"credit"` → `readCredit(reader, score)`
  - `readIdentification`: consume child elements; for each `<creator>`
    grab `type` attribute and text via `readText`; call
    `score.addCreator(Creator.of(type, text))`. Skip empty names.
  - `readCredit`: scan for `<credit-type>` and `<credit-words>` inside;
    if the credit-type matches a known role and `Score.Builder` doesn't
    yet have a creator with that role, add it. Otherwise ignore.
  - Add a `KNOWN_CREATOR_ROLES` constant (Set of lowercase strings).
- Keep the deduplication behaviour inside the builder or via a helper
  `boolean hasCreatorRole(String)` on the builder (add if convenient).

### Step 3 — Writer: emit `<identification>`
- `sheetmusic4j/core/src/main/java/com/sheetmusic4j/core/musicxml/MusicXmlWriter.java`
  `writeScore` — right after the optional `<work>` block, if
  `score.creators()` is non-empty, emit:
  ```xml
  <identification>
    <creator type="composer">…</creator>
    <creator type="lyricist">…</creator>
  </identification>
  ```
  using existing `IndentingWriter` helpers.

### Step 4 — Engraving: TextPlacement.Category + creator rows
- `sheetmusic4j/engraving/src/main/java/com/sheetmusic4j/engraving/TextPlacement.java`
  - add `public enum Category { TITLE, SUBTITLE, CREATOR }`
  - add `Category category` as a new record component
  - add a canonical constructor + a compact backwards-compatible factory
    (`TextPlacement(String, double, double, double, Align)` delegates to
    the new one with `Category.TITLE`) so existing engraver code compiles
    unchanged before we migrate it in Step 5.
- `sheetmusic4j/engraving/src/main/java/com/sheetmusic4j/engraving/Engraver.java`
  - `layoutTitleBlock`: tag the two existing TextPlacements with
    `Category.TITLE` and `Category.SUBTITLE` respectively.
  - After the movement-title branch, add a new "creator rows" section:
    - Split `score.creators()` into two visual columns:
      - Right column: role in {`composer`, `arranger`, `transcriber`}
      - Left column: role in {`lyricist`, `poet`, `translator`}
      - Fallback: any other role → left column, appended below.
    - Compute row count = `max(rightRows.size(), leftRows.size())`.
    - For row `i` in `[0, rowCount)`:
      - `fontSize = gap * 1.1`
      - Right entry (if present): `x = systemWidth - rightMargin`,
        `align = RIGHT`, `y = currentY + fontSize`, category `CREATOR`.
      - Left entry (if present): same y baseline,
        `x = leftMargin`, `align = LEFT`, category `CREATOR`.
      - After emitting the pair, advance `y` by `fontSize * 1.4` and add
        that to `consumed`.

### Step 5 — Viewer: painter filter + SheetView property
- `sheetmusic4j/fxviewer/src/main/java/com/sheetmusic4j/fxviewer/ScorePainter.java`
  - add `private final EnumSet<TextPlacement.Category> hiddenCategories = EnumSet.noneOf(...)`.
  - add `setHiddenCategories(Set<Category>)` /
    `getHiddenCategories()`.
  - in `paint`, skip `TextPlacement`s whose `category()` is in
    `hiddenCategories` (still draw everything else). Comment noting that
    hidden text still consumes vertical space at the engraver.
- `sheetmusic4j/fxviewer/src/main/java/com/sheetmusic4j/fxviewer/ScoreRenderer.java`
  - Expose the painter's setter/getter so callers can configure it via the
    renderer (`setHiddenTextCategories(Set)`).
- `sheetmusic4j/fxviewer/src/main/java/com/sheetmusic4j/fxviewer/SheetView.java`
  - add
    `private final ObservableSet<TextPlacement.Category> hiddenCategories =
    FXCollections.observableSet(EnumSet.noneOf(TextPlacement.Category.class));`
  - `hiddenTextCategoriesProperty()` returns it; changes trigger
    `rebuild()`.
  - `rebuild()` calls `renderer.setHiddenTextCategories(hiddenCategories)`
    before rendering.

### Step 6 — Demo: View menu toggles
- `sheetmusic4j/fxdemo/src/main/java/com/sheetmusic4j/fxdemo/SheetDemoApp.java`
  - In `buildMenuBar`, add a nested "Text" menu (or submenu on the existing
    View menu) with `CheckMenuItem`s wired to
    `sheetView.hiddenTextCategoriesProperty()`:
    - "Show titles" (toggles `TITLE` + `SUBTITLE`)
    - "Show composer / lyricist" (toggles `CREATOR`)
  - Both checked by default.

### Step 7 — Tests
Add / extend tests in the existing style:

- `sheetmusic4j/core/src/test/java/com/sheetmusic4j/core/musicxml/MusicXmlReaderTest.java`
  - `readsComposerAndLyricistFromIdentification` — inline XML with
    `<identification><creator type="composer">…</creator>
    <creator type="lyricist">…</creator></identification>`. Assert
    `score.creators()` size, roles, and names.
  - `readsCreatorFromCreditWhenIdentificationAbsent` — inline XML with
    only `<credit><credit-type>composer</credit-type>
    <credit-words>…</credit-words></credit>`.
  - `identificationTakesPrecedenceOverCredit` — both present with
    different names for the same role; identification wins.
  - `readsCreatorsFromMozartSampleFile` (optional) — verifies against
    `fxdemo/src/test/resources/xmlsamples/MozaVeilSample.musicxml`
    (Mozart + Goethe from `<credit-words>` — only add if the reader
    already picks them up without extra file wiring).

- `sheetmusic4j/core/src/test/java/com/sheetmusic4j/core/musicxml/MusicXmlWriterTest.java`
  - `roundTripPreservesCreators` — build a Score with two `Creator`s,
    write + reparse, assert `score.creators()` equal.

- `sheetmusic4j/engraving/src/test/java/com/sheetmusic4j/engraving/EngraverTextBlockTest.java`
  - `emitsComposerRightAndLyricistLeft` — Score with two creators,
    assert two extra `TextPlacement`s with `Category.CREATOR`, correct
    `Align`, and expected x anchors (`systemWidth - rightMargin` and
    `leftMargin`).
  - `creatorRowsPushFirstStaffDownward` — analog of the existing title
    push-down test but toggled by creators only.
  - `titlePlacementsCarryCorrectCategories` — asserts the two existing
    title placements now carry `Category.TITLE` / `Category.SUBTITLE`.

- New test file
  `sheetmusic4j/fxviewer/src/test/java/com/sheetmusic4j/fxviewer/ScorePainterTextVisibilityTest.java`
  (headless, using the existing `AwtRenderSurface` if practical, else a
  minimal stub `RenderSurface` that records `drawText` calls):
  - `hidingCreatorCategorySkipsCreatorText` — build a `LayoutResult`
    with one TITLE and one CREATOR `TextPlacement`, call painter with
    `setHiddenCategories(EnumSet.of(CREATOR))`, assert only the title
    was drawn.
  - `hiddenCategoryStillDrawsStaves` — verify staves still draw.

## Reference Examples
- `Engraver.java:98` (`layoutTitleBlock`) — pattern the new creator rows
  follow (compute font size from `gap`, add to `texts`, accumulate the
  `consumed` advance, add trailing breathing space).
- `MusicXmlReader.java:170` (`parseDocument` switch + `readScorePart`) —
  pattern for adding new top-level element handlers.
- `MusicXmlReader.java:190` (`readScorePart`) — nested-element scanning
  pattern using START/END events; reuse for `readIdentification` and
  `readCredit`.
- `MusicXmlWriter.java:70` (`writeScore` work block) — pattern for the
  new `<identification>` block.
- `EngraverTextBlockTest.java:34` (`emitsWorkTitleAsCenteredText`) — the
  test style / minimal-score helper the new engraver tests follow.
- `ScorePainter.java:53` (`drawText`) — the render path we filter in
  Step 5.

## Verification
1. `cd sheetmusic4j && ./gradlew :core:test :engraving:test :fxviewer:test :fxdemo:test`
   — all pass, including the new tests above.
2. Existing round-trip test (`MusicXmlWriterTest.roundTripPreservesStructure`)
   still passes — no regression from the writer changes.
3. Manual smoke: launch `SheetDemoApp` with
   `fxdemo/src/test/resources/xmlsamples/MozaVeilSample.musicxml`.
   Verify:
   - "Wolfgang Amadeus Mozart" appears top-right below the title.
   - "Johann Wolfgang von Goethe" appears top-left below the title.
   - View → Text → uncheck "Show composer / lyricist" hides both;
     re-check restores them.
   - View → Text → uncheck "Show titles" hides the title/subtitle but
     leaves creators visible.
4. Manual smoke: open `xmlsamples/FaurReveSample.musicxml` and confirm
   "Gabriel Fauré" (right) and "Romain Bussine" (left) appear.

## Deferred / Follow-ups
- Reclaiming the vertical space of hidden text (requires plumbing the
  hidden set into `LayoutOptions` so the engraver can skip the block).
- Rendering `<rights>` (copyright line) as a footer — same mechanism,
  new category `RIGHTS`.
- Honoring `<credit-words>` positioning attributes when present (a
  positioned variant of `TextPlacement`).
