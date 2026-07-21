# Sheet4j :: FX Demo

Standalone JavaFX testbed for the Sheet4j engraving/rendering pipeline. Exposes
the same `ScorePainter` used by the on-screen viewer through a headless AWT
surface, so its output can be compared to a trusted reference in tests.

## Local commands

Run the default test suite (no WebView, no network, no display needed):

    mvn -pl fxdemo -am test

Regenerate the OSMD reference PNGs used by `CompareFxViewWithReferenceTest`.
**This is a local-only step** — it opens a JavaFX WebView, loads OSMD
from `src/test/resources/reference/osmd/opensheetmusicdisplay.min.js`,
renders each fixture, and writes a PNG under
`src/test/resources/reference/generated/`. Commit the resulting PNGs by
hand.

    mvn -pl fxdemo -am -Prefresh-references test

Launch the interactive demo:

    mvn -pl fxdemo javafx:run

## How to add or refresh a reference PNG

1. Ensure a working JavaFX runtime on your machine:
   - macOS / Windows: works out of the box.
   - Linux without a display: start `Xvfb :99 -screen 0 1600x1200x24 &`
     and export `DISPLAY=:99` before running Maven.
2. Add the fixture to
   `CompareFxViewWithReferenceTest.fixtures()` and to
   `GenerateReferenceImagesTest` (or drop it into
   `src/test/resources/xmlsamples/` — the generation test picks up every
   `.musicxml` under that directory automatically).
3. Run `mvn -pl fxdemo -am -Prefresh-references test`. Expect
   `Tests run: N (N > 0)` in the Surefire summary. If you see
   `Tests run: 0`, the profile-merge guard has regressed — see
   `fxdemo/pom.xml` and the `combine.self="override"` note there.
4. Inspect `src/test/resources/reference/generated/*.png`. Only commit
   PNGs whose content you have visually reviewed.
5. Run the default suite once more:

       mvn -pl fxdemo test

   This runs `CompareFxViewWithReferenceTest` against the new PNGs;
   the HTML diff report is written to
   `fxdemo/target/sheet4j-diff/<fixture>/report.html`.

## Tests

- `RenderingPipelineTest` — deterministic smoke test that the pipeline
  produces non-blank output for `c-major-scale.musicxml`.
- `CompareFxViewWithReferenceTest` — step-by-step diagnostic comparison
  against a committed reference PNG. Skips fixtures that have no PNG
  committed yet.
- `GenerateReferenceImagesTest` *(tagged `reference-generation`)* —
  boots a JavaFX WebView with OpenSheetMusicDisplay and writes reference
  PNGs. Excluded from the default surefire run; activated only via
  `-Prefresh-references`.

## CI

`.github/workflows/ci.yml` runs `mvn verify` on JDK 26. It never opens a
WebView; it only executes tests against the committed reference PNGs.
Regenerating references is a local-only workflow; there is no CI job
that produces PNGs.
