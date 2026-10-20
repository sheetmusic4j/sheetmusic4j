package com.sheetmusic4j.fxdemo.stress;

import java.lang.management.ManagementFactory;
import java.lang.ref.WeakReference;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import com.sheetmusic4j.core.io.ScoreFile;
import com.sheetmusic4j.core.model.Score;
import com.sheetmusic4j.engraving.layout.NoteAnchor;
import com.sheetmusic4j.engraving.layout.PlaybackTimeline;
import com.sheetmusic4j.fxviewer.FlyOverSheetView;
import com.sheetmusic4j.fxviewer.NodeSheetView;
import com.sheetmusic4j.fxviewer.SheetView;
import com.sheetmusic4j.fxviewer.StripSheetView;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

/**
 * Drives every fxviewer view the way the demo apps do, cycle after cycle, so
 * JFR and the GC log can show leaks and threading problems. Not a unit test
 * (Surefire skips it); see {@code .claude/tasks/memory-and-threading-check.md}.
 *
 * <p>Args: {@code sampleDir cycles framesPerScore [highlightWithoutClear]}.
 * System properties: {@code stress.views} (comma list of
 * {@code sheet,strip,node,fly}), {@code stress.files} (file-name regex),
 * {@code stress.perFile=true} (heap after GC per score).
 *
 * <p>Each score is parsed on a background thread and shown on the FX thread,
 * cursors are animated and sounding notes highlighted like
 * {@code StripDemoApp}. Every cycle builds fresh views and drops the old ones;
 * the report shows heap after GC, how many dropped views and old scores are
 * still reachable (both should be 0 after a cycle, 1 score while shown).
 */
public class StressHarness {

    public static void main(String[] args) {
        Application.launch(App.class, args);
    }

    public static class App extends Application {
        private Stage stage;
        private boolean perFile;
        private final List<WeakReference<Object>> discarded = new ArrayList<>();
        private final List<WeakReference<Score>> oldScores = new ArrayList<>();

        @Override
        public void start(Stage stage) throws Exception {
            this.stage = stage;
            List<String> a = getParameters().getRaw();
            Path dir = Path.of(a.get(0));
            int cycles = Integer.parseInt(a.get(1));
            int frames = Integer.parseInt(a.get(2));
            boolean noClear = a.size() > 3 && Boolean.parseBoolean(a.get(3));
            perFile = Boolean.getBoolean("stress.perFile");
            List<Path> files;
            try (Stream<Path> s = Files.walk(dir)) {
                String only = System.getProperty("stress.files", ".*");
                files = s.filter(p -> p.toString().matches(".*\\.(musicxml|xml|mxl|mid|midi|abc|gp)$"))
                        .filter(p -> p.getFileName().toString().matches(only))
                        .sorted().toList();
            }
            stage.setScene(new Scene(new VBox(), 1200, 900));
            stage.show();
            boolean finalNoClear = noClear;
            Thread driver = new Thread(() -> drive(files, cycles, frames, finalNoClear), "stress-driver");
            driver.setDaemon(true);
            driver.start();
        }

        private void drive(List<Path> files, int cycles, int frames, boolean noClear) {
            log("files=" + files.size() + " cycles=" + cycles + " frames=" + frames + " noClear=" + noClear
                    + " 3D=" + FlyOverSheetView.is3DSupported());
            try {
                report("baseline");
                for (int c = 1; c <= cycles; c++) {
                    int failed = runCycle(c, files, frames, noClear);
                    report("cycle " + c + " (load failures " + failed + ")");
                }
                log("DONE");
            } catch (Throwable t) {
                t.printStackTrace();
            } finally {
                Platform.exit();
            }
        }

        /** One cycle in its own frame, so its views are unreachable once it returns. */
        private int runCycle(int c, List<Path> files, int frames, boolean noClear) throws Exception {
                Views v = fx(Views::new);
                fx(() -> { stage.getScene().setRoot(v.root); return null; });
                int failed = 0;
                for (Path file : files) {
                    Score score;
                    try {
                        score = ScoreFile.load(file); // background thread, like the demos' Task
                    } catch (RuntimeException ex) {
                        failed++;
                        continue;
                    }
                    oldScores.add(new WeakReference<>(score));
                    long t0 = System.nanoTime();
                    fx(() -> { v.show(score, noClear); return null; });
                    long showMs = (System.nanoTime() - t0) / 1_000_000;
                    for (int f = 0; f < frames; f++) {
                        double t = v.total * f / Math.max(frames - 1, 1);
                        fx(() -> { v.frame(t, noClear); return null; });
                        pulse();
                    }
                    if (perFile) {
                        System.gc();
                        long used = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
                        log(String.format("  %-48s show=%5d ms  heapAfterGC=%6.1f MB  scoresAlive=%d",
                                file.getFileName(), showMs, used / 1048576.0,
                                oldScores.stream().filter(r -> r.get() != null).count()));
                    }
                }
                report("cycle " + c + " views still shown");
                fx(() -> { stage.getScene().setRoot(new VBox()); return null; });
                // The scene holds its previous root until the next pulse has synced it.
                fx(() -> { stage.getScene().getRoot().requestLayout(); return null; });
                long settle = Long.getLong("stress.settleMs", 0);
                for (long end = System.currentTimeMillis() + settle; System.currentTimeMillis() < end; ) {
                    fx(() -> { stage.getScene().getRoot().requestLayout(); return null; });
                    pulse();
                    Thread.sleep(16);
                }
                pulse();
                pulse();
                discarded.add(new WeakReference<>(v.sheet));
                discarded.add(new WeakReference<>(v.strip));
                discarded.add(new WeakReference<>(v.node));
                if (v.fly != null) {
                    discarded.add(new WeakReference<>(v.fly));
                }
                return failed;
        }

        private void report(String label) throws Exception {
            for (int i = 0; i < 3; i++) {
                System.gc();
                Thread.sleep(200);
            }
            long used = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
            long liveViews = discarded.stream().filter(r -> r.get() != null).count();
            long liveScores = oldScores.stream().filter(r -> r.get() != null).count();
            log(String.format("%-28s heapAfterGC=%6.1f MB  threads=%d  discardedViewsAlive=%d/%d  scoresAlive=%d/%d",
                    label, used / 1048576.0, Thread.activeCount(), liveViews, discarded.size(),
                    liveScores, oldScores.size()));
        }

        private static void log(String s) {
            System.out.println("[stress] " + s);
        }

        /** Wait for one rendered pulse so canvases and the 3D scene actually draw. */
        private static void pulse() throws InterruptedException {
            CountDownLatch latch = new CountDownLatch(1);
            Platform.runLater(() -> Platform.runLater(latch::countDown));
            latch.await();
        }

        private static <T> T fx(java.util.concurrent.Callable<T> work) throws Exception {
            AtomicReference<T> result = new AtomicReference<>();
            AtomicReference<Throwable> error = new AtomicReference<>();
            CountDownLatch latch = new CountDownLatch(1);
            Platform.runLater(() -> {
                try {
                    result.set(work.call());
                } catch (Throwable t) {
                    error.set(t);
                } finally {
                    latch.countDown();
                }
            });
            latch.await();
            if (error.get() != null) {
                throw new RuntimeException(error.get());
            }
            return result.get();
        }
    }

    /** One set of views, like the four demo apps side by side. */
    static final class Views {
        static final String VIEWS = System.getProperty("stress.views", "sheet,strip,node,fly");
        final boolean useSheet = VIEWS.contains("sheet");
        final boolean useStrip = VIEWS.contains("strip");
        final boolean useNode = VIEWS.contains("node");
        final SheetView sheet = new SheetView();
        final StripSheetView strip = new StripSheetView();
        final NodeSheetView node = new NodeSheetView();
        final FlyOverSheetView fly = VIEWS.contains("fly") && FlyOverSheetView.is3DSupported() ? new FlyOverSheetView() : null;
        final VBox root;
        final List<Object> active = new ArrayList<>();
        double total;

        Views() {
            if (fly != null) {
                fly.setPrefSize(1200, 250);
            }
            ScrollPane sheetScroll = new ScrollPane(sheet);
            sheetScroll.setPrefHeight(300);
            ScrollPane nodeScroll = new ScrollPane(node);
            nodeScroll.setPrefHeight(200);
            root = new VBox();
            if (useSheet) {
                root.getChildren().add(sheetScroll);
            }
            if (useStrip) {
                root.getChildren().add(strip);
            }
            if (useNode) {
                root.getChildren().add(nodeScroll);
            }
            if (fly != null) {
                root.getChildren().add(fly);
            }
        }

        void show(Score score, boolean noClear) {
            active.clear();
            if (!noClear) {
                sheet.noteHighlights().clear();
                sheet.noteBackgrounds().clear();
                strip.noteHighlights().clear();
                strip.noteBackgrounds().clear();
            }
            if (useSheet) {
                sheet.setScore(score);
            }
            strip.setScore(score); // drives the timeline; only added to the scene when selected
            if (useNode) {
                node.setScore(score);
            }
            if (fly != null) {
                fly.setScore(score);
            }
            total = strip.totalDurationQuarters();
        }

        /** One animation frame: cursors move, sounding notes highlight (StripDemoApp/PlaybackSimulator style). */
        void frame(double t, boolean noClear) {
            strip.setCursorTime(t);
            if (fly != null) {
                fly.setCursorTime(t);
            }
            PlaybackTimeline timeline = strip.getTimeline();
            if (timeline == null) {
                return;
            }
            List<NoteAnchor> now = timeline.soundingAt(t);
            if (!noClear) {
                for (Object e : active) {
                    strip.noteHighlights().remove(e);
                    strip.noteBackgrounds().remove(e);
                    sheet.noteHighlights().remove(e);
                }
                active.clear();
            }
            for (NoteAnchor a : now) {
                strip.noteHighlights().put(a.elementRef(), Color.CRIMSON);
                strip.noteBackgrounds().put(a.elementRef(), Color.YELLOW);
                sheet.noteHighlights().put(a.elementRef(), Color.CRIMSON);
                active.add(a.elementRef());
            }
        }
    }
}
