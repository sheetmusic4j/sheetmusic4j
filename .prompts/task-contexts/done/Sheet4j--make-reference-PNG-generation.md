id: a752ac6c-6f3b-4dc9-86c6-e0c2ecd3165c
sessionId: 5de52ab8-5a10-4c3f-a6de-b9e9c136e08c
date: '2026-07-21T05:43:31.944Z'
label: 'Sheet4j: make reference-PNG generation local-only + fix Maven merge bug'
---
# Sheet4j: make reference-PNG generation local-only + fix Maven merge bug

## Goal

Two coupled issues:

1. **`mvn -pl fxdemo -Prefresh-references test` silently runs 0 tests.**
   The `refresh-references` profile is broken by a Maven POM-merging quirk:
   the base Surefire config sets `<excludedGroups>reference-generation</excludedGroups>`,
   and the profile's `<excludedGroups></excludedGroups>` **does not override**
   the parent (empty child elements merge, they don't replace, unless
   `combine.self="override"` is set). The effective config filters on
   `groups=reference-generation` AND `excludedGroups=reference-generation`
   simultaneously — nothing matches → `Tests run: 0`, no PNG, no error.
2. **Reference-PNG generation should never run in CI.** WebView + JavaFX
   under a headless Linux runner (with or without Monocle) is fragile and
   we've already spent time chasing failures there. The decision is:
   PNG generation is a **local-only developer step**; CI only consumes
   committed PNGs to run `CompareFxViewWithReferenceTest`.

This task: fix the profile so it actually runs, drop the Monocle
misconfiguration, delete the CI workflow that generated PNGs, and
document the local workflow clearly.

## Current state

- `sheetmusic4j/.github/workflows/refresh-references.yml` exists and is
  `workflow_dispatch`-triggered; it opens a PR with regenerated PNGs.
  This is what we're removing.
- `sheetmusic4j/.github/workflows/ci.yml` (JDK 26) runs `mvn verify` with
  no WebView — good, keep as-is.
- `sheetmusic4j/.github/workflows/maven.yml` (JDK 25 Zulu) references a
  non-existent `-Pheadless-tests` profile. That's a pre-existing bug —
  fix it in passing while we're here.
- `sheetmusic4j/fxdemo/pom.xml`:
  - Base Surefire config: `<excludedGroups>reference-generation</excludedGroups>`.
  - Profile `refresh-references` sets Monocle system properties
    (`glass.platform=Monocle`, `monocle.platform=Headless`, etc.) and
    pulls `org.testfx:openjfx-monocle`. **Monocle cannot host `WebView`**
    — WebKit is not part of the Monocle Glass backend. Even if the merge
    bug were fixed, this configuration would break WebView.
- `sheetmusic4j/fxdemo/src/test/java/com/sheetmusic4j/fxdemo/reference/GenerateReferenceImagesTest.java`
  is `@Tag("reference-generation")` and generates PNGs from the OSMD
  bundle already committed at
  `fxdemo/src/test/resources/reference/osmd/opensheetmusicdisplay.min.js`.
- No `*.png` currently committed under
  `fxdemo/src/test/resources/reference/generated/` — so
  `CompareFxViewWithReferenceTest` still skips every fixture on CI. That's
  expected until the developer commits the first batch of PNGs locally.

## Design

**Contract after this task:**

- **CI never runs WebView.** It only runs `CompareFxViewWithReferenceTest`
  against committed PNGs (skips gracefully when a fixture has no PNG
  yet). No workflow ever regenerates PNGs.
- **Local machine is the sole PNG oracle.** The developer runs
  `mvn -pl fxdemo -Prefresh-references test`, inspects the resulting
  PNGs, and commits them. The command succeeds only when JavaFX+WebView
  works on that machine — which on macOS/Windows/Linux-with-a-display is
  the native platform, *not* Monocle.
- **Any misconfiguration fails loudly.** `<failIfNoTests>true</failIfNoTests>`
  in the profile so a future POM merge accident goes red instead of
  silently passing.

**Why drop Monocle entirely?**
- Its purpose was CI headless rendering, which we're no longer doing.
- It breaks WebView locally too, because it *overrides* the native
  platform whenever it's on the classpath and the system properties are
  set. Removing both the dependency and the system properties makes the
  profile use whatever native JavaFX backend is installed.

**Why keep `prism.order=sw`?**
- Forces software rendering. Marginally slower but rules out GPU driver
  variability between developers. Optional but safer for
  reproducibility of the PNG output.

## Implementation Steps

### Step 1 — Delete the reference-generation CI workflow

- **Delete** `sheetmusic4j/.github/workflows/refresh-references.yml`
  in its entirety.

Rationale: it never runs successfully (Monocle+WebView incompatibility),
and the policy is now "local only".

### Step 2 — Fix `fxdemo/pom.xml`

Two problems to fix in the `refresh-references` profile:

1. The `<excludedGroups>` merge bug.
2. Monocle stack should be removed.

Also add `<failIfNoTests>true</failIfNoTests>` so future silent
no-op regressions are impossible.

Replace the entire `<profile>` block in `sheetmusic4j/fxdemo/pom.xml`
with:

```xml
<profile>
    <!--
        Regenerate the reference PNGs used by the diagnostic comparator.
        Local-only: needs a working JavaFX + WebView (native macOS / Windows,
        or Linux with a display / Xvfb). Do NOT wire this into CI.

        Usage:
            mvn -pl fxdemo -am -Prefresh-references test
    -->
    <id>refresh-references</id>
    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
                <configuration>
                    <!--
                        combine.self="override" is required: without it, an
                        empty child element MERGES with (does not replace)
                        the parent's <excludedGroups>reference-generation</...>,
                        so JUnit ends up including AND excluding the same
                        tag and Tests run: 0 silently.
                    -->
                    <excludedGroups combine.self="override"></excludedGroups>
                    <groups>reference-generation</groups>
                    <!--
                        Fail loudly when the tag filter accidentally
                        matches nothing (regression guard for the merge
                        bug we just fixed).
                    -->
                    <failIfNoTests>true</failIfNoTests>
                    <systemPropertyVariables>
                        <sheetmusic4j.reference.regenerate>true</sheetmusic4j.reference.regenerate>
                        <!--
                            Force software Prism to keep PNG output stable
                            across GPU drivers. Do NOT set glass.platform
                            or monocle.platform: Monocle cannot host WebView.
                        -->
                        <prism.order>sw</prism.order>
                    </systemPropertyVariables>
                </configuration>
            </plugin>
        </plugins>
    </build>
</profile>
```

Explicit changes vs current file:
- **Remove** the `<dependencies>` block that adds `org.testfx:openjfx-monocle`.
- **Add** `combine.self="override"` on `<excludedGroups>`.
- **Add** `<failIfNoTests>true</failIfNoTests>`.
- **Remove** system properties: `glass.platform`, `monocle.platform`,
  `prism.text`, `java.awt.headless`.
- **Keep** `sheetmusic4j.reference.regenerate=true` and `prism.order=sw`.

### Step 3 — Remove Monocle from parent `pom.xml`

Nothing else uses `openjfx-monocle` after Step 2. Trim the
`<dependencyManagement>` entry to keep the POM honest.

In `sheetmusic4j/pom.xml`:

- **Remove** the `<dependency>` block for
  `org.testfx:openjfx-monocle`.
- **Remove** the `<monocle.version>21.0.2</monocle.version>` property.

Verify with `grep -R monocle sheetmusic4j` after the change — should
return zero matches.

### Step 4 — Fix / remove the stale `maven.yml` workflow

`.github/workflows/maven.yml` runs `mvn test -Pheadless-tests`, but no
such profile exists in any POM. The whole file duplicates `ci.yml` at a
different JDK (25 Zulu vs 26 Temurin).

Choose one of:

- **Preferred**: delete `sheetmusic4j/.github/workflows/maven.yml`
  entirely. `ci.yml` already runs `mvn verify` on JDK 26.
- Alternative: change the command in `maven.yml` to plain
  `mvn -ntp -B test` (drop the missing profile) if you want the JDK 25
  matrix.

Pick the delete option unless you have a specific reason to keep JDK 25
coverage.

### Step 5 — Update documentation

`sheetmusic4j/fxdemo/README.md` currently says:

> `.github/workflows/refresh-references.yml` is a manually-triggered
> workflow that runs the `refresh-references` profile and opens a PR
> limited to `fxdemo/src/test/resources/reference/generated/*.png`.

Replace the entire `## CI` section (and the `## Local commands` /
`## Tests` sections above it) with the block below. Full replacement
is easier than surgical edits here.

```markdown
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
```

Also update:

- `sheetmusic4j/fxdemo/src/test/resources/reference/generated/README.md`:
  replace with:

  ```markdown
  # Generated reference images

  PNGs produced locally by
  `fxdemo/src/test/java/com/sheetmusic4j/fxdemo/reference/GenerateReferenceImagesTest`
  and committed by hand.

  **Do not hand-edit these.** Regenerate with:

      mvn -pl fxdemo -am -Prefresh-references test

  This is a local-only step — no CI workflow generates PNGs. See
  `fxdemo/README.md` for the full local workflow.
  ```

- `sheetmusic4j/CLAUDE.md` — the "Visual regression testing" section
  still mentions the old `-Pheadless-tests` CI profile:

  > CI (`.github/workflows/maven.yml`) runs tests with
  > `mvn test -Pheadless-tests` and a separate build/JavaDoc-validation
  > job with `mvn -Dmaven.test.skip=true install` + `mvn javadoc:javadoc`,
  > on JDK 25 (Zulu) even though `maven.compiler.release` is 21 …

  Rewrite that paragraph to:

  > CI (`.github/workflows/ci.yml`) runs `mvn verify` on JDK 26 (Temurin).
  > The library still targets `maven.compiler.release=21`. There is no CI
  > workflow that regenerates OSMD reference PNGs — that is a local-only
  > step (`mvn -pl fxdemo -am -Prefresh-references test`); PNGs are
  > committed by the developer.

### Step 6 — Verify the fix

After all edits, run this sequence on your local machine:

```bash
# 1. Default suite still passes and touches no WebView.
mvn -pl fxdemo -am test
#    → expect: RenderingPipelineTest + CompareFxViewWithReferenceTest
#    → expect the compare test to SKIP every fixture (no PNGs yet).
#    → expect NO Monocle / WebView activity in the log.

# 2. The refresh profile now discovers tests.
mvn -pl fxdemo -am -Prefresh-references test
#    → expect: "Running com.sheetmusic4j.fxdemo.reference.GenerateReferenceImagesTest"
#    → expect: Tests run: N with N > 0 (one per fixture).
#    → expect PNGs to appear under
#      fxdemo/src/test/resources/reference/generated/*.png
#    → if you see "Tests run: 0", the POM merge fix is wrong: check that
#      combine.self="override" is present on <excludedGroups>.

# 3. The compare test now runs (not skips) against the new PNGs.
mvn -pl fxdemo test
#    → expect: engravingMatchesReference[<fixture>] runs and reports
#      per-measure similarity numbers.

# 4. Commit the PNGs (and only the PNGs, no source changes).
git add fxdemo/src/test/resources/reference/generated/*.png
git status              # confirm only PNGs are staged
git commit -m "chore(refs): regenerate OSMD reference images"
```

If step 2 still prints `Tests run: 0`, run
`mvn -pl fxdemo -Prefresh-references -X test` and look for the effective
Surefire configuration in the debug log; both `<groups>` and
`<excludedGroups>` should be visible, and `<excludedGroups>` should be
empty. Anything else means the override attribute didn't take effect.

## Local run instructions (for the README / hand-off)

Once the refactor lands, the everyday flow is:

1. **First-time setup on this machine.**
   Nothing to install beyond JDK 21+ and Maven 3.9+. JavaFX pulls its
   native binaries via the `org.openjfx` Maven artifacts. On Linux
   without a desktop session, install `xvfb libgtk-3-0 libxtst6` and
   start `Xvfb` before running the reference profile.
2. **Every time you land an engraving change that could shift the
   reference:**
   ```bash
   mvn -pl fxdemo -am -Prefresh-references test
   ```
   This regenerates PNGs for every fixture (about 5–20s per fixture,
   depending on your machine) into
   `fxdemo/src/test/resources/reference/generated/`.
3. **Visually inspect each modified PNG.** Just open them in Preview /
   an image viewer — the point is that a *human* signs off on the
   reference. If a PNG changed because *your* engraving changed and the
   new render is worse, do NOT commit; fix the engraving first.
4. **Run the diagnostic suite:**
   ```bash
   mvn -pl fxdemo test
   ```
   Look at `fxdemo/target/sheet4j-diff/<fixture>/report.html` for the
   per-measure similarity numbers and the pixel diff overlay.
5. **Commit only the PNGs**, in a dedicated commit separate from the
   code that caused them to shift. This keeps `git blame` on the PNGs
   meaningful.

## Reference Examples

- `sheetmusic4j/fxdemo/pom.xml` — the file to edit in Step 2. Note the
  existing base `<excludedGroups>reference-generation</excludedGroups>`
  under `<build>/<plugins>` — that stays.
- `sheetmusic4j/.github/workflows/ci.yml` — untouched by this refactor;
  demonstrates the "read-only" CI pattern we want to preserve.
- `sheetmusic4j/fxdemo/src/test/java/com/sheetmusic4j/fxdemo/reference/GenerateReferenceImagesTest.java`
  — the test class that becomes discoverable after the POM fix.
- Maven's plugin-config merging rules:
  https://maven.apache.org/pom.html#Plugins — search for
  `combine.self`; that is the attribute we're relying on.

## Verification

1. `mvn -pl fxdemo -am test` in a fresh clone runs `RenderingPipelineTest`
   and `CompareFxViewWithReferenceTest` (skipped fixtures OK), no
   WebView bootstrap in the log.
2. `mvn -pl fxdemo -am -Prefresh-references test` in a fresh clone:
   - Surefire log contains
     `Running com.sheetmusic4j.fxdemo.reference.GenerateReferenceImagesTest`.
   - `Tests run: N` with N ≥ 1.
   - New PNGs materialise under
     `fxdemo/src/test/resources/reference/generated/`.
   - No `Monocle` / `Glass` / `openjfx-monocle` mentions anywhere.
3. `grep -R "refresh-references.yml\|openjfx-monocle\|monocle.platform\|glass.platform"
   sheetmusic4j` returns zero hits.
4. `.github/workflows/` contains at most `ci.yml` and `release.yml`
   (and optionally `maven.yml` if you kept it; `refresh-references.yml`
   is gone).
5. Manual review: `fxdemo/README.md` describes the local workflow and
   does **not** mention any CI job that regenerates PNGs.

## Open question

The `maven.yml` workflow (JDK 25 Zulu) is redundant with `ci.yml`
(JDK 26 Temurin) and references a non-existent profile. Step 4
proposes deleting it. If you want to preserve JDK 25 coverage instead,
say so and Coder will only fix the `-Pheadless-tests` typo without
removing the file.
