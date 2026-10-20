package com.sheetmusic4j.render;

/**
 * A simple, framework-agnostic RGBA color (channels 0-255). Keeps
 * {@link RenderSurface} free of any UI-toolkit types.
 *
 * <p>An {@link #alpha()} channel is included so semi-transparent fills -
 * used for note-background highlights - can be represented without adding
 * a separate colour type. The three-argument constructor
 * {@link #RenderColor(int, int, int)} defaults {@code alpha} to {@code 255}
 * so all pre-alpha callers stay fully opaque.
 *
 * @param red   red channel, 0-255
 * @param green green channel, 0-255
 * @param blue  blue channel, 0-255
 * @param alpha alpha channel, 0-255 ({@code 0} = fully transparent,
 *              {@code 255} = fully opaque)
 */
public record RenderColor(int red, int green, int blue, int alpha) {

    public static final RenderColor BLACK = new RenderColor(0, 0, 0);
    public static final RenderColor WHITE = new RenderColor(255, 255, 255);

    /**
     * Backwards-compatible constructor for fully-opaque colours. Sets
     * {@link #alpha} to {@code 255}.
     */
    public RenderColor(int red, int green, int blue) {
        this(red, green, blue, 255);
    }
}
