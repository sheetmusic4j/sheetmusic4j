# fxdemo

## Visual regression testing

`CompareFxViewWithReferenceTest` renders every fixture headlessly (`HeadlessScoreImage`, using the production
`Engraver` + `ScorePainter` + `AwtRenderSurface`) and compares it against the sibling PDF committed next to the MusicXML
fixture:

- The sibling PDF is rasterized page-by-page via `PdfRasterizer` (PDFBox, invoked reflectively so the reference stays on
  the classpath transitively via `pdfviewfx`).
- Rasterization DPI is tunable via `-Dsheetmusic4j.compare.pdf.dpi=<float>` (default `150`); per-measure similarity
  threshold via `-Dsheetmusic4j.compare.measure.threshold=<0..1>` (default `0.4`).
- On failure a self-contained HTML report is written to `fxdemo/target/sheetmusic4j-diff/<fixture>/report.html`.

`src/test/resources/xmlsamples/` contains real-world MusicXML/MXL sample scores (with matching reference
PDFs/PNGs for several) sourced from https://www.musicxml.com/music-in-musicxml/example-set/ — useful for exercising the
reader/engraver against non-trivial scores beyond the minimal `c-major-scale.musicxml` fixture.
