# Task: memory-leak and threading check of the JavaFX views

Repeatable check that the `fxviewer` views don't leak and only touch the scene graph on the FX thread. It uses
`StressHarness` (`../../fxdemo/src/test/java/com/sheetmusic4j/fxdemo/stress`), which drives `SheetView`, `StripSheetView`,
`NodeSheetView` and `FlyOverSheetView` the way the demo apps do. It parses each sample score on a background thread
and shows it on the FX thread, animates the cursors, and highlights the sounding notes. Each cycle builds fresh views
and drops the old ones. Run it with Java Flight Recorder (JFR) and a GC log.

Needs a display (the views must really render) and a JDK with `jfr` and `jcmd`. macOS has no `timeout` command, so
start long runs in the background and watch the output file.

## 1. Build and classpath

```shell
mvn -B install
mvn -B -q test-compile dependency:build-classpath -pl fxdemo -Dmdep.outputFile=target/stress-cp.txt
CP="fxdemo/target/test-classes:fxdemo/target/classes:$(cat fxdemo/target/stress-cp.txt)"
H=com.sheetmusic4j.fxdemo.stress.StressHarness
R=fxdemo/src/test/resources
```

Arguments are `sampleDir cycles framesPerScore`. The system properties are:

- `stress.views`: which views to include, e.g. `sheet,strip,node,fly`.
- `stress.files`: a regex on the file name.
- `stress.perFile=true`: report heap after GC for each score.
- `stress.settleMs`: how long to keep pulsing before each report.

## 2. Smoke run (one small file)

```shell
java -cp "$CP" '-Dstress.files=c-major-scale\.musicxml' $H $R 2 5 | grep '^\[stress\]'
```

`discardedViewsAlive` must be `0/N` after each cycle, and `scoresAlive` `1/N` while the views are shown.

## 3. Scaling: each view alone on the largest scores

A view that needs gigabytes for one long score hides everything else, so measure the views one at a time first.

```shell
for v in sheet strip node fly; do
  echo "== $v"
  java -Xmx3g -Dstress.perFile=true -Dstress.views=$v \
    '-Dstress.files=(death-painkiller\.gp|ActorPreludeSample\.musicxml)' \
    -cp "$CP" $H $R 1 3 2>&1 | grep -E '^\[stress\]   |texture|OutOfMemory' | sort | uniq -c
done
```

Look at the `show=` time (that is how long the FX thread is blocked), `heapAfterGC`, and any
`exceed maximum texture size (16384)` errors (a canvas wider or taller than the GPU limit draws nothing).

## 4. Leak run under JFR and GC log

All views, all sample files, a 1 GB heap:

```shell
T=fxdemo/target
java -Xmx1g -XX:+ExitOnOutOfMemoryError -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=$T/stress.hprof \
  "-Xlog:gc*:file=$T/stress-gc.log:time,uptime,level,tags" \
  -XX:StartFlightRecording=filename=$T/stress-all.jfr,settings=profile,dumponexit=true \
  -cp "$CP" $H $R 3 10 > $T/stress-all.out 2>&1

grep -E '^\[stress\] (baseline|cycle|DONE)|OutOfMemory' $T/stress-all.out
grep -hE 'Exception|Error' $T/stress-all.out | sed 's/[0-9]\{3,\}x[0-9]*/NxM/' | sort | uniq -c | sort -rn | head
```

If one view dominates memory (step 3), split the run with `-Dstress.views=...` so the others can still be judged.

You have a leak if:

- `heapAfterGC` at the end of a cycle keeps growing from cycle to cycle (it should be flat after cycle 1), or
- `discardedViewsAlive` is greater than 0 after a cycle, or
- `scoresAlive` is greater than 1 while views are shown.

If `discardedViewsAlive` is greater than 0, re-run with `-Dstress.settleMs=2000` first. JavaFX keeps the previous
scene root until a pulse has synced it, so views that are released after settling are not a leak. If the JVM stops
answering `jcmd`, the heap is full: look at the end of the GC log for back-to-back `Pause Full` lines.

## 5. Read the recordings

```shell
for f in fxdemo/target/stress-all.jfr; do
  echo "===== $f"
  jfr summary $f | grep -E 'JavaMonitorEnter |ThreadPark |JavaErrorThrow|OldObjectSample'
  for v in gc-pauses contention-by-site hot-methods allocation-by-site thread-cpu-load; do
    jfr view --width 160 $v $f | head -20
  done
done
grep -hE 'Exception|Error' fxdemo/target/stress-*.out | sed 's/[0-9]\{3,\}x[0-9]*/NxM/' | sort | uniq -c | sort -rn | head
```

Check:

- **Threading:** library code (`com.sheetmusic4j.*`) may only run on the FX application thread, plus `core` parsing on
  the loader thread. On macOS, JFR names the FX thread `Thread-2`; its stacks end in `InvokeLaterDispatcher`. Script it with
  `jfr print --json --events jdk.ExecutionSample` and group the samples with library frames by `sampledThread`. Any
  other thread in a `fxviewer` frame is a bug.
- **Contention:** `contention-by-site` should report no events.
- **GC:** `gc-pauses` p99 in single-digit to low double-digit ms; no `Pause Full` outside the harness's own
  `System.gc()`.
- **Allocation:** attribute `jdk.ObjectAllocationSample` weight to the first `com.sheetmusic4j` frame. A repaint lambda
  or `Font.font` near the top means work is being repeated per map change or per glyph.
- **Errors:** texture-size `RuntimeException`s, or `RTTexture ... is null` NPEs (the GPU ran out of texture memory),
  come from the render thread and mean something draws nothing.

## Results

### First run (2026-10-14)

- **No leaks:** heap after GC was flat over 4 cycles (14.4 MB for the 2D views, about 16 MB for the fly-over), all
  dropped views were collected, and only the current score was retained.
- **Threading OK:** library code ran only on the FX thread, parsing ran on the loader thread, and there was no monitor
  contention.
- **GC OK:** 212 pauses with p99 11 ms.
- **Open problems:**
  - `FlyOverSheetView` bakes and keeps image tiles for the whole strip. It needed 2.4–2.9 GB and up to 92 s on the FX
    thread for one long score, and the GPU ran out of textures.
  - `StripSheetView` has no canvas-size cap, so most real scores exceed the 16384 px texture limit and render blank.
  - `NoteDecorations` repaints the whole canvas synchronously on every highlight map change.
  - The FX render surfaces call `Font.font` per glyph, which accounts for about 57% of allocation.
  - The demo apps start a new loader thread per open without cancelling the previous one, so a slow earlier load can
    overwrite a newer one.

### After the fixes (2026-10-14)

- Long strips are drawn in tiles near the visible range (`LayoutTiles` + `ScorePainter.paintRegion`). On the largest
  scores, the strip now renders at 15 MB, and the fly-over takes 137–227 MB and 0.3–0.4 s instead of 2.4–2.9 GB and
  up to 92 s.
- Decoration changes are coalesced into one repaint, fonts come from `FontCache`, and the demos load through
  `ScoreLoader` (latest request wins).
- Full run (all four views, 65 files, 3 cycles, `-Xmx1g`): no errors, heap after GC 17–18 MB after every cycle, all
  dropped views collected.
