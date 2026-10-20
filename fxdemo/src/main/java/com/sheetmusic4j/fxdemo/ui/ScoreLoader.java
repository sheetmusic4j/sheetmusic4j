package com.sheetmusic4j.fxdemo.ui;

import java.nio.file.Path;
import java.util.function.Consumer;

import com.sheetmusic4j.core.io.ScoreFile;
import com.sheetmusic4j.core.model.Score;
import javafx.concurrent.Task;

/**
 * Loads scores on a background thread - large files take a while to parse -
 * and hands the result back on the FX thread. Only the most recent request
 * counts: starting a new load cancels the previous one, and a load that
 * finishes after a newer one was started is ignored, so a slow file opened
 * first can never replace a fast file opened after it. FX thread only.
 */
public final class ScoreLoader {

    private Task<Score> current;

    /**
     * Load {@code path} and call {@code onLoaded} or {@code onFailed} on the
     * FX thread, unless another load was started in the meantime.
     */
    public void load(Path path, Consumer<Score> onLoaded, Consumer<Throwable> onFailed) {
        if (current != null) {
            current.cancel();
        }
        Task<Score> task = new Task<>() {
            @Override
            protected Score call() {
                return ScoreFile.load(path);
            }
        };
        current = task;
        task.setOnSucceeded(e -> {
            if (current == task) {
                current = null;
                onLoaded.accept(task.getValue());
            }
        });
        task.setOnFailed(e -> {
            if (current == task) {
                current = null;
                onFailed.accept(task.getException());
            }
        });
        Thread thread = new Thread(task, "sheetmusic4j-score-loader");
        thread.setDaemon(true);
        thread.start();
    }
}
