id: 345a4723-f755-4a46-85cd-ccdad5d6c62f
sessionId: e96886f4-1b2f-4bdd-a6f0-190b762b8356
date: '2026-07-21T12:50:32.253Z'
label: 'Task 2: Lyrics (single verse)'
---
# Task 2: Lyrics (single verse)

## Goal
Read MusicXML `<lyric>` elements attached to notes, carry them through the
model, engrave verse-1 syllables as `TextPlacement`s centered below their
notehead, and expose a visibility toggle for the new `LYRIC` category in
the demo app. Multi-verse stacking, elision, and `<extend>` melisma lines
are deferred.

**Depends on Task 1** — reuses `TextPlacement.Category` (needs `LYRIC`
added to the enum) and the `SheetView.hiddenTextCategoriesProperty()`
visibility plumbing.

## Design

### Model — `Lyric` attached to `Note`
- New record `Lyric(String text, Syllabic syllabic, int verse)`.
  - `text`: the syllable's raw string (whitespace-trimmed, may be empty
    if the source `<text>` was blank — we drop empty lyrics at the
    reader).
  - `syllabic`: enum `Syllabic { SINGLE, BEGIN, MIDDLE, END }` mapped
    from the MusicXML `<syllabic>` element. Defaults to `SINGLE` when
    the element is absent.
  - `verse`: MusicXML `<lyric number="…">`, integer ≥ 1, defaults to 1
    when the attribute is absent or unparseable.
- `Note` grows `private final List<Lyric> lyrics;` populated via
  `Note.Builder.addLyric(Lyric)` / `Note.Builder.lyrics(List<Lyric>)`,
  following the existing `beams` pattern (defensive `List.copyOf` at
  build time, empty list by default). Public accessor `List<Lyric>
  lyrics()` returns the immutable list.
- Rationale for putting lyrics on `Note` (not `MusicElement`): MusicXML
  attaches `<lyric>` inside `<note>`. Chords: the primary note carries
  the lyric (per MusicXML convention); `Chord.notes().get(0).lyrics()`
  is what the engraver reads.

### Reader — parse `<lyric>` inside `<note>`
- In `MusicXmlReader.readNote`, add a `case "lyric"` branch that
  delegates to a new `readLyric(reader)` helper returning a `Lyric` (or
  `null` when the syllable text is empty).
- `readLyric` scans the `<lyric>` element:
  - `number` attribute → verse (fallback 1).
  - Nested `<syllabic>` → `Syllabic` enum (fallback `SINGLE`).
  - Nested `<text>` → syllable text; if multiple `<text>` chunks appear
    (elision), concatenate them with a single space separator. `<elision>`
    itself is ignored for MVP.
  - `<extend>` is ignored for MVP (logged nowhere; simply skipped).
- Collected lyrics are appended to a local `List<Lyric>` and pushed via
  `nb.lyrics(lyrics)` when the note is built.

### Writer — round-trip
- `MusicXmlWriter.writeNote`: after beams (or as the last child before
  `</note>`) emit each `Lyric` as:
  ```xml
  <lyric number="1">
    <syllabic>begin</syllabic>
    <text>Ma</text>
  </lyric>
  ```
- Add `Syllabic.xmlValue()` for lowercase MusicXML rendering.

### Engraver — verse-1 syllables below the notehead
- `TextPlacement.Category` gains a new value `LYRIC` (this is a small
  addition on top of Task 1's enum — the plan for Task 1 already
  reserved room for it).
- Extend `PartInfo` with `boolean hasLyrics()` — set during
  `PartInfo.of(part)` by scanning every note (including chord members)
  for a non-empty verse-1 lyric.
- After `layoutStaffRow` finishes emitting the last staff of a part, if
  `partInfo.hasLyrics()`, advance the row's `staffTop` cursor by an
  extra `LYRIC_RESERVE = gap * 3.2` before the next part starts. This
  is a per-part reservation so multi-staff parts (grand-staff piano)
  still get one lyric row below the whole part, and instrument parts
  without lyrics don't waste vertical space.
- In `Engraver.placeElement` (or a new `placeLyrics` helper called from
  it):
  - For a bare `Note`: use the note's lyric list.
  - For a `Chord`: use `chord.notes().get(0).lyrics()`.
  - For a `Rest`: no lyrics.
- For each lyric in the note that has `verse == 1` (verses ≥ 2 are
  read+kept in the model but not rendered — MVP):
  - `text = lyric.text() + syllabicSuffix(lyric.syllabic())`
    - `SINGLE` / `END` → no suffix
    - `BEGIN` / `MIDDLE` → append `"-"` (simple hyphen, drawn as part of
      the text; a proper mid-syllable hyphen glyph is a follow-up)
  - `fontSize = gap * 1.4`
  - `x = noteX` (center-aligned under the notehead)
  - `y = staffY + options.staffHeight() + gap * 2.2 + fontSize`
    (baseline below the last staff line)
  - `align = CENTER`
  - `category = LYRIC`
- Only draw lyrics on the *primary* staff of a multi-staff part (staff
  1) to avoid duplicating them under a piano LH.

### Viewer — extend the visibility menu
- Task 1 already wired the filter. Extension only:
  - `SheetDemoApp.buildMenuBar`: add another `CheckMenuItem`
    "Show lyrics" bound to `TextPlacement.Category.LYRIC` in
    `sheetView.hiddenTextCategoriesProperty()`. Checked by default.

## Implementation Steps

### Step 1 — Model: `Syllabic` + `Lyric` + `Note.lyrics()`
- `sheetmusic4j/core/src/main/java/com/sheetmusic4j/core/model/Syllabic.java`
  (new) — enum `SINGLE`, `BEGIN`, `MIDDLE`, `END` with a
  `xmlValue()` (lowercase name) and static `fromXml(String)` (falls back
  to `SINGLE` for unknown / null input).
- `sheetmusic4j/core/src/main/java/com/sheetmusic4j/core/model/Lyric.java`
  (new) — record `Lyric(String text, Syllabic syllabic, int verse)` with
  compact constructor: null text → empty; null syllabic → `SINGLE`;
  verse < 1 → 1.
- `sheetmusic4j/core/src/main/java/com/sheetmusic4j/core/model/Note.java`
  - add `private final List<Lyric> lyrics;` field
  - populate from `builder.lyrics` via `List.copyOf`
  - expose `public List<Lyric> lyrics()`
  - add `Builder.addLyric(Lyric)` and
    `Builder.lyrics(List<Lyric>)` (following the beams pattern).

### Step 2 — Reader: `<lyric>` handling
- `sheetmusic4j/core/src/main/java/com/sheetmusic4j/core/musicxml/MusicXmlReader.java`
  - In `readNote`, before the `default` branch, add:
    ```java
    case "lyric" -> {
        Lyric lyric = readLyric(reader);
        if (lyric != null) {
            lyrics.add(lyric);
        }
    }
    ```
  - Declare `List<Lyric> lyrics = new ArrayList<>();` at the top of
    `readNote` next to the other collectors.
  - Pass to `nb.lyrics(lyrics)` when building the note.
  - New private helper `readLyric(XMLStreamReader reader)`:
    - Read `number` attribute (default 1).
    - Loop until `END_ELEMENT("lyric")`:
      - `<syllabic>` → parse via `Syllabic.fromXml(readText(...))`.
      - `<text>` → append to a StringBuilder (space-separated on repeat).
      - Everything else (`<extend>`, `<elision>`, `<end-line>`, ...)
        skipped.
    - Return `null` if the accumulated text is blank; otherwise a
      `Lyric` with the collected values.

### Step 3 — Writer: emit `<lyric>`
- `sheetmusic4j/core/src/main/java/com/sheetmusic4j/core/musicxml/MusicXmlWriter.java`
  - `writeNote`: after the beams loop, iterate `note.lyrics()` and for
    each write:
    ```
    <lyric number="{verse}">
      <syllabic>{lower}</syllabic>
      <text>{text}</text>
    </lyric>
    ```
    Use the existing `IndentingWriter.start`/`textElement`/`end`.

### Step 4 — Engraver: reserve space + emit placements
- `sheetmusic4j/engraving/src/main/java/com/sheetmusic4j/engraving/TextPlacement.java`
  - Add `LYRIC` to `Category`.
- `sheetmusic4j/engraving/src/main/java/com/sheetmusic4j/engraving/Engraver.java`
  - `PartInfo`: add `boolean hasLyrics` field; compute in `PartInfo.of`
    by scanning every measure element (Note directly, Chord first
    member) for a non-empty verse-1 lyric.
  - `PartInfo.hasLyrics()` accessor.
  - Introduce a constant `LYRIC_RESERVE_GAPS = 3.2`.
  - In the row layout loop, after the inner staff loop for a `PartInfo
    p`, if `p.hasLyrics()`, add
    `staffTop += options.staffLineGap() * LYRIC_RESERVE_GAPS;`
    (before advancing to the next part).
  - Extend `placeElement` signature (or create a sibling helper called
    right after glyph placement) so it also emits lyric TextPlacements.
    Cleanest: pass the `List<TextPlacement> texts` down through
    `layoutStaffRow` → `placeElement`. Add a new parameter, wire it
    from `Engraver.layout` (the same `texts` list currently populated
    only by `layoutTitleBlock`).
  - New helper `placeLyrics(List<TextPlacement> texts, MusicElement el,
    double noteX, double staffY, int staffIdx, LayoutOptions options)`:
    - Skip when `staffIdx != 0` (only staff 1 of a part gets lyrics).
    - Extract the note carrying lyrics (Note directly / Chord's first
      note); ignore Rest.
    - For each lyric with `verse == 1` and non-empty text:
      - Compute text + syllabic suffix.
      - Compute y = `staffY + options.staffHeight() + options.staffLineGap() * 2.2 + fontSize`.
      - `texts.add(new TextPlacement(text, noteX, y, fontSize,
        Align.CENTER, Category.LYRIC));`

### Step 5 — Demo: lyric visibility toggle
- `sheetmusic4j/fxdemo/src/main/java/com/sheetmusic4j/fxdemo/SheetDemoApp.java`
  - In the *View → Text* submenu built in Task 1, add another
    `CheckMenuItem` "Show lyrics" — checked by default, toggles
    `TextPlacement.Category.LYRIC` in
    `sheetView.hiddenTextCategoriesProperty()`.

### Step 6 — Tests

- `sheetmusic4j/core/src/test/java/com/sheetmusic4j/core/musicxml/MusicXmlReaderTest.java`
  - `readsSingleVerseLyric` — inline XML with one syllable
    (`<lyric number="1"><syllabic>single</syllabic><text>La</text></lyric>`);
    assert `note.lyrics()` has size 1 with `text=="La"`, `syllabic ==
    Syllabic.SINGLE`, `verse == 1`.
  - `readsSyllabicHyphenation` — three-note begin/middle/end sequence
    (`Ma-gni-fi`); assert each syllabic value.
  - `readsLyricsFromBinchoisSample` — read
    `fxdemo/src/test/resources/xmlsamples/Binchois.musicxml` via
    resource path; assert at least the first measure's note has
    `text=="Ma"` (only add if the file is accessible from the core
    test classpath — otherwise skip and rely on inline XML tests).
  - `defaultsVerseAndSyllabicWhenMissing` — inline XML with just
    `<lyric><text>Hi</text></lyric>`; assert verse=1, syllabic=SINGLE.

- `sheetmusic4j/core/src/test/java/com/sheetmusic4j/core/musicxml/MusicXmlWriterTest.java`
  - `roundTripPreservesLyrics` — programmatically build a Score whose
    first note carries `new Lyric("La", Syllabic.SINGLE, 1)`, write,
    reparse, assert lyric equality on the reparsed note.
  - Extend `assertSameElement` to compare `note.lyrics()`.

- `sheetmusic4j/engraving/src/test/java/com/sheetmusic4j/engraving/EngraverTest.java`
  or a new `EngraverLyricsTest.java`
  - `emitsLyricTextPlacementUnderNotehead` — Score with one note
    carrying a lyric; assert the resulting `LayoutResult.texts()`
    contains a `TextPlacement` with `Category.LYRIC`, `Align.CENTER`,
    `x` equal to the note's glyph x, `y` below the staff.
  - `syllabicSuffixAppendedForBeginAndMiddle` — assert `"Ma-"` for
    `BEGIN`, `"gni-"` for `MIDDLE`, `"cat"` (no dash) for `END`,
    `"La"` (no dash) for `SINGLE`.
  - `lyricsPushNextPartDownward` — two-part score; part 1 has lyrics,
    part 2 does not. Layout twice (once with lyrics, once with same
    structure but no lyrics) and assert the second part's staff `y`
    is larger when the first part carries lyrics.
  - `chordUsesPrimaryNoteLyric` — Chord where only the first note has
    a lyric; assert exactly one lyric TextPlacement, x under the chord.
  - `verseTwoIsNotRendered` — Note with two lyrics (verses 1 and 2);
    assert only the verse-1 placement is emitted (documents the MVP
    scope so the follow-up multi-verse task can flip this).

- `sheetmusic4j/fxviewer/src/test/java/com/sheetmusic4j/fxviewer/ScorePainterTextVisibilityTest.java`
  (extend the file created in Task 1)
  - `hidingLyricCategorySkipsLyricText` — mirror the CREATOR test.

## Reference Examples
- `MusicXmlReader.java:280` (`readNote` switch) — pattern the new
  `case "lyric"` follows; `beams` accumulation is the closest structural
  analogue.
- `MusicXmlReader.java:270` (`readNote` beam accumulator) — declares
  `List<Beam> beams` at the top of the method and pushes into the
  builder at the end; mirror this for lyrics.
- `MusicXmlWriter.java:170` (`writeNote` beam loop) — pattern for
  emitting a variable-length child collection.
- `Engraver.java` `placeNote` — dot-placement code around
  `Glyph.AUG_DOT` is the closest existing "small extra thing per note"
  we can pattern after when adding lyrics-per-note emission.
- `Engraver.java` `PartInfo.of` — extend this exactly the same way we
  precompute per-part state (clefs, keys, times).
- `EngraverTextBlockTest.java:34` — test style for TextPlacement
  assertions.
- `fxdemo/src/test/resources/xmlsamples/Binchois.musicxml:135-205` —
  real-world example with `syllabic` values, used for the manual
  verification and (optionally) an integration-style reader test.

## Verification
1. `cd sheetmusic4j && ./gradlew :core:test :engraving:test :fxviewer:test :fxdemo:test`
   — all new tests + existing suites pass.
2. Round-trip: `MusicXmlWriterTest.roundTripPreservesStructure` still
   passes; the new `roundTripPreservesLyrics` passes.
3. Manual smoke: launch `SheetDemoApp` with
   `fxdemo/src/test/resources/xmlsamples/Binchois.musicxml`.
   Expect:
   - "Ma-", "gni-", "fi", "-cat" (or the raw
     source variants) visible below the corresponding noteheads.
   - Notes without lyrics have no text below them.
   - View → Text → uncheck "Show lyrics" hides them; re-check restores.
4. Manual smoke: open a purely instrumental score (`c-major-scale.musicxml`).
   Expect no lyric row reserved (the first staff sits at the same y as
   it did before Task 2 landed for that file).
5. Manual smoke: open `MozaVeilSample.musicxml` (Mozart Das Veilchen — a
   Lied with lyrics on the voice line and no lyrics on the piano).
   Expect lyrics only under the voice staff, and the piano grand-staff
   below sits further down than in a hypothetical no-lyric version.

## Deferred / Follow-ups
- **Multi-verse rendering.** Verses 2+ are captured in the model but
  not drawn. Stacking them requires computing the max verse count per
  part and reserving `verseCount * fontSize * 1.4` below the staff.
- **Proper hyphens.** Draw a centered hyphen glyph between adjacent
  `BEGIN`/`MIDDLE`/`END` syllables at the midpoint of the gap
  (currently we just append `"-"` to the syllable text).
- **Melisma extension lines.** `<extend>` draws a horizontal line under
  subsequent notes until the next syllable.
- **Elision.** Rendered as `‿` under the shared notehead.
- **Right-alignment of `end`/`single` at final barline** and lyric-word
  boundary spacing tweaks — engraving refinements.
- **Lyric font.** Currently we use the default surface font. The
  Bravura companion "Bravura Text" would be more appropriate; wire it
  through `SmuflGlyphs` when we have it on the classpath.
