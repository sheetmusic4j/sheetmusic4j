id: 7cc5e91e-61fc-4ed2-a42c-1ee40f6b83a1
sessionId: 5de52ab8-5a10-4c3f-a6de-b9e9c136e08c
date: '2026-07-21T04:28:41.451Z'
label: 'Sheet4j: incrementally close FX↔OSMD reference gap via fixture ladder'
---
# Sheet4j: incrementally close FX↔OSMD reference gap via fixture ladder

## Goal

The previous plan (`Sheet4j--next-steps-for-correctness.md`) delivered the
infrastructure: OSMD-in-WebView reference generator, `DiagnosticComparator`,
HTML diff reports, and the first real glyphs (Bravura clefs, time-signature
digits, stems). The FX view is now visually recognisable — but per-measure
similarity vs the OSMD reference is still poor.

The obvious next question is *"which correctness gap should we close next?"*.
This plan turns that into a **fixture-driven ladder**: each step picks one
existing `xmlsamples/*.musicxml` file that isolates a single missing feature,
lands the code needed to make it engrave, regenerates its reference PNG, adds
it as a parameterised fixture, and ratchets the per-measure similarity
threshold. Every step is a self-contained PR.

## Current state (as of this plan)

- No reference PNGs are checked in
  (`fxdemo/src/test/resources/reference/generated/` contains only a README),
  so `CompareFxViewWithReferenceTest` currently **skips every fixture** via
  `Assumptions.assumeTrue(Files.isRegularFile(reference))`. There is no
  baseline to measure improvement against.
- `Engraver` already: proportional measure widths, per-digit time signatures,
  stem direction by staff position.
- `ScorePainter` + Bravura already draw: G/F/C clefs, note-heads,
  half/whole/quarter/eighth rests, time-signature digits.
- Still missing (in rough order of visual impact):
  1. Notehead / clef / rest **anchoring** — SMuFL glyphs are drawn with
     `x` at left edge, `y` at baseline (see
     `ScorePainter.drawSmuflIfAvailable` → `RenderSurface.drawSmuflGlyph`
     contract), but noteheads should be **center-anchored** and clefs need
     a specific y-origin per SMuFL "engravingDefaults". This one misalignment
     hurts every measure diff.
  2. **Flags** for unbeamed short notes (no flag drawn today → 6/8 & anything
     with eighths looks obviously wrong).
  3. **Beams** (needs `<beam>` parsing which is currently absent —
     `searchInWorkspace(beam)` returns 0 hits in `core/`).
  4. **Accidentals** — `Pitch.alter` is parsed but no glyph is emitted
     (`ScorePainter` has no `ACCIDENTAL_*` case).
  5. **Key signatures** — `Attributes.keySignature()` exists but is never
     consulted by `Engraver`.
  6. **Dotted notes** — `Note.dots()` is parsed, no augmentation-dot glyph
     is emitted.
  7. **Ties / slurs** — `tieStart`/`tieStop` parsed, never drawn.
  8. **Multi-staff parts** (piano grand staff) — `Engraver.layoutPart`
     emits one staff per part.
  9. **System / line breaking** — everything overflows to the right on one
     line.
  10. **Titles, part names, lyrics, dynamics** — no text rendering.

## Coder guideline

Never use runTask as this blocks the flow.

## Design

### Fixture ladder

Existing files under `fxdemo/src/test/resources/xmlsamples/` (from the
MusicXML example set) are ordered here from simplest to most complex.
Each step in the plan takes the *simplest fixture that still fails at the
current similarity threshold*, adds the minimum feature to make it pass,
and commits the regenerated PNG.

| Step | Fixture (path relative to `xmlsamples/`) | New feature required |
|------|-------------------------------------------|----------------------|
| 0    | `../c-major-scale.musicxml`               | (baseline; already covered) |
| 1    | `../c-major-scale.musicxml`               | SMuFL anchor/baseline fix |
| 2    | `Saltarello.musicxml` (6/8 eighths, C clef line 3) | flags on unbeamed eighths |
| 3    | `Saltarello.musicxml`                     | beam parsing + beam rendering |
| 4    | `Telemann.musicxml` (2 parts, key sig, accidentals) | key-signature engraving |
| 5    | `Telemann.musicxml`                       | in-measure accidentals |
| 6    | `Echigo-Jishi.musicxml` (dots, ties)      | augmentation dots |
| 7    | `Echigo-Jishi.musicxml`                   | ties |
| 8    | `Dichterliebe01.musicxml` (voice + piano grand staff) | multi-staff parts |
| 9    | `MozartPianoSonata.musicxml`              | system / line breaking |
| 10   | `MozartTrio.musicxml` / `ActorPreludeSample.musicxml` | multi-part alignment, rehearsal marks |

Later fixtures (Mahler, Fauré, Brahms, Debussy, Schubert) exist so that once
Step 10 is done we already have a large regression corpus. They are not
individually planned — the diagnostic report will tell us which measure to
fix next.

### Per-step recipe

Every step follows the same sequence so Coder can execute mechanically:

1. **Add fixture to `CompareFxViewWithReferenceTest.fixtures()`.**
2. **Regenerate the reference PNG** via the `refresh-references` profile
   (`mvn -pl fxdemo -Prefresh-references test`). Commit the PNG under
   `fxdemo/src/test/resources/reference/generated/<basename>.png`.
3. **Run the diagnostic test** — expect it to fail. Capture the numbers:
   which measures are the worst, which glyphs are `presentInReference=false`
   in the rendered image (missing on our side) vs `true` but with low
   similarity (drawn in the wrong place / wrong shape).
4. **Implement the smallest change** (engraver + painter) that improves
   the worst-measure similarity for that fixture.
5. **Re-run**; assert the fixture now clears
   `MIN_PER_MEASURE_SIMILARITY` (raise the threshold if it improves for
   *all* committed fixtures).
6. **Commit** as one PR titled `feat(engraving): <feature>` referencing
   the fixture.

### Global threshold ratcheting

`CompareFxViewWithReferenceTest.MIN_PER_MEASURE_SIMILARITY` is currently
`0.4` (overridable via `-Dsheetmusic4j.compare.measure.threshold=...`). At
each step we intentionally raise this value in the same PR that lands the
feature — provided *every* committed fixture still passes at the new
threshold. This turns the test into a monotonic quality ratchet.

If a step improves fixture X but regresses fixture Y, add a per-fixture
override (a `Map<String, Double>` in the test, keyed by fixture name) rather
than lowering the global bar.

## Implementation Steps

### Step 0 — Bootstrap: commit `c-major-scale.png` and unskip the test

Before touching the engraver we need the diagnostic pipeline to actually
report numbers. Today it just skips.

- Run `mvn -pl fxdemo -Prefresh-references test` locally to generate
  `fxdemo/src/test/resources/reference/generated/c-major-scale.png`.
- Commit the PNG plus, if OSMD requires it, the pinned OSMD bundle at
  `fxdemo/src/test/resources/reference/osmd/opensheetmusicdisplay.min.js`
  (should already be present from the previous plan; verify).
- Run the default `mvn -pl fxdemo test`. The parameterised
  `engravingMatchesReference[c-major-scale]` invocation must now execute
  (not skip). Capture the current per-measure similarity numbers from
  `target/sheet4j-diff/c-major-scale/report.html` and set
  `MIN_PER_MEASURE_SIMILARITY` to `min(perMeasureSim) - 0.05` so the test
  is a green baseline going forward. Document the observed baseline in
  a short comment above the constant.
- Add a comment above `fixtures()` documenting how to add a fixture:
  "drop `<basename>.png` into `reference/generated/`, add a line here".

### Step 1 — Fix SMuFL glyph anchoring

All SMuFL codepoints in `SmuflGlyphs.codepoint` are today drawn via
`RenderSurface.drawSmuflGlyph(codepoint, x, y, sizeHint)`. The AWT
implementation used by `HeadlessScoreImage` and the JavaFX implementation
almost certainly place the glyph at *left edge = x, baseline = y*. But:

- **Noteheads** need to be *center-anchored* on `(x, y)`. Fix in
  `ScorePainter#drawGlyph` case `NOTEHEAD_*` by shifting `x -= advance/2`
  and `y += yShift` before calling `drawSmuflGlyph`. Either introduce a
  new `RenderSurface.drawSmuflGlyphCentered(...)` default method, or
  compute the advance from a small anchor table hard-coded in
  `SmuflGlyphs` (SMuFL publishes them in `glyphBBoxes.json`; hard-code
  the handful we use — see Reference Examples).
- **Clefs** need per-clef y-anchor: G-clef curl on the G4 line (staff line
  2), F-clef dot on the F3 line (staff line 4), C-clef center on the
  clef's assigned line. Today `Engraver.layoutPart` places the clef at
  `staffY + staffHeight * 0.5` unconditionally — introduce a
  `clefAnchorLine(Clef)` helper and pass its y to the glyph.
- **Rests** need to sit at specific staff steps (whole rest hangs from
  line 4-from-top, half rest sits on line 3, quarter rest centered).
  Adjust `Engraver.placeElement` for `Rest`.
- **Time digits** — currently placed at hard-coded `topY = staffY + gap`
  and `bottomY = staffY + gap * 3`. Verify these correspond to SMuFL
  `timeSig` origin; if not, adjust.

Files:
- `fxviewer/src/main/java/com/sheetmusic4j/fxviewer/ScorePainter.java`
- `fxviewer/src/main/java/com/sheetmusic4j/fxviewer/SmuflGlyphs.java`
  (add `bboxHalfWidth(Glyph)` / `yShiftFromBaseline(Glyph)` or an
  `Anchor` record).
- `fxviewer/src/main/java/com/sheetmusic4j/fxviewer/RenderSurface.java`
  (add centered variant, default delegates to existing method).
- `fxviewer/src/main/java/com/sheetmusic4j/fxviewer/CanvasRenderSurface.java`
  (or equivalent JavaFX surface — locate via `findFilesByPattern` for
  `Canvas*.java` under `fxviewer/`) and any AWT counterpart under
  `fxdemo/src/test/java/.../HeadlessScoreImage.java`.
- `engraving/src/main/java/com/sheetmusic4j/engraving/Engraver.java`
  (clef anchor y, rest anchor y).

Verification: `engravingMatchesReference[c-major-scale]` per-measure
similarity rises noticeably (target: > 0.6). Bump
`MIN_PER_MEASURE_SIMILARITY` to `0.55`.

### Step 2 — Draw flags on unbeamed short notes

Add `FLAG_8TH_UP`, `FLAG_8TH_DOWN`, `FLAG_16TH_UP`, `FLAG_16TH_DOWN`
to `engraving/Glyph.java`. SMuFL codepoints (add to `SmuflGlyphs`):

- `flag8thUp` = `U+E240`, `flag8thDown` = `U+E241`
- `flag16thUp` = `U+E242`, `flag16thDown` = `U+E243`

`Engraver.placeNote`:
- After emitting the stem, for `NoteType.EIGHTH` / `SIXTEENTH` /
  `THIRTY_SECOND` etc., emit a `FLAG_*_UP` or `FLAG_*_DOWN` placement at
  the stem's *tip* (opposite end of the notehead). Skip flags entirely
  when we later know the note is beamed (Step 3 will thread that flag
  through).

`ScorePainter.drawGlyph` gets `FLAG_*` cases that just delegate to
`drawSmuflIfAvailable` (no fallback — a missing flag is acceptable when
Bravura is absent).

Fixture: `Saltarello.musicxml` (6/8, all eighth notes, currently no
flags, no beams).

Add to `CompareFxViewWithReferenceTest.fixtures()`:
```java
Arguments.of("Saltarello", Paths.get("src/test/resources/xmlsamples/Saltarello.musicxml"))
```
Regenerate reference PNG, commit under `reference/generated/Saltarello.png`.

Verification: `engravingMatchesReference[Saltarello]` passes at threshold
`0.4`; `c-major-scale` still passes at its previous threshold.

### Step 3 — Beam parsing + beam rendering

- **Parse `<beam>`** in `core/musicxml/MusicXmlReader.java`. Extend
  `Note` (or introduce `Note.beam(): Optional<BeamInfo>`) with
  `{ number, state ∈ BEGIN|CONTINUE|END|FORWARD_HOOK|BACKWARD_HOOK }`.
  Extend `MusicXmlWriter` to round-trip. Add unit tests in
  `core/src/test/java/.../musicxml/MusicXmlReaderTest.java` mirroring
  the existing test structure.
- **Group beamed notes in the engraver.** After all note glyphs are
  placed for a measure, walk the note list and produce runs
  `[beginIdx..endIdx]` (matching `BEGIN…CONTINUE*…END`). Add a new
  glyph `Glyph.BEAM` with a start-and-end `(x, y)` pair — introduce
  `GlyphPlacement` variant carrying two anchor points, or a new
  `BeamPlacement(x1, y1, x2, y2, level)` and expose it on
  `StaffLayout.beams()`.
- **Painter:** `ScorePainter.drawStaff` iterates the new list, calls
  `surface.fillRect` (rotated / polygon) for each beam. For an MVP,
  draw an axis-aligned thick rectangle spanning the stem tips at the
  average y — good enough for the fixture, refine later.
- Skip the flag emission from Step 2 for notes participating in a beam.

Files:
- `core/src/main/java/com/sheetmusic4j/core/model/Note.java` (`beam(): Optional<Beam>`)
- new `core/src/main/java/com/sheetmusic4j/core/model/Beam.java` record
- `core/src/main/java/com/sheetmusic4j/core/musicxml/MusicXmlReader.java`
  (locate the note-parsing block around line 310 for `alter`)
- `core/src/main/java/com/sheetmusic4j/core/musicxml/MusicXmlWriter.java`
- `engraving/src/main/java/com/sheetmusic4j/engraving/StaffLayout.java`
  (add `List<BeamPlacement> beams()`)
- `engraving/src/main/java/com/sheetmusic4j/engraving/Engraver.java`
- `fxviewer/src/main/java/com/sheetmusic4j/fxviewer/ScorePainter.java`

Verification: on `Saltarello`, per-measure similarity climbs above `0.6`
(three beamed eighths per beat should now look like OSMD's).

### Step 4 — Key signatures

- Extend `Engraver.layoutPart` to, right after the clef and before the
  time signature, emit `Glyph.ACCIDENTAL_SHARP` / `ACCIDENTAL_FLAT`
  placements for each pitch dictated by
  `KeySignature.fifths()`. Standard order:
  - Sharps: F♯ C♯ G♯ D♯ A♯ E♯ B♯ (SMuFL: staff positions on treble
    clef: line 5, space 5, above line 5, space 4, line 4, space 3, line 3)
  - Flats:  B♭ E♭ A♭ D♭ G♭ C♭ F♭ (mirror positions).
  Positions differ for bass/alto/tenor — encapsulate in a
  `KeySignatureLayout.positions(Clef, KeySignature)` helper (new class
  under `engraving/`).
- Add SMuFL codepoints to `SmuflGlyphs`:
  - `accidentalSharp` = `U+E262`
  - `accidentalFlat` = `U+E260`
  - `accidentalNatural` = `U+E261`
  - `accidentalDoubleSharp` = `U+E263`
  - `accidentalDoubleFlat` = `U+E264`
- Add `ACCIDENTAL_*` values to `Glyph.java`.
- `ScorePainter.drawGlyph`: `ACCIDENTAL_*` → `drawSmuflIfAvailable`,
  with a text fallback (`#`, `b`, `n`).

Fixture: `Telemann.musicxml` — has a real key signature. Commit reference
PNG, add fixture entry.

Verification: key signature glyphs appear in the correct positions on the
diff report; per-measure similarity for `Telemann` clears the current
threshold.

### Step 5 — In-measure accidentals

Independently of key signature, MusicXML's `<accidental>` element (child
of `<note>`) tells the renderer to draw an accidental *this occurrence*.
Falls back to `Pitch.alter` when `<accidental>` is absent, per the
spec.

- Extend `Note` with `Optional<Accidental> displayedAccidental()`.
- Parse `<accidental>` in `MusicXmlReader` (around the same block that
  reads `<alter>`).
- `Engraver.placeNote`: if the note has a displayed accidental, emit an
  `ACCIDENTAL_*` glyph just to the left of the notehead
  (`x -= 1.5 * staffLineGap`). Reuse the codepoints/glyphs from Step 4.

Verification: measures in `Telemann` that carry in-line sharps/flats
now show them; per-measure similarity climbs further.

### Step 6 — Augmentation dots

`Note.dots()` is already parsed. In `Engraver.placeNote` emit a
`Glyph.AUG_DOT` (SMuFL `augmentationDot` = `U+E1E7`) `dots` times to the
right of the notehead, spaced by `~0.6 * staffLineGap`. Dot vertical
placement: on the same staff step as the note when the step is a space,
one half-step above when the step is a line (standard convention).

Fixture: `Echigo-Jishi.musicxml` (uses dotted rhythms).

### Step 7 — Ties

- `Engraver` post-pass over consecutive notes with the same pitch where
  the earlier has `tieStart` and the later has `tieStop`. Emit a
  `TiePlacement(x1, x2, y, direction)` on the staff (new list, mirror
  the beam plumbing from Step 3).
- `ScorePainter` draws each tie as a shallow quadratic curve using
  `RenderSurface.strokeQuadCurve(...)`. Add the new primitive to
  `RenderSurface` with an AWT implementation delegating to
  `Graphics2D.draw(QuadCurve2D.Double)` and a JavaFX implementation
  via `GraphicsContext.quadraticCurveTo`.

Fixture: `Echigo-Jishi.musicxml` also has ties. Confirm both features
are visible in the same report.

### Step 8 — Multi-staff parts (piano grand staff)

MusicXML represents a piano as one `<part>` whose `<attributes>` has
`<staves>2</staves>`, and each `<note>` carries a `<staff>` element.

- Extend `Attributes` with `int staves()` (default 1).
- Parse `<staff>` in `MusicXmlReader`; add `int staff()` to `Note`
  (default 1).
- `Engraver.layoutPart`: if `attributes.staves() == 2`, emit **two**
  `StaffLayout` instances (upper = treble clef, lower = bass clef by
  default; override with per-staff `<clef number="N">` elements).
  Route each note to the staff its `staff()` field indicates.
- Add a "brace" primitive (SMuFL `brace` = `U+E000`) between the two
  staves. Keep it a `strokeText` for now if that is simpler.

Fixture: `Dichterliebe01.musicxml` (voice + piano). Commit reference
PNG. This is the first fixture with more than one staff per part.

### Step 9 — System / line breaking

Current `Engraver.layoutPart` puts every measure on one row. Break into
systems when cumulative measure widths exceed
`options.systemWidth() - margins`:

- Introduce `SystemLayout` per-row wrap: each row is a
  `SystemLayout(x=0, y=…, width, staves=[...])`. Return a
  `LayoutResult(List<SystemLayout> systems, width, height)`.
- Each system re-emits the clef (and key signature when it changes) at
  its start.
- Bump `LayoutResult.height` accordingly; `HeadlessScoreImage.render`
  and the on-screen canvas already resize by that value.

Fixture: `MozartPianoSonata.musicxml` (longer than one line at the
1000×300 test viewport).

### Step 10 — Multi-part alignment + rehearsal marks

Once we have multiple parts each with their own systems, the parts must
share x-positions per measure (measure `k` of part 1 aligns with
measure `k` of part 2). Add a pre-pass that computes the *maximum*
per-measure width across all parts before laying out.

Fixture: `MozartTrio.musicxml` (three instruments, three staves).

### Cross-cutting: threshold + per-fixture overrides

- After each successful step, edit `CompareFxViewWithReferenceTest`:
  - raise `MIN_PER_MEASURE_SIMILARITY`, **or**
  - add a `Map.of("Telemann", 0.5, "Saltarello", 0.55, ...)` per-fixture
    override loaded from a simple JSON at
    `fxdemo/src/test/resources/reference/thresholds.json`.
- Keep every previously committed fixture green; if you would need to
  *lower* any threshold to land a step, that is a regression: fix the
  regression first.

### Cross-cutting: Diff tab in `SheetDemoApp`

The interactive Diff tab from the previous plan (Step 3 there) is
optional but hugely accelerates this fixture ladder. If it is not yet
implemented, land it early in this round (before Step 2 here) so each
subsequent step is easier to debug visually:

- Load a MusicXML file in `SheetDemoApp`.
- Button "Generate reference" runs `WebViewReferenceRenderer` on-demand.
- Third pane in the `SplitPane` renders the resulting HTML diff report
  in an embedded `WebView`.

## Reference Examples

- `sheetmusic4j/engraving/src/main/java/com/sheetmusic4j/engraving/Engraver.java:65-95`
  — the current place-elements loop; every step in this plan hangs new
  glyphs off `placeElement` / `placeNote`.
- `sheetmusic4j/engraving/src/main/java/com/sheetmusic4j/engraving/Engraver.java:100-140`
  — `computeMeasureWidths`, the model for a *pre-pass* over the part.
  Step 10's cross-part width alignment follows the same idiom, one level up.
- `sheetmusic4j/fxviewer/src/main/java/com/sheetmusic4j/fxviewer/SmuflGlyphs.java:38-70`
  — the codepoint switch; extend it whenever a new glyph is added.
- `sheetmusic4j/fxviewer/src/main/java/com/sheetmusic4j/fxviewer/ScorePainter.java:59-105`
  — the switch on `Glyph`; every new case follows the "try SMuFL, else
  primitive fallback" pattern already established for rests.
- `sheetmusic4j/core/src/main/java/com/sheetmusic4j/core/musicxml/MusicXmlReader.java`
  around line 310 — pattern for extending the note reader with new
  sub-elements (`<beam>`, `<accidental>`, `<staff>`).
- SMuFL bounding-box reference for Step 1's anchor tables:
  https://raw.githubusercontent.com/w3c/smufl/gh-pages/metadata/glyphnames.json
  — commit only the small subset we consume as constants in
  `SmuflGlyphs`, not the whole file.
- Existing diff report from Step 0 will be at
  `sheetmusic4j/fxdemo/target/sheet4j-diff/c-major-scale/report.html`
  after the first `mvn -pl fxdemo test` run. Every subsequent step should
  screenshot the report's per-measure table for the PR description.

## Verification

**Global (every step)**
- `mvn -pl fxdemo test` (default surefire) passes.
- `mvn -pl fxdemo -Prefresh-references test` runs cleanly on the
  developer's machine and produces byte-stable PNGs between two
  consecutive invocations (deterministic OSMD render).

**Per-step**
- Step 0: `engravingMatchesReference[c-major-scale]` is no longer
  skipped; passes at whatever baseline threshold we settle on.
- Step 1: baseline threshold ratchets to ≥ 0.55; every existing
  fixture passes.
- Step 2: `engravingMatchesReference[Saltarello]` is added and passes
  at ≥ 0.4.
- Step 3: `Saltarello` passes at ≥ 0.6; unit test for beam round-trip
  in `MusicXmlReaderTest` is green.
- Step 4: `engravingMatchesReference[Telemann]` added, passes at ≥
  0.4.
- Step 5: `Telemann` passes at ≥ 0.55.
- Step 6: `engravingMatchesReference[Echigo-Jishi]` added, passes at
  ≥ 0.4.
- Step 7: `Echigo-Jishi` passes at ≥ 0.55.
- Step 8: `engravingMatchesReference[Dichterliebe01]` added; two
  staves are detected by `StaffDetector` in the report.
- Step 9: `engravingMatchesReference[MozartPianoSonata]` added;
  reference PNG shows multiple systems and Sheet4j does likewise.
- Step 10: `engravingMatchesReference[MozartTrio]` added; report shows
  three vertically stacked staves with aligned barlines.

**Regression safety net**
- Every previously landed fixture stays green under the new global
  threshold (or a fixture-specific override, if unavoidable) — no step
  is allowed to *lower* an existing bar.

## Open questions for the user

1. Are the OSMD PNGs deterministic enough for CI? (Font rendering across
   JDKs / OSes can flake at pixel level.) If not, we may want to store a
   pre-hashed *quantised* reference or run the compare test only on
   Linux/JDK 26.
2. Do we want each step to be its own PR, or a stacked series where the
   PR title just calls out the fixture that turned green?
3. Is there any preference between the "brace" (piano grand staff,
   Step 8) being drawn as a Bravura glyph vs a hand-drawn Bezier curve
   in the JavaFX layer?
