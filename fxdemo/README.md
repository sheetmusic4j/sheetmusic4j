# Sheet4j :: FX Demo

Standalone JavaFX testbed for the Sheet4j engraving/rendering pipeline. Exposes
the same `ScorePainter` used by the on-screen viewer through a headless AWT
surface, so its output can be compared to a trusted reference in tests.

## Local commands

Run the default test suite (no WebView, no network — passes on a fresh
checkout):

```
mvn -pl fxdemo test
```

Regenerate the reference PNGs used by the diagnostic comparator (needs the
OSMD bundle checked in — see `src/test/resources/reference/osmd/README.md`):

```
mvn -pl fxdemo -Prefresh-references test
```

Launch the interactive demo:

```
mvn -pl fxdemo javafx:run
```

## Tests

- `RenderingPipelineTest`
  Deterministic smoke test that the engraving pipeline produces non-blank
  output for `c-major-scale.musicxml`.

- `CompareFxViewWithReferenceTest`
  Step-by-step diagnostic comparison against a committed reference PNG. Skips
  gracefully when no reference is present. On failure, writes a
  self-contained HTML report to `target/sheet4j-diff/<fixture>/report.html`.

- `GenerateReferenceImagesTest` *(tagged `reference-generation`)*
  Boots a headless JavaFX WebView with OpenSheetMusicDisplay and writes fresh
  reference PNGs for every fixture. Excluded from the default surefire run —
  activate with the `refresh-references` profile.

## Diff tab in the demo

The `SheetDemoApp` exposes a "View → Show Diff tab" action that renders an
OSMD reference on demand and shows the diff report inline in an embedded
WebView.

## CI

- `.github/workflows/ci.yml` runs `mvn verify` on JDK 26 without touching the
  network or opening a WebView.
- `.github/workflows/refresh-references.yml` is a manually-triggered workflow
  (`workflow_dispatch`) that runs the `refresh-references` profile and opens
  a PR limited to `fxdemo/src/test/resources/reference/generated/*.png`.
