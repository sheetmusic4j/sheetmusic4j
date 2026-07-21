package com.sheetmusic4j.fxdemo;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

import com.sheetmusic4j.core.model.Score;
import com.sheetmusic4j.engraving.Engraver;
import com.sheetmusic4j.engraving.LayoutOptions;
import com.sheetmusic4j.engraving.LayoutResult;
import com.sheetmusic4j.fxviewer.ScorePainter;

/**
 * Renders a {@link Score} to a {@link BufferedImage} entirely headlessly, using the
 * production {@link Engraver} and {@link ScorePainter}. This mirrors what the JavaFX
 * {@link com.sheetmusic4j.fxviewer.SheetView} draws, allowing pixel comparisons in tests
 * <em>and</em> in the demo's Diff tab.
 */
public final class HeadlessScoreImage {

    /**
     * Minimum image height in pixels. Prevents very short scores from
     * collapsing to a canvas so small that the ink ratio drops to zero.
     */
    private static final int MIN_HEIGHT = 300;

    /**
     * Vertical padding (in pixels) added below the engraved content when the
     * canvas grows to fit the layout.
     */
    private static final int VERTICAL_PAD = 8;

    private HeadlessScoreImage() {
    }

    /**
     * Render a score into an off-screen image sized to fit the engraved
     * layout at the given width. The resulting image is at least
     * {@link #MIN_HEIGHT} pixels tall.
     *
     * @param score  score to render
     * @param width  target image width in pixels
     * @return rendered score image
     */
    public static BufferedImage render(Score score, int width) {
        return render(score, width, MIN_HEIGHT);
    }

    /**
     * Render a score into an off-screen image using the production engraving and
     * painting pipeline. The height acts as a <em>minimum</em>: when the
     * engraved layout is taller than {@code height}, the canvas grows to
     * fit it so nothing is clipped.
     *
     * @param score  score to render
     * @param width  target image width in pixels
     * @param height minimum image height in pixels
     * @return rendered score image
     */
    public static BufferedImage render(Score score, int width, int height) {
        LayoutOptions defaults = LayoutOptions.defaults();
        LayoutOptions options = new LayoutOptions(
                defaults.staffLineGap(),
                defaults.staffSpacing(),
                width,
                defaults.leftMargin(),
                defaults.rightMargin(),
                defaults.topMargin(),
                defaults.measureMinWidth(),
                defaults.fontSize());

        LayoutResult layout = new Engraver().layout(score, options);
        int layoutHeight = (int) Math.ceil(layout.height()) + VERTICAL_PAD;
        int imageHeight = Math.max(height, Math.max(MIN_HEIGHT, layoutHeight));

        BufferedImage image = new BufferedImage(width, imageHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, width, imageHeight);

        AwtRenderSurface surface = new AwtRenderSurface(g);
        new ScorePainter().paint(surface, layout, width, imageHeight);
        g.dispose();
        return image;
    }
}
