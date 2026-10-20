# Task: architecture and separation-of-concerns review

Repeatable check that the module layering described in [CLAUDE.md](../../CLAUDE.md) still holds. Run it after larger
refactors or before a release. Every step is read-only; fix findings in separate commits.

## 1. Baseline build

```shell
mvn -B install
```

All modules must be green before you start, so later failures are yours.

## 2. Module dependencies (Maven + JPMS)

```shell
for m in core engraving render render-awt fxviewer fxdemo; do
  echo "== $m"
  cat $m/src/main/java/module-info.java 2>/dev/null
  sed -n '/<dependencies>/,/<\/dependencies>/p' $m/pom.xml | grep -E 'artifactId|scope'
done
```

Check:

- Every library module (`core`, `engraving`, `render`, `render-awt`, `fxviewer`) has a `module-info.java`.
- Dependencies only point down the build order: `core` → `engraving` → `render` → `render-awt` / `fxviewer` → `fxdemo`.
- `requires transitive` is only used for modules whose types appear in the exported API (e.g. `render-awt` exposes
  `BufferedImage`, so it needs `java.desktop` transitively; `render` must not).
- Only `core` (for `javax.sound.midi`) and `render-awt` require `java.desktop`.

## 3. Imports that cross layers

```shell
for m in core engraving render render-awt fxviewer fxdemo; do
  echo "== $m"
  grep -rhoE '^import (static )?(com\.sheetmusic4j\.[a-z]+(\.[a-z]+)?|javafx\.[a-z]+|java\.awt|javax\.[a-z]+)' \
    $m/src/main/java | sed 's/import static /import /' | sort | uniq -c | sort -rn
done
```

Check:

- No `javafx.*` in `core`, `engraving`, `render`, `render-awt`.
- No `java.awt` in `core`, `engraving`, `render` (a `{@code java.awt...}` mention in Javadoc is fine; a `{@link}` is not,
  because it needs the module).
- `fxviewer` only draws through `ScorePainter` / a `RenderSurface`; direct `GraphicsContext` use is limited to canvas
  transforms and JavaFX-only effects. `grep -n 'GraphicsContext' fxviewer/src/main/java -r`

## 4. Package cycles inside a module

```shell
E=engraving/src/main/java/com/sheetmusic4j/engraving
for p in analysis text spacing staff page glyph layout placement; do
  echo "$p ->" $(grep -rhoE '^import com\.sheetmusic4j\.engraving\.[a-z]+' $E/$p \
    | sed 's/import com.sheetmusic4j.engraving.//' | sort -u | grep -v "^$p$" | tr '\n' ' ')
done
C=core/src/main/java/com/sheetmusic4j/core
for p in model io musicxml midi abc guitarpro internal; do
  echo "$p ->" $(grep -rhoE '^import com\.sheetmusic4j\.core\.[a-z]+' $C/$p \
    | sed 's/import com.sheetmusic4j.core.//' | sort -u | grep -v "^$p$" | tr '\n' ' ')
done
```

Expected (2026-10): `analysis → glyph`, `spacing → analysis glyph layout text`, `staff → … spacing text`,
`page → … staff`, `layout → glyph placement`, `placement → glyph`, `glyph →` nothing; `io → model` only (formats are
found through `ServiceLoader`). Any arrow pointing back up is a cycle. When an import is only used in Javadoc, replace
it with a fully qualified `{@link}`.

## 5. Logic in the wrong layer

Look for:

- Pure layout/time math inside JavaFX views or the demo (`fxviewer`, `fxdemo`) that should live in `engraving`
  (example fixed: `LayoutResult.slice`, `PlaybackTimeline`). Hint:
  `grep -rn 'onsetQuarters\|durationQuarters\|xAtTime' fxviewer/src/main fxdemo/src/main`.
- The same filter or helper copied into several views/apps.
- Pass-through wrapper classes that only mirror another class's setters.
- Classes in the `engraving` root package other than `Engraver`.

## 6. Verify

```shell
mvn -B install
```

`LayoutSnapshotTest` must stay unchanged unless the change is meant to move glyphs. Update `../../CLAUDE.md` and
`README.md` when modules or responsibilities move.
