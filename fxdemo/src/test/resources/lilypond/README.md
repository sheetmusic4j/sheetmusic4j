# LilyPond sample fixtures

Each `.ly` file in this directory is a hand-authored snippet inspired by
[lilypond.org/examples.html](https://lilypond.org/examples.html) and is used
as an input fixture by the demo app so that the JavaFX viewer has real
LilyPond source to open and render.

## Sibling reference images

The demo app (`SheetDemoApp` / `ImageSibling`) shows a companion image
side-by-side with the loaded score when a `.png` (or `.jpg`, `.jpeg`,
`.gif`, `.bmp`) exists next to the source file with the same base name —
same convention as the `abcsamples/` directory.

Expected filenames (paired with the `.ly` files already committed here):

- `musical-notation-simple.png`
- `musical-notation-piano.png`
- `vocal-music-lied.png`
- `vocal-music-choral.png`
- `chord-names.png`
- `orchestral-score.png`
- `complex-notation-cadenza.png`
- `ancient-notation.png`
- `real-music-bach.png`
- `real-music-mozart.png`

There are two supported ways to produce these images so that the demo app
picks them up automatically:

1. **From the LilyPond website.** Screenshot the rendered example on
   [lilypond.org/examples.html](https://lilypond.org/examples.html) that
   corresponds to the fixture and save it next to the `.ly` file. The
   licence covering the site (GPL/GFDL — see the header comment inside
   each `.ly` file) permits this.

2. **Locally, from the source.** If you have `lilypond` on your `PATH`,
   run:

   ```bash
   cd fxdemo/src/test/resources/lilypond
   for f in *.ly; do
       lilypond --png -dresolution=90 -dno-point-and-click \
                -o "${f%.ly}" "$f"
   done
   ```

   This is *not* wired into the Maven build so that CI stays green
   without a LilyPond installation, mirroring the ABC integration's
   stance (`.abc` fixtures ship with hand-captured PNGs rather than a
   generated reference).

The tests do not require the PNGs to be present — `ImageSibling`
gracefully returns an empty optional when no matching image exists.
