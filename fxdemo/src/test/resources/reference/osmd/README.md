# OpenSheetMusicDisplay bundle

This directory expects a pinned OpenSheetMusicDisplay (OSMD) UMD build at:

```
opensheetmusicdisplay.min.js
```

The file is **not** committed to git by default because the build has to be
downloaded and reviewed. Fetch it once (per intended OSMD version) and commit
it alongside `index.html`. Grab the UMD build from either an npm release or a
GitHub release of the project:

<https://github.com/opensheetmusicdisplay/opensheetmusicdisplay>

Verify the actual asset URL from the release page (the exact filename and
download path have varied between minor versions). Once downloaded, the
`NOTICE` file already committed here covers the BSD-3-Clause licence text.

When the file is absent, the `WebViewReferenceRenderer` in
`fxdemo/src/test/java/.../reference/` reports the bundle as missing and every
downstream test is skipped via `Assumptions.assumeTrue`. The default
`mvn test` therefore keeps working on a fresh checkout without any network
access.

## Refreshing references

```
mvn -pl fxdemo -Prefresh-references test
```

That runs only the JUnit tests tagged `reference-generation` and writes PNGs
under `fxdemo/src/test/resources/reference/generated/`.
