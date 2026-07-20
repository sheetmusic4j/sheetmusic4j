package com.sheetmusic4j.fxviewer;

/**
 * A simple, framework-agnostic RGB color (channels 0-255). Keeps
 * {@link RenderSurface} free of any UI-toolkit types.
 */
public record RenderColor(int red, int green, int blue) {

    public static final RenderColor BLACK = new RenderColor(0, 0, 0);
    public static final RenderColor WHITE = new RenderColor(255, 255, 255);
}
