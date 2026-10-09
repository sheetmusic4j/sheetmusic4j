package com.sheetmusic4j.fxviewer;

import java.util.IdentityHashMap;
import java.util.Optional;

import com.sheetmusic4j.core.model.Accidental;
import com.sheetmusic4j.core.model.MusicElement;
import com.sheetmusic4j.render.RenderColor;
import javafx.collections.FXCollections;
import javafx.collections.MapChangeListener;
import javafx.collections.ObservableMap;
import javafx.scene.paint.Color;

/**
 * The per-element overlays shared by {@link SheetView} and
 * {@link StripSheetView}: highlight tints, background rectangles and
 * accidental overlays. Keys are compared by identity, so callers use the
 * exact {@link MusicElement} instances of the displayed score. Every
 * mutation only repaints - no re-engrave - which keeps the overlays cheap
 * enough to drive from real-time MIDI events.
 */
final class NoteDecorations {

    private final ObservableMap<MusicElement, Color> highlights =
            FXCollections.observableMap(new IdentityHashMap<>());

    private final ObservableMap<MusicElement, Color> backgrounds =
            FXCollections.observableMap(new IdentityHashMap<>());

    private final ObservableMap<MusicElement, Accidental> accidentals =
            FXCollections.observableMap(new IdentityHashMap<>());

    /**
     * Feed the overlays into {@code renderer} and call {@code repaint}
     * whenever any of them changes.
     */
    NoteDecorations(ScoreRenderer renderer, Runnable repaint) {
        renderer.setNoteColorProvider(element -> toRenderColor(highlights.get(element)));
        renderer.setNoteBackgroundProvider(element -> toRenderColor(backgrounds.get(element)));
        renderer.setNoteAccidentalProvider(element -> Optional.ofNullable(accidentals.get(element)));
        highlights.addListener((MapChangeListener<MusicElement, Color>) change -> repaint.run());
        backgrounds.addListener((MapChangeListener<MusicElement, Color>) change -> repaint.run());
        accidentals.addListener((MapChangeListener<MusicElement, Accidental>) change -> repaint.run());
    }

    ObservableMap<MusicElement, Color> highlights() {
        return highlights;
    }

    ObservableMap<MusicElement, Color> backgrounds() {
        return backgrounds;
    }

    ObservableMap<MusicElement, Accidental> accidentals() {
        return accidentals;
    }

    /**
     * Convert a JavaFX colour (including its opacity) to the toolkit-neutral
     * {@link RenderColor} the painter understands.
     */
    static Optional<RenderColor> toRenderColor(Color c) {
        if (c == null) {
            return Optional.empty();
        }
        return Optional.of(new RenderColor(
                (int) Math.round(c.getRed() * 255),
                (int) Math.round(c.getGreen() * 255),
                (int) Math.round(c.getBlue() * 255),
                (int) Math.round(c.getOpacity() * 255)));
    }
}
