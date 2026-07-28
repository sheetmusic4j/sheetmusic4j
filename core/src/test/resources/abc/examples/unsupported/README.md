# Unsupported ABC examples

Files in this directory use ABC 2.1 features that the MVP `AbcReader`
does not implement. They are kept as-is (copied verbatim from
<https://abcnotation.com/examples>) so the gap between the current
subset and the full spec is auditable and trackable.

`AbcExamplesTest` explicitly skips this directory.

## Currently gated features

- **Multi-voice tunes** (`V:` fields, `%%staves`): a single tune contains
  several concurrent voice streams. The MVP parser has no notion of
  concurrent voices — every note becomes part of the same sequential
  timeline, which corrupts scores where the voices differ.

  - `jericho-hidden-voice.abc` — uses a hidden `V:chords` voice to
    stack chord symbols on top of the melody voice.

When one of these features is implemented, move the file back into
`../` and it will be picked up by `AbcExamplesTest` automatically.
