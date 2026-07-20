package com.sheetmusic4j.fxdemo.reference;

import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import javafx.scene.web.WebView;
import javafx.stage.Stage;

import java.awt.image.BufferedImage;
import java.net.URL;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Renders a MusicXML string into a {@link BufferedImage} by driving a JavaFX
 * {@link WebView} that hosts <a href="https://opensheetmusicdisplay.org/">OpenSheetMusicDisplay</a>.
 *
 * <p>Mirrors the pattern used by lottie4j's {@code WebViewScreenshotGenerator}:
 * bootstrap the JavaFX toolkit, load a small local HTML page, feed data into it
 * via {@link javafx.scene.web.WebEngine#executeScript(String)}, wait for a
 * status signal (here via {@code document.title}) and finally snapshot the
 * WebView node.
 *
 * <p>This class is intentionally tolerant: if JavaFX-web is missing, the
 * OSMD bundle is not committed, or a headless display cannot be opened, it
 * returns a {@link Result#missing()} / {@link Result#error(String)} rather than
 * throwing. Callers - both tests and the demo Diff tab - can then require
 * a real result before comparing, for example with JUnit's
 * {@code Assumptions.assumeTrue(...)} in tests.
 */
public final class WebViewReferenceRenderer {

    /**
     * Sentinel value the HTML page sets on {@code document.title} once OSMD has
     * finished laying out the score.
     */
    private static final String TITLE_DONE = "sheet4j:done";
    private static final String TITLE_MISSING = "sheet4j:missing";
    private static final String TITLE_ERROR_PREFIX = "sheet4j:error";

    private static final AtomicBoolean TOOLKIT_STARTED = new AtomicBoolean(false);

    /**
     * Outcome of a reference render.
     *
     * @param image    the rendered image, or {@code null} if unavailable
     * @param missing  {@code true} when OSMD/JavaFX-web is not usable in this environment
     * @param errorMsg human-readable error message, or {@code null} on success
     */
    public record Result(BufferedImage image, boolean missing, String errorMsg) {
        /**
         * Returns whether the render produced an image successfully.
         *
         * @return {@code true} when an image is available and no error was reported
         */
        public boolean isSuccess() {
            return image != null && errorMsg == null && !missing;
        }

        /**
         * Creates a successful render result.
         *
         * @param image rendered image
         * @return success result carrying the rendered image
         */
        public static Result success(BufferedImage image) {
            return new Result(image, false, null);
        }

        /**
         * Creates a result indicating OSMD or JavaFX-web is unavailable.
         *
         * @return unavailable result
         */
        public static Result unavailable() {
            return new Result(null, true, "OSMD bundle not present or JavaFX-web unavailable");
        }

        /**
         * Creates a failed render result with a message.
         *
         * @param message human-readable error description
         * @return error result
         */
        public static Result error(String message) {
            return new Result(null, false, message);
        }
    }

    private final long timeoutMillis;

    /**
     * Creates a renderer with the default timeout.
     */
    public WebViewReferenceRenderer() {
        this(20_000L);
    }

    /**
     * Creates a renderer with a custom timeout.
     *
     * @param timeoutMillis maximum time to wait for OSMD rendering
     */
    public WebViewReferenceRenderer(long timeoutMillis) {
        this.timeoutMillis = timeoutMillis;
    }

    /**
     * Render the given MusicXML string in an off-screen WebView + OSMD and snapshot
     * the result. Blocks up to the configured timeout waiting for OSMD to signal
     * completion via {@code document.title}.
     *
     * @param musicXml MusicXML content to render
     * @param widthPx  target render width in pixels
     * @param heightPx target render height in pixels
     * @return successful, unavailable, or failed render result
     */
    public Result render(String musicXml, int widthPx, int heightPx) {
        URL page = getClass().getResource("/reference/osmd/index.html");
        if (page == null) {
            return Result.unavailable();
        }
        URL bundle = getClass().getResource("/reference/osmd/opensheetmusicdisplay.min.js");
        if (bundle == null) {
            return Result.unavailable();
        }

        try {
            ensureToolkit();
        } catch (Throwable t) {
            return Result.error("JavaFX toolkit not available: " + t.getMessage());
        }

        CompletableFuture<Result> future = new CompletableFuture<>();
        Platform.runLater(() -> renderOnFxThread(page.toExternalForm(), musicXml, widthPx, heightPx, future));
        try {
            return future.get(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (TimeoutException te) {
            return Result.error("Timed out after " + timeoutMillis + " ms waiting for OSMD");
        } catch (Exception e) {
            return Result.error(e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private void renderOnFxThread(String pageUrl, String musicXml, int widthPx, int heightPx,
                                  CompletableFuture<Result> future) {
        WebView view;
        Stage stage;
        try {
            view = new WebView();
            view.setPrefSize(widthPx, heightPx);
            view.setMinSize(widthPx, heightPx);
            view.setMaxSize(widthPx, heightPx);
            stage = new Stage();
            Scene scene = new Scene(view, widthPx, heightPx, Color.WHITE);
            stage.setScene(scene);
            // Do not show(): headless snapshot works on a non-visible Stage/Scene
            // provided the Scene has been attached, which happens here.
        } catch (Throwable t) {
            future.complete(Result.error("Failed to create WebView: " + t.getMessage()));
            return;
        }

        var engine = view.getEngine();
        engine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
            if (newState == Worker.State.FAILED) {
                future.complete(Result.error("Failed to load OSMD HTML page"));
            } else if (newState == Worker.State.SUCCEEDED) {
                triggerRender(engine, musicXml, widthPx);
            }
        });

        engine.titleProperty().addListener((obs, oldTitle, newTitle) -> {
            if (newTitle == null) {
                return;
            }
            if (newTitle.equals(TITLE_DONE)) {
                snapshotAndComplete(view, future);
            } else if (newTitle.equals(TITLE_MISSING)) {
                future.complete(Result.unavailable());
            } else if (newTitle.startsWith(TITLE_ERROR_PREFIX)) {
                future.complete(Result.error(newTitle));
            }
        });

        engine.load(pageUrl);
    }

    private void triggerRender(javafx.scene.web.WebEngine engine, String musicXml, int widthPx) {
        try {
            String js = "renderMusicXml(" + jsQuote(musicXml) + ", " + widthPx + ");";
            engine.executeScript(js);
        } catch (Throwable t) {
            // executeScript failures are surfaced via future
            // (the listener above will still fire on title changes if any).
        }
    }

    private void snapshotAndComplete(WebView view, CompletableFuture<Result> future) {
        try {
            SnapshotParameters params = new SnapshotParameters();
            params.setFill(Color.WHITE);
            WritableImage fxImage = view.snapshot(params, null);
            BufferedImage image = SwingFXUtils.fromFXImage(fxImage, null);
            future.complete(Result.success(image));
        } catch (Throwable t) {
            future.complete(Result.error("Snapshot failed: " + t.getMessage()));
        }
    }

    /**
     * Escape a MusicXML string for embedding as a JavaScript string literal.
     */
    private static String jsQuote(String value) {
        StringBuilder sb = new StringBuilder(value.length() + 32);
        sb.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '"' -> sb.append("\\\"");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\u2028' -> sb.append("\\u2028");
                case '\u2029' -> sb.append("\\u2029");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
        return sb.toString();
    }

    private static void ensureToolkit() {
        if (!TOOLKIT_STARTED.compareAndSet(false, true)) {
            return;
        }
        try {
            Platform.startup(() -> {
            });
        } catch (IllegalStateException alreadyStarted) {
            // Fine: JavaFX was already booted (e.g. by SheetDemoApp).
        }
        // Keep the JavaFX runtime alive across successive renders.
        Platform.setImplicitExit(false);
    }
}
