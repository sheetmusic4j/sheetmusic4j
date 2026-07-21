id: 87c51f01-5752-45eb-a33e-d45a1b339467
sessionId: f34e523e-a93f-4dd5-9628-618b97d281dc
date: '2026-07-21T07:54:05.985Z'
label: 'Sheet4j: fixture-driven FX improvement using sibling PDFs (v2)'
---
# Sheet4j: fixture-driven FX improvement using sibling PDFs (v2)

## Goal

Continue closing the gap between the Sheet4j FX rendering and each fixture's
sibling PDF, using the diagnostic pipeline that already exists
(`CompareFxViewWithReferenceTest` → `DiagnosticComparator` → `DiffReportWriter`).
Concretely:

1. Add the currently-uncovered fixtures that have sibling PDFs
   (`melodymatrix/do-re-mi.mxl` is the trigger case) to the test ladder.
2. Land the two features the previous plan still owes — **system / line
   breaking** and **multi-part measure alignment** — so long scores (like
   `do-re-mi`) actually flow across multiple systems instead of a single
   overflowing line.
3. Fix the "first part not correctly aligned" symptom, which comes from
   the fixed left-margin + clef/keysig/timesig block at the head of every
   staff being drawn out of proportion to the SMuFL glyph widths.
4. Run the per-fixture diff report, iterate file-by-file, and ratchet
   per-fixture similarity thresholds so no fixture regresses.

## Concrete trigger

`fxdemo/src/test/resources/melodymatrix/do-re-mi.mxl` renders in the FX
view as a single overflowing row, while the sibling
`melodymatrix/do-re-mi.pdf` has **one page with several systems**. The
first measure block (clef + key signature + time signature + first
notes) is also visibly mis-aligned. That fixture is not even in the
current test ladder yet.

## Current state (verified)

- `CompareFxViewWithReferenceTest.fixtures()` lists 12 `xmlsamples/*` PDFs
  and hard-codes `HEIGHT = 300` when calling
  `HeadlessScoreImage.render(score, WIDTH, HEIGHT)`. That fixed height
  is too small for a multi-system layout.
- `Engraver.layout(...)` still returns **one `SystemLayout` per score**
  containing all staves back-to-back (see
  `Engraver.java:47-64` — `SystemLayout system = new SystemLayout(0, 0,
  options.systemWidth(), staves);`). No line breaking exists.
- Multi-staff parts (Step 8 of the old plan) are already in via
  `Engraver.determineStaveCount` + `filterElementsForStaff`.
- `MIN_PER_MEASURE_SIMILARITY = 0.2` (overridable). Test asserts
  per-measure similarity + relaxed staff-height ratio; there is no
  per-fixture threshold override yet.
- No `melodymatrix/` entry in `fixtures()`, and `PdfSibling.existingPathFor`
  works for any XML/MXL as long as the sibling `.pdf` sits next to it.
- `HeadlessScoreImage.render(score, w, h)` uses a fixed `TYPE_INT_RGB`
  BufferedImage sized `w × h`. `SheetView` on the other hand already
  auto-sizes its canvas to `layout.height()`.

## Coder guideline

Never use `runTask` — it blocks the flow. Prefer the diagnostic HTML
report (`target/sheet4j-diff/<fixture>/report.html`) as the feedback loop.

## Design

### A. Fixture ladder — sibling PDFs only

Every fixture is a `(basename, xmlPath, expectedPageCount)` triple that
matches the sibling PDF committed under `src/test/resources/`. New
entries added by this plan:

| basename            | xmlPath                                          | expected pages |
|---------------------|--------------------------------------------------|----------------|
| `do-re-mi`          | `melodymatrix/do-re-mi.mxl`                       | (probe once, then commit) |

Any additional `.mxl`/`.musicxml` files landing under `melodymatrix/` or
`basic/` in the future with a sibling PDF should be added the same way.

### B. Auto-sized rendering canvas + viewport-driven demo width

`HeadlessScoreImage.render(score, width, height)` currently truncates
everything below `height`. Once line breaking is in, the layout grows
vertically. Fix the API so it returns a canvas sized to the layout:

- Add an overload `render(Score score, int width)` that:
  1. Runs the engraver with `systemWidth = width` and default margins.
  2. Sizes the BufferedImage to
     `(width, (int)Math.ceil(layout.height()))` with a small padding.
- The existing `(score, width, height)` overload treats `height` as a
  *minimum*, growing to `layout.height()` when needed.
- Update `assertStaffBoundingBoxes` in
  `CompareFxViewWithReferenceTest` to iterate over *all* rendered
  staff bounding boxes now that they no longer get clipped off the
  canvas.

**`SheetDemoApp` uses viewport-driven width (user decision).** The FX
view reflows to whatever width the ScrollPane viewport currently
offers:

- In `SheetDemoApp.buildContent()`, bind `sheetView.systemWidthProperty()`
  to `scoreScroll.viewportBoundsProperty()`'s width (minus scrollbar
  padding). `SheetView` already re-engraves on `systemWidth` change
  (see `SheetView.rebuild()`), so no other plumbing is needed.
- For the "Compare against PDF" tab: keep the diff comparison at the
  test's fixed 1000 px so demo and CI produce comparable diagnostic
  reports. Only the top "Sheet4j rendering" pane reflows.
- Update the Debug pane to display the current effective
  `systemWidth` so it's visible when the user resizes the window.

Note the resulting divergence: interactive rendering follows the
viewport; the CI `CompareFxViewWithReferenceTest` keeps the fixed
1000 px width so per-fixture similarity numbers stay comparable
across runs. This is intentional.

### C. System / line breaking (Step 9 of the old plan)

- In `Engraver.layoutStaff`, once cumulative measure widths would
  exceed `systemWidth - leftMargin - rightMargin - headerWidth`, close
  the current row and start a new one at `cursorX = leftMargin`,
  `y += staffHeight + staffSpacing`.
- Refactor `Engraver.layout(...)` to emit **N `SystemLayout` instances**,
  one per row. Each new system:
  - Re-emits the clef (and current key signature, and current time
    signature only when it just changed).
  - Restarts `first = true` for the leading-glyph placement.
- Update `LayoutResult.height` to the accumulated bottom y.
- `ScorePainter.paint(...)` already iterates
  `layout.staves()`; verify it still works when several staves share
  the same x-range but stack vertically.

### D. Header alignment fix (root cause of "first part not aligned")

`Engraver.layoutStaff` currently uses `staffLineGap * 4` as the fixed
step from bar-start to first-note when there is no key/time signature,
and adds the digits/accidentals *after* setting that. This makes the
first note jump right when key/time signatures are present. Solution:

- Compute the header width *before* starting the measure loop
  (`clefAdvance + keySigAdvance + timeSigAdvance + trailingPad`) and
  advance `cursorX` (or `contentStart`) by exactly that amount.
- Include that header width in `computeMeasureWidths` so the *rest of
  the first measure* isn't squeezed. Pass a "leading reserved width"
  parameter into `computeMeasureWidths` and subtract it from
  `totalWidth` before distributing weights.
- Use `SmuflGlyphs.halfAdvanceWidth`-style values for the actual
  header widths rather than the current constants `staffLineGap * 4`
  and `staffLineGap * 1.4`, so the header block matches what
  `ScorePainter` actually paints.

### E. Multi-part measure alignment (Step 10 of the old plan)

Once systems exist, measure `k` in part 1 must be x-aligned with
measure `k` in part 2 (and with the shared barline crossing both
staves for grand-staff parts). Approach:

- Pre-pass across all parts: compute `List<Double> maxMeasureWidths` by
  taking the max measure width across all parts for each measure index.
- `layoutStaff` uses that shared list instead of its own
  `computeMeasureWidths` output.
- For the grand-staff case (Step 8 already implemented), the same
  shared widths already apply since the two staves come from the same
  `Part` — no change needed there.

### F. Global threshold ratchet toward 95%

One single global bar for `MIN_PER_MEASURE_SIMILARITY`, no per-fixture
overrides. The **target is 0.95** — every fixture must clear the same
number. Because we start at `0.2`, each step raises the bar only as far
as *every* committed fixture can clear it. Concretely:

- After each step, run the whole ladder and set
  `MIN_PER_MEASURE_SIMILARITY` to
  `floor(min(perMeasureSim across all fixtures) * 100) / 100`
  (i.e. the highest 2-decimal value below the current weakest measure).
- If a step lands its target fixture but *lowers* the min across the
  ladder, the step is a regression — fix the regression before merging.
- Ratcheting continues until the global bar reaches `0.95`. That is
  the definition of "done" for this plan.
- The system property override `-Dsheetmusic4j.compare.measure.threshold`
  stays, so contributors can temporarily loosen the bar locally
  while iterating.

### G. Report tweaks

`DiffReportWriter` already writes `report.html`; add a small
"per-system" line under the summary showing:

```
Rendered systems: <n> · Reference bands: <m>
```

(inferred from `StaffDetector.detect(reference).size()` and
`layout.systems().size()`). This is the fastest way to eyeball whether
line breaking is actually kicking in.

## Implementation Steps

### Step 1 — Add `do-re-mi` fixture and grow the canvas

- `sheetmusic4j/fxdemo/src/test/java/com/sheetmusic4j/fxdemo/CompareFxViewWithReferenceTest.java`:
  - Add
    `Arguments.of("do-re-mi", Paths.get("src","test","resources","melodymatrix","do-re-mi.mxl"), <pages>)`
    to `fixtures()`. Probe the page count once (open the PDF with
    `PdfRasterizer.pageCount`) and hard-code the resulting integer.
  - Replace the fixed `HEIGHT = 300` code path with a call to the new
    auto-sizing overload of `HeadlessScoreImage.render`.
- `sheetmusic4j/fxdemo/src/main/java/com/sheetmusic4j/fxdemo/HeadlessScoreImage.java`:
  - Add `public static BufferedImage render(Score score, int width)` that
    lays out first, sizes the image to `layout.height()` (with a small
    top/bottom pad and a floor of 300 so ink-ratio doesn't collapse
    for tiny scores), then paints.
  - Keep the existing `(score, width, height)` overload delegating to
    the new one when the layout is smaller than `height`, and
    otherwise growing to `layout.height()`.
- `sheetmusic4j/fxdemo/src/main/java/com/sheetmusic4j/fxdemo/SheetDemoApp.java`:
  - Diff tab: keep `HeadlessScoreImage.render(score, DIFF_WIDTH, DIFF_HEIGHT)`
    but let `DIFF_HEIGHT` be a minimum-only hint (grow to
    `layout.height()`).
  - Top "Sheet4j rendering" pane: bind
    `sheetView.systemWidthProperty()` to the ScrollPane's viewport
    width so the score reflows as the window resizes. Sample idiom:
    ```java
    scoreScroll.viewportBoundsProperty().addListener((obs, o, n) ->
        sheetView.setSystemWidth(Math.max(200, n.getWidth() - 4)));
    ```
    Trigger an initial value once the scene is shown.
  - Debug pane: append a line
    `System width (viewport): <n>` alongside the score file line.

Verification: `mvn -pl fxdemo test` — the `do-re-mi` invocation now
executes; it may fail on measure similarity but the assumeTrue-skip is
gone. Capture the baseline similarity from
`target/sheet4j-diff/do-re-mi/report.html`. Launching `SheetDemoApp`,
opening `do-re-mi.mxl`, and dragging the window narrower/wider must
cause the score to re-flow across a different number of systems.

### Step 2 — Fix the first-measure header alignment

- `sheetmusic4j/engraving/src/main/java/com/sheetmusic4j/engraving/Engraver.java`:
  - Extract a `headerAdvance(Clef, KeySignature, TimeSignature, LayoutOptions)`
    helper that returns the horizontal space the *painter* needs for
    the clef + key signature + time signature block.
  - Replace the ad-hoc `contentStart = cursorX + options.staffLineGap() * 4`
    in `layoutStaff` with `contentStart = cursorX + headerAdvance(...)`.
  - Have `computeMeasureWidths` receive an additional
    `leadingReservedWidth` and subtract it from `totalWidth` when
    distributing widths, so the first measure's *notes* still get
    their fair share of horizontal space.
- Verify against `do-re-mi` and re-run the report; expect the very
  first measure's per-measure similarity to jump.

### Step 3 — System / line breaking

- `sheetmusic4j/engraving/src/main/java/com/sheetmusic4j/engraving/Engraver.java`:
  - Change `layoutStaff` to close-and-restart a row when the pending
    measure won't fit. Emit intermediate `StaffLayout` rows into a
    per-part `List<StaffLayout>` and produce one `SystemLayout` per row.
  - Re-emit the clef (and re-emit key signature only when it changes;
    always re-emit time signature? standard practice = only on change).
  - Add a `LayoutOptions.systemPadding` if needed for inter-system
    vertical spacing; otherwise reuse `staffSpacing`.
- `sheetmusic4j/engraving/src/main/java/com/sheetmusic4j/engraving/LayoutResult.java`:
  - No API change needed; `staves()` already flat-maps across systems.
  - Verify `height` reflects the last system's bottom.
- Cover with a new unit test in
  `engraving/src/test/java/com/sheetmusic4j/engraving/EngraverSystemBreakTest.java`
  that constructs a synthetic 20-measure score with narrow
  `systemWidth` and asserts
  `layout.systems().size() > 1` and
  `layout.staves().stream().map(StaffLayout::y).distinct().count() > 1`.
- Re-run `do-re-mi`: report should now show multiple systems, and
  per-measure similarity for the *first* line should climb noticeably.

### Step 4 — Multi-part measure alignment

- `Engraver`:
  - Pre-pass across all parts before laying out any of them: compute
    a shared `List<Double> maxMeasureWidths` (one entry per unified
    measure index).
  - Thread that list through `layoutPart` → `layoutStaff` in place of
    the per-part width computation.
  - Skip empty-part gaps: measures a part doesn't have (or has
    filtered out for its staff) still consume the shared width so
    barlines stay aligned across parts.
- Add a two-part fixture unit test verifying that the barline x of
  measure `k` in part 1 equals the barline x of measure `k` in part 2
  (use `Dichterliebe01.musicxml` — already in the fixture ladder).

### Step 5 — Painter tweaks for multi-system output

- `sheetmusic4j/fxviewer/src/main/java/com/sheetmusic4j/fxviewer/ScorePainter.java`:
  - No structural change expected — `paint` already loops over
    `layout.staves()`. Sanity-check that barlines drawn at
    `measure.right()` don't bleed into the neighbour system.
  - If systems ever share y with each other (they shouldn't after
    Step 3), fail loudly — add an assertion that adjacent
    `StaffLayout`s do not overlap.
- Bravura glyph anchors: rerun the report on all fixtures and, for any
  fixture where the report shows many `presentInReference=false`
  entries clustered at the *left* of every staff, tighten the SMuFL
  half-advance-width entries in
  `sheetmusic4j/fxviewer/src/main/java/com/sheetmusic4j/fxviewer/SmuflGlyphs.java`.

### Step 6 — Ratchet the global threshold

- `sheetmusic4j/fxdemo/src/test/java/com/sheetmusic4j/fxdemo/CompareFxViewWithReferenceTest.java`:
  - Keep the single constant `MIN_PER_MEASURE_SIMILARITY` and raise it
    (default in source) after each fixture sweep to the highest
    2-decimal value that *all* fixtures still clear. Update the
    comment above the constant to record the ratcheting history
    (`0.20 -> 0.30 -> 0.45 -> ...`).
- If any previously-green fixture would force the global bar back
  down, fix the regression before merging.
- The plan is complete when `MIN_PER_MEASURE_SIMILARITY = 0.95` and
  every fixture is green.

### Step 7 — DiffReportWriter: "systems / bands" line

- `sheetmusic4j/fxdemo/src/main/java/com/sheetmusic4j/fxdemo/reference/DiffReportWriter.java`:
  - Extend `write(...)` to also accept `int renderedSystems` (or fetch
    it from `diagnostic.renderedStaves()` by grouping distinct `y`
    bands). Add one summary line under the existing overall-similarity
    row.
- Update both callers (`CompareFxViewWithReferenceTest` and
  `SheetDemoApp.generateReferenceAsync`) to pass
  `layout.systems().size()`.

### Step 8 — Sweep every fixture, one file at a time

For each fixture in the ladder, in order:

1. Run the test, open `target/sheet4j-diff/<fixture>/report.html`.
2. Note the top 3 worst measures and the missing-glyph clusters
   flagged as `presentInReference=false`.
3. Apply the smallest change that lifts the worst measure. Common
   remedies (all already scaffolded — this is fine-tuning, not new
   feature work):
   - Slight adjustments to `SmuflGlyphs.halfAdvanceWidth` for the
     glyph classes clustered at the bad x.
   - Tightening the `headerAdvance` heuristics if the first measure
     is always the loser.
   - Increasing/decreasing `LayoutOptions.measureMinWidth` if measures
     are consistently too dense or too sparse relative to PDF.
4. Bump the per-fixture threshold. Commit. Move to the next fixture.

Fixtures to sweep, in ascending complexity (page counts confirmed in
the existing test):

1. `do-re-mi` (melodymatrix, primary trigger)
2. `Echigo-Jishi` (1 page, simple)
3. `MozaChloSample`, `MozaVeilSample`, `BrookeWestSample` (1 page each)
4. `BeetAnGeSample`, `BrahWiMeSample`, `SchbAvMaSample` (1 page, more
   ornamentation)
5. `DebuMandSample`, `FaurReveSample`, `MahlFaGe4Sample` (1 page,
   complex textures)
6. `Dichterliebe01` (2 pages, voice + piano grand staff — first true
   multi-part multi-page test)
7. `ActorPreludeSample` (4 pages, large orchestral score — final boss)

Each row is its own commit; the PR title is
`feat(engraving): raise <fixture> to <threshold>`.

## Reference Examples

- `sheetmusic4j/engraving/src/main/java/com/sheetmusic4j/engraving/Engraver.java:47-64`
  — where `SystemLayout` is currently built as a *single* system;
  Step 3 turns this loop into "one system per row".
- `sheetmusic4j/engraving/src/main/java/com/sheetmusic4j/engraving/Engraver.java:130-180`
  — `layoutStaff`'s measure loop, where the row-break test needs to
  live (after `measureWidths.get(idx)` is known, before advancing
  `cursorX`).
- `sheetmusic4j/engraving/src/main/java/com/sheetmusic4j/engraving/Engraver.java:243-274`
  — `computeMeasureWidths`. Step 2's `leadingReservedWidth` parameter
  is added here; Step 4 replaces the per-part variant with a shared
  pre-pass over all parts.
- `sheetmusic4j/fxviewer/src/main/java/com/sheetmusic4j/fxviewer/SheetView.java:102`
  — the on-screen canvas already sizes itself to `layout.height()`;
  Step 1 makes `HeadlessScoreImage` do the same.
- `sheetmusic4j/fxdemo/src/main/java/com/sheetmusic4j/fxdemo/reference/DiagnosticComparator.java`
  — already produces per-measure MeasureDiff + per-glyph
  GlyphPresence. Nothing structural to change; Step 7 only adds a
  system count to the report.
- `sheetmusic4j/fxdemo/src/test/java/com/sheetmusic4j/fxdemo/CompareFxViewWithReferenceTest.java`
  — parameterized test template. Every new fixture follows the same
  `Arguments.of("<basename>", <xmlPath>, <expectedPages>)` pattern.

## Verification

**After Step 1:**
- `mvn -pl fxdemo test -Dtest=CompareFxViewWithReferenceTest` runs the
  `do-re-mi` invocation (no `assumeTrue` skip).
- `target/sheet4j-diff/do-re-mi/report.html` exists and shows the
  Sheet4j output *taller than 300 px* when the score is long enough.

**After Step 2:**
- On `do-re-mi`, the per-measure similarity for measure 1 rises above
  the current worst-measure baseline (capture the numbers from the
  report and quote them in the PR).

**After Step 3:**
- `Engraver.layout(<long score>).systems().size() > 1` (new unit test
  in `EngraverSystemBreakTest`).
- Every existing fixture still passes; the `do-re-mi` report shows
  multiple visually separated rows of staff.

**After Step 4:**
- Barline x-coords for `Dichterliebe01`'s two parts are equal on a
  per-measure basis (new assertion in
  `EngraverAlignmentTest.multiPartBarlinesAlign()`).

**After Step 5:**
- All 13 (12 existing + `do-re-mi`) fixtures pass `mvn -pl fxdemo test`
  under whatever per-fixture thresholds Step 6 defines.

**After Step 6:**
- `MIN_PER_MEASURE_SIMILARITY` in
  `CompareFxViewWithReferenceTest` has been raised to the highest
  2-decimal value that all fixtures clear. The comment above the
  constant records the ratcheting history.
- Terminal condition: `MIN_PER_MEASURE_SIMILARITY = 0.95` with every
  fixture green.

**Regression safety net (every step):**
- Any fixture whose per-measure similarity would force the global bar
  back down is a regression; fix it before landing the step.
- The HTML report is committed as a screenshot on the PR description
  for each fixture that crosses a new threshold.

## Decisions locked in

1. **Canvas policy in `SheetDemoApp`:** viewport-driven. The FX view
   reflows to whatever width the ScrollPane viewport currently offers;
   `CompareFxViewWithReferenceTest` keeps the fixed 1000 px width so
   CI similarity numbers stay comparable. See design section B.
2. **Threshold model:** one single global
   `MIN_PER_MEASURE_SIMILARITY`, ratcheted step by step until it
   reaches **0.95** with every fixture green. No per-fixture
   overrides. See design section F.
3. **Fixture inventory:** only `melodymatrix/do-re-mi.mxl` joins the
   ladder in this plan; no additional sibling PDFs are available at
   this time. If more `.pdf` companions appear later, they get added
   the same way (`Arguments.of(...)` in `fixtures()`).