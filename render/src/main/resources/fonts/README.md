# SMuFL font bundle

Sheetmusic4J uses [Bravura](https://github.com/steinbergmedia/bravura) — the reference SMuFL font — to draw clefs,
noteheads, rests, and time-signature digits. Because the font is a ~3 MB binary and needs a licence commit review, it is
**not** included in a fresh source checkout by default.

## How to enable SMuFL rendering

1. Download `Bravura.otf` from the Bravura repository releases or `redist/`
   directory:

   <https://github.com/steinbergmedia/bravura>

   Verify the exact path in the currently-checked-out tag before scripting it — the upstream layout has moved between
   `redist/otf/` and `redist/` over the years.

2. Place the file at:

   ```
   fxviewer/src/main/resources/fonts/Bravura.otf
   ```

3. Grab the SIL Open Font License text from the Bravura repository root and commit it next to the OTF as `LICENSE` (or
   `OFL.txt`):

   <https://github.com/steinbergmedia/bravura>

   The exact filename in the upstream repo has changed a few times; whichever
   `OFL.txt` / `LICENSE` / `LICENSE.txt` is present at the root of the currently-checked-out tag is the one that belongs
   here.

Bravura is distributed under the [SIL Open Font License, Version 1.1](
https://scripts.sil.org/OFL) — compatible with committing it to this repository.

## What happens when the font is present

- Both `FxRenderSurface` (JavaFX on-screen) and `AwtRenderSurface` (headless test / demo diff tab) detect `Bravura.otf`
  on the classpath and register it the first time they are asked to draw a SMuFL glyph.
- `ScorePainter#drawGlyph` prefers SMuFL codepoints (`SmuflGlyphs.codepoint`)
  and only falls back to the primitive/text shapes if the surface reports the font is unavailable.
- No configuration switch is needed — the code auto-detects.

## What happens when the font is absent

- `RenderSurface#drawSmuflGlyph` returns `false`.
- The painter falls back to primitive shapes: filled/hollow ovals for noteheads, ASCII letters for clefs, simple
  rectangles/zig-zags for rests, and plain-digit text for time signatures.
- All tests still pass on a fresh checkout — the diff test simply reports lower per-measure similarity numbers.
