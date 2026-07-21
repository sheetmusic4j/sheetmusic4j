id: d55d3996-0ebb-416e-84e9-3da6ab4682cc
sessionId: 61815e5e-52d3-4be5-8803-05833098b48c
date: '2026-07-20T11:56:20.708Z'
label: 'Sheet4j: next steps for correctness + WebView-based reference renderer'
---
# Sheet4j: next steps for correctness + WebView-based reference renderer

## Goal

Push Sheet4j from its current "smoke-test" state (staff lines + placeholder letters
for clefs/time signatures) toward a rendering that can actually be *evaluated*
against a trusted reference — and turn `CompareFxViewWithPdfTest` from a single
opaque similarity number into a **step-by-step diagnostic harness** that says
*where* our engraving differs.

Concretely we want to:

1. Automate the production of expected images by embedding a public,
   trusted MusicXML renderer (**OpenSheetMusicDisplay**) in a JavaFX
   `WebView` — mirroring what `WebViewScreenshotGenerator` does in the
   Lottie4J project.
2. Replace the current whole-image similarity check with a staged comparison
   (page → system → staff → measure → glyph) that reports *what* differs.
3. Fix the most visible correctness gaps in the engraver/painter so the
   comparison is meaningful.

## Current state (as observed)

- `fxviewer/ScorePainter` still draws the letter `G` / `F` / `C` for clefs,
  the literal string `"4/4"` for every time signature, and `\u00A6` for rests.
  There are no real SMuFL glyphs, no stems/beams for durations, no accidentals,
  no key signatures, no dynamics, no lyrics, no slurs/ties.
- `Engraver` spaces measures evenly by count (ignores rhythmic content), uses
  a single system per part, and has no system breaking.
- `CompareFxViewWithPdfTest` compares the whole rendering as a single
  256×256 gray blur (`ImageSimilarity`), so it only tells you "different"
  vs "same-ish" — never *where*.
- No reference images are checked in
  (`fxdemo/src/test/resources/reference/` is empty), and the current design
  requires the user to manually export a PDF from their notation software.
- The demo already displays a PDF sibling side-by-side (`SheetDemoApp`,
  `PdfSibling`) — that is a good manual QA, but not usable as a CI oracle.

## Design

### A. Reference generation via WebView + OpenSheetMusicDisplay

Mirror the Lottie4J approach:

- Lottie4J: JavaFX `WebView` loads `lottie-web`, feeds it the animation JSON,
  the app renders each frame in-browser, `WritableImage snapshot(...)` +
  `SwingFXUtils.fromFXImage` writes each frame to disk. That set of PNGs then
  becomes the ground truth the native FX renderer is diff'd against.
- Sheet4j equivalent: `WebView` loads a small local HTML page bundling
  **OpenSheetMusicDisplay (OSMD)**. The page exposes a JS bridge
  (`window.osmdBridge.render(xmlString, options)`) that:
  1. renders the MusicXML into an SVG container of a known pixel size,
  2. signals `readyState = "done"` back to Java when layout is finished.
  Java then calls `WebView.snapshot(...)` and writes the PNG to
  `fxdemo/src/test/resources/reference/generated/<sample>.png`.

Advantages over the current PDF-companion approach:

- Same MusicXML feeds both our engraver and the reference — no manual export.
- Deterministic: OSMD version is pinned, page size is fixed, snapshot is
  byte-stable enough for CI (allowing a small tolerance).
- Any sample under `fxdemo/src/test/resources/xmlsamples/` becomes an
  automatic regression fixture.

### B. Step-by-step comparison harness

Replace the single "one gray blur" check with an incremental pipeline:

1. **Sanity** — engraving is non-empty (ink ratio, already present).
2. **Bounding-box overlap** — Sheet4j reports the bbox of every
   `StaffLayout`; on the reference image, detect the staves via horizontal
   projection (five equally-spaced dark rows). Compare positions/heights.
3. **Per-measure crop diff** — using `LayoutResult.staves()[i].measures()[j]`,
   crop the corresponding rectangle from *both* rendered and reference images
   (the reference gets the same relative rectangle scaled to its own bbox).
   Report similarity per measure and dump the worst-N as side-by-side PNGs.
4. **Per-glyph presence** — for each `GlyphPlacement`, sample a small window
   around `(x, y)` in the reference image; assert the window is not blank
   (i.e. "something is drawn there too"). This is the poor-man's structural
   check that catches missing accidentals, missing rests, etc.
5. **Structural summary report** — write a markdown/HTML report to
   `target/sheet4j-diff/<sample>/report.html` with the two images side by
   side, per-measure similarity numbers, and highlighted mismatch boxes.

The point is not pixel-perfect equivalence (we will never match OSMD's font
metrics), but to **localize** regressions so we know which measure/glyph
introduced them.

### C. Correctness roadmap for the renderer (in priority order)

These are the fixes that will make the diff meaningful. Each is a follow-up
plan; listed here so we don't lose track:

1. Draw a real clef glyph, not the ASCII letter. Use SMuFL Bravura from
   `fxviewer` classpath resources.
2. Render time signature as two stacked digits from the actual `TimeSignature`
   values (not always `4/4`).
3. Draw stems with correct direction (down if step above middle line, up
   otherwise) and correct length.
4. Draw flags for eighth/sixteenth notes (or beams when adjacent).
5. Draw accidentals from `Note.accidental()` / key signature.
6. Draw a real rest glyph per duration.
7. Space notes proportionally to duration (not evenly per measure).
8. Handle key signatures (`Attributes.keySignature()`).
9. Wrap systems when a line is full instead of squishing everything into one
   system.

Only 1, 2, 3, 6, 7 are needed for the C-major-scale fixture to become
recognisable to the diff test.

## Implementation Steps

### Step 1 — Add the WebView-based reference generator

Create a new package `com.sheetmusic4j.fxdemo.reference` under
`fxdemo/src/test/java/`.

- `fxdemo/src/test/resources/reference/osmd/index.html` — small HTML page
  with `<div id="osmd-container">` sized to a configurable width/height.
  Loads OSMD via a locally checked-in bundle at
  `fxdemo/src/test/resources/reference/osmd/opensheetmusicdisplay.min.js`
  (version pinned, e.g. `1.8.7`).
  Exposes JS function `renderMusicXml(xmlString, widthPx)` which:
  - constructs `new opensheetmusicdisplay.OpenSheetMusicDisplay('osmd-container')`,
  - loads the XML,
  - calls `.render()`,
  - sets `document.title = "sheet4j:done"` (used by Java as a completion signal).

- `WebViewReferenceRenderer.java` — the JavaFX-side driver. API:
  ```java
  BufferedImage render(String musicXml, int widthPx, int heightPx);
  ```
  Implementation:
  - Boots JavaFX toolkit if needed (`Platform.startup` / `new JFXPanel`).
  - Runs on the FX thread: creates a `WebView`, loads
    `getClass().getResource("/reference/osmd/index.html").toExternalForm()`.
  - On `LOAD_SUCCEEDED`, calls
    `webEngine.executeScript("renderMusicXml(...)")` with the XML escaped.
  - Polls `document.title` (or listens to a `titleProperty()` change) for
    `"sheet4j:done"` with a bounded timeout.
  - Snapshots the `WebView` to a `WritableImage`, converts via
    `SwingFXUtils.fromFXImage` to a `BufferedImage`, returns it.
  - Result is `CompletableFuture<BufferedImage>` so callers can `.get(timeout)`.
  Model this closely on Lottie4J's
  `fxfileviewer/src/test/java/com/lottie4j/fxfileviewer/WebViewScreenshotGenerator.java`
  (same JFX-thread bootstrap, same "wait for signal + snapshot" pattern).

- `ReferenceCache.java` — helper that, given a MusicXML resource path,
  returns the reference PNG path under
  `fxdemo/src/test/resources/reference/generated/<sample>.png` and only
  regenerates it when the source XML is newer or when the system
  property `-Dsheetmusic4j.reference.regenerate=true` is set. This makes
  CI deterministic (uses committed PNGs) while giving developers a single
  flag to refresh.

- `GenerateReferenceImagesTest.java` (JUnit 5, but tagged
  `@Tag("reference-generation")` and *excluded from the default surefire
  run* — see Step 5). Iterates every `.musicxml` under
  `src/test/resources/xmlsamples/`, calls `WebViewReferenceRenderer`, and
  writes PNGs. This is the "one command to refresh all references".

Dependencies to add in `fxdemo/pom.xml`:
- `org.openjfx:javafx-web` (test scope) — for `WebView`.
- `org.openjfx:javafx-swing` (test scope) — for `SwingFXUtils`.

### Step 2 — Turn `CompareFxViewWithPdfTest` into a step-by-step diagnostic

Rename to `CompareFxViewWithReferenceTest` (keep a `@Deprecated` alias if
easier) and restructure into a `@ParameterizedTest` over every fixture that
has a reference PNG:

- New helper `DiagnosticComparator.java` in `fxdemo/src/test/java/.../compare/`:
  - `Diagnostic compare(BufferedImage rendered, BufferedImage reference, LayoutResult layout)`
  - Produces a `Diagnostic` record with:
    - overall similarity (existing metric),
    - detected staff bounding boxes on both sides,
    - per-measure similarity list,
    - per-glyph "present in reference?" list,
    - path to a written side-by-side HTML report.
- `StaffDetector.java` — horizontal-projection based five-line detector for
  the reference image (returns list of `Rectangle` bounding boxes).
- `DiffReportWriter.java` — writes `target/sheet4j-diff/<name>/report.html`
  with:
  - both images side by side (as `<img>` tags into copied PNGs),
  - a table of per-measure similarities with the worst rows highlighted,
  - a red overlay PNG showing pixel differences (`ImageSimilarity`
    extended with a `diffMap` method).
- Test structure:
  ```java
  @ParameterizedTest(name = "{0}")
  @MethodSource("fixtures")
  void engravingMatchesReference(String name, Path xml) { ... }
  ```
  Each assertion is layered:
  1. `assertTrue(inkRatio(rendered) > 0.001)` — sanity.
  2. `assertEquals(refStaves.size(), layout.staves().size())` — right
     number of staves.
  3. For each staff `assertBoundingBoxRoughlyEquals(...)` with generous
     tolerance.
  4. For each measure `assertMeasureSimilarity >= perMeasureThreshold`.
  5. On failure, print the path to the generated HTML report so the
     developer can jump straight to it.

Failure messages must always include: fixture name, failing measure index,
the two cropped PNG paths, and the report URL.

### Step 3 — Add a "diff view" tab to `SheetDemoApp`

So the same diagnostics can be inspected interactively, not just from tests.

- Add a `SplitPane` tab / third pane "Diff" that, when a MusicXML file is
  open, runs `WebViewReferenceRenderer` on-demand (button "Generate
  reference") and shows the diff report inline in an embedded `WebView`.
- This reuses `WebViewReferenceRenderer` and `DiagnosticComparator` — no
  new engine code needed.
- Move `WebViewReferenceRenderer` and `DiagnosticComparator` out of `test/`
  and into `fxdemo/src/main/java/.../reference/` if the demo needs them
  at runtime; keep the test-only glue (`GenerateReferenceImagesTest`,
  `CompareFxViewWithReferenceTest`) in `test/`.

### Step 4 — Fix the top-priority correctness gaps

These are separate follow-up commits but should be planned now so the
above diff infrastructure has something meaningful to catch. In order:

- `ScorePainter#drawGlyph`: load Bravura (SMuFL) glyphs from a
  `fxviewer/src/main/resources/fonts/Bravura.otf` and use SMuFL codepoints
  for `CLEF_G`, `CLEF_F`, `CLEF_C`, rests, and time-signature digits
  instead of ASCII fallbacks.
- `Engraver`: emit a real `TIME_DIGIT` per numerator/denominator using
  `Attributes.timeSignature()` values, not a single placeholder.
- `Engraver` + `ScorePainter`: compute stem direction from `staffStep`
  (up when below middle line, down when above) and only draw stems for
  notes shorter than a whole note.
- `Engraver`: measure width proportional to sum of note `Duration`s, with
  a minimum from `LayoutOptions.measureMinWidth()`.

Each of these will get its own task-context plan when we tackle it — do
not bundle into Step 1's PR.

### Step 5 — CI wiring (Java 21 library, Java 26 runtime on CI)

**Split policy:**
- The library keeps `<maven.compiler.release>21</maven.compiler.release>`
  in the parent `pom.xml` — no bytecode is emitted for anything newer than
  21. Consumers can still use Sheet4j on JDK 21.
- CI *runs* on JDK 26 (matching Lottie4J's GitHub Actions setup), because
  headless JavaFX WebView snapshotting has been more stable on recent JDKs.
- Coder must not introduce Java 22+ language features or standard-library
  APIs in `main/` sources; `--enable-preview` is not used. Test sources
  may use up-to-Java-21 features only, to keep local runs on JDK 21 green.

**Maven / surefire configuration in `fxdemo/pom.xml`:**
- Default surefire run excludes `@Tag("reference-generation")` so the
  standard `mvn test` never opens a `WebView`.
- Add a Maven profile `refresh-references` that runs *only*
  `@Tag("reference-generation")` and passes the headless-JavaFX system
  properties needed by Monocle:
  ```
  -Dglass.platform=Monocle
  -Dmonocle.platform=Headless
  -Dprism.order=sw
  -Dprism.text=t2k
  -Djava.awt.headless=true
  ```
- Add test-scoped dependencies to `fxdemo/pom.xml`:
  - `org.openjfx:javafx-web`
  - `org.openjfx:javafx-swing` (for `SwingFXUtils`)
  - `org.openjfx:javafx-media` — only if OSMD requires it (probably not).
  - `org.openjfx:javafx-graphics` with classifier `monocle` **is not
    published**; instead depend on
    `org.openjfx:javafx-controls` normally and use Gluon's Monocle:
    `com.gluonhq:javafx-monocle:21-ea+2` (or the latest that supports the
    JDK on CI).

**GitHub Actions workflow** (`.github/workflows/ci.yml`, create if absent):
- Job `build-and-test` on `ubuntu-latest`:
  - `actions/setup-java@v4` with `distribution: temurin`, `java-version: 26`.
  - `mvn -B -ntp verify` — runs unit tests and the diff assertions against
    committed reference PNGs. No WebView is booted here.
- Separate job `refresh-references` (manual `workflow_dispatch` trigger
  only, does *not* run on push/PR):
  - Same JDK 26 setup.
  - `mvn -B -ntp -pl fxdemo -Prefresh-references test`.
  - Uses `peter-evans/create-pull-request` (or similar) to open a PR with
    the regenerated PNGs so a human reviews any change in the reference
    before it lands on `main`.
- Model these jobs on the Lottie4J `.github/workflows/*.yml` — same JDK
  version, same Monocle system properties.

**Local developer commands** (document in `fxdemo/README.md` — create
this file):
- `mvn -pl fxdemo test` — run diff assertions against committed
  references (no WebView).
- `mvn -pl fxdemo -Prefresh-references test` — regenerate references
  locally in a headless WebView (requires JDK 21 or newer; JDK 26 matches
  CI most closely).
- `mvn -pl fxdemo javafx:run` — launch `SheetDemoApp` interactively.

## Reference Examples

- `fxdemo/src/test/java/com/sheetmusic4j/fxdemo/CompareFxViewWithPdfTest.java`
  — current whole-image comparison; to be restructured in Step 2.
- `fxdemo/src/test/java/com/sheetmusic4j/fxdemo/HeadlessScoreImage.java`
  — the existing headless-render helper; reuse for the "rendered" side of
  the diff. Do not duplicate.
- `fxdemo/src/test/java/com/sheetmusic4j/fxdemo/PdfRasterizer.java`
  — pattern for reflectively-optional external dependency; the OSMD/WebView
  path should be similarly tolerant if `javafx-web` is missing (skip via
  `Assumptions.assumeTrue`).
- `fxdemo/src/main/java/com/sheetmusic4j/fxdemo/SheetDemoApp.java:120-140`
  — how a side-panel is added to the `SplitPane`; the "Diff" tab in
  Step 3 follows the same idiom as the existing PDF pane.
- `fxviewer/src/main/java/com/sheetmusic4j/fxviewer/ScorePainter.java:47-70`
  — the switch statement to replace in Step 4 with SMuFL glyphs.
- **External model to mirror:**
  `lottie4j/fxfileviewer/src/test/java/com/lottie4j/fxfileviewer/WebViewScreenshotGenerator.java`
  — same JFX bootstrap, WebView load, JS bridge, snapshot pattern. The
  OSMD version differs only in the HTML page and the JS call.

## Verification

For each step:

**Step 1 (reference generator)**
- Run `mvn -pl fxdemo -Prefresh-references test`; expect
  `fxdemo/src/test/resources/reference/generated/c-major-scale.png` to
  appear and be a non-blank staff rendering.
- Delete the file, rerun without the profile → the diff test should skip
  gracefully (existing `assumeTrue` pattern).

**Step 2 (diagnostic comparator)**
- Deliberately break the engraver (e.g. drop every rest) and confirm the
  failure message names the affected measures and links to the HTML
  report under `target/sheet4j-diff/`.
- Revert; test passes with the (still loose) similarity threshold.

**Step 3 (demo Diff tab)**
- Open `c-major-scale.musicxml` in `SheetDemoApp`, click "Generate
  reference", verify the diff report appears in the new tab.

**Step 4 (correctness fixes)** — verified indirectly by rising per-measure
similarity numbers reported by Step 2.

**Step 5 (CI wiring)**
- On a clean checkout with JDK 21: `mvn -pl fxdemo test` runs only diff
  assertions (no WebView bootstrap needed) — proves the library still
  works on the supported minimum JDK.
- On JDK 26: `mvn -pl fxdemo -Prefresh-references test` performs
  regeneration only; produced PNGs are byte-identical between two
  consecutive local runs (deterministic snapshot).
- GitHub Actions `build-and-test` job passes on push using JDK 26 without
  ever hitting the network or opening a display.
- The manually-triggered `refresh-references` workflow opens a PR whose
  diff is limited to `fxdemo/src/test/resources/reference/generated/*.png`.

## Confirmed decisions

1. **Reference renderer:** OpenSheetMusicDisplay (OSMD), pinned version
   `1.8.7` (latest stable at time of writing — Coder should verify and
   bump if a newer patch release exists).
2. **Bundle location:** OSMD is committed **in-repo** at
   `fxdemo/src/test/resources/reference/osmd/opensheetmusicdisplay.min.js`
   (~1.2 MB). Tests never touch the network. Add a `NOTICE` file next to it
   with the OSMD BSD-3-Clause license text.
3. **Headless CI:** Runs on GitHub Actions using JDK 26 (same setup as
   Lottie4J), while the library artifacts stay compiled for **Java 21**.
   See Step 5 for the exact toolchain split.