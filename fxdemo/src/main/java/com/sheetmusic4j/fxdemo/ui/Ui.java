package com.sheetmusic4j.fxdemo.ui;

import java.util.List;
import java.util.Locale;

import com.sheetmusic4j.core.io.ScoreFile;
import com.sheetmusic4j.core.io.ScoreFormat;
import javafx.geometry.Insets;
import javafx.scene.control.Label;

/**
 * Small helpers shared by the demo's panes.
 */
public final class Ui {

    /** Lower bound of the zoom controls. */
    public static final double ZOOM_MIN = 0.25;
    /** Upper bound of the zoom controls. */
    public static final double ZOOM_MAX = 4.0;
    /** Factor applied per zoom-in / zoom-out step. */
    public static final double ZOOM_STEP = 1.25;

    private Ui() {
    }

    /**
     * A bold pane title.
     */
    public static Label sectionTitle(String text) {
        Label label = new Label(text);
        label.setPadding(new Insets(4));
        label.setStyle("-fx-font-weight: bold;");
        return label;
    }

    /**
     * Clamp a zoom factor to {@link #ZOOM_MIN}..{@link #ZOOM_MAX}.
     */
    public static double clampZoom(double value) {
        return Math.max(ZOOM_MIN, Math.min(ZOOM_MAX, value));
    }

    /**
     * "Zoom 125%" style label text.
     */
    public static String zoomText(double zoom) {
        return String.format(Locale.ROOT, "Zoom %.0f%%", zoom * 100.0);
    }

    /**
     * Every file extension (with leading dot) a score can be loaded from,
     * taken from {@link ScoreFile#formats()} so the demo never lags behind
     * the library.
     */
    public static List<String> scoreExtensions() {
        return ScoreFile.formats().stream()
                .flatMap(format -> format.extensions().stream())
                .map(ext -> "." + ext)
                .sorted()
                .toList();
    }

    /**
     * Whether a file name ends with one of {@link #scoreExtensions()}.
     */
    public static boolean isScoreFile(String fileName) {
        String name = fileName.toLowerCase(Locale.ROOT);
        return scoreExtensions().stream().anyMatch(name::endsWith);
    }

    /**
     * File-chooser glob patterns ({@code *.ext}) for one format.
     */
    public static List<String> globs(ScoreFormat format) {
        return format.extensions().stream().sorted().map(ext -> "*." + ext).toList();
    }
}
