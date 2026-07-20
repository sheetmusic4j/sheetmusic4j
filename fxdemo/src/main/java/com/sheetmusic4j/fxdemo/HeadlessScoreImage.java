package com.sheetmusic4j.fxdemo;

import com.sheetmusic4j.core.model.Score;
import com.sheetmusic4j.engraving.Engraver;
import com.sheetmusic4j.engraving.LayoutOptions;
import com.sheetmusic4j.engraving.LayoutResult;
import com.sheetmusic4j.fxviewer.ScorePainter;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/**
 * Renders a {@link Score} to a {@link BufferedImage} entirely headlessly, using the
 * production {@link Engraver} and {@link ScorePainter}. This mirrors what the JavaFX
 * {@link com.sheetmusic4j.fxviewer.SheetView} draws, allowing pixel comparisons in tests
 * <em>and</em> in the demo's Diff tab.
 */
public final class HeadlessScoreImage {

    private HeadlessScoreImage() {
    }

    /**
     * Render a score into an off-screen image using the production engraving and
     * painting pipeline.
     *
     * @param score  score to render
     * @param width  target image width in pixels
     * @param height target image height in pixels
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

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, width, height);

        AwtRenderSurface surface = new AwtRenderSurface(g);
        new ScorePainter().paint(surface, layout, width, height);
        g.dispose();
        return image;
    }
}
