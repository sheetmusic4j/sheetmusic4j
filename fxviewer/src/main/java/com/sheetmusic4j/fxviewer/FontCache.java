package com.sheetmusic4j.fxviewer;

import java.util.LinkedHashMap;
import java.util.Map;

import javafx.scene.text.Font;

/**
 * Fonts by family and size. {@link Font#font(String, double)} looks the family
 * up and creates a new font on every call, and the surfaces draw every glyph
 * and text with an explicit size, so without a cache painting a score spends
 * most of its allocations on font objects. Only a handful of sizes occur per
 * zoom level; the least recently used fonts are dropped beyond
 * {@value #MAX_FONTS}. Thread-safe: canvases outside a scene may be painted
 * off the FX thread.
 */
final class FontCache {

    private static final int MAX_FONTS = 64;

    private static final Map<Key, Font> FONTS = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Key, Font> eldest) {
            return size() > MAX_FONTS;
        }
    };

    private FontCache() {
    }

    /** The font of the given family and size, as {@link Font#font(String, double)} would create it. */
    static Font font(String family, double size) {
        Key key = new Key(family, size);
        synchronized (FONTS) {
            return FONTS.computeIfAbsent(key, k -> Font.font(k.family(), k.size()));
        }
    }

    private record Key(String family, double size) {
    }
}
