package com.sheetmusic4j.fxdemo;

import com.sheetmusic4j.fxviewer.RenderColor;
import com.sheetmusic4j.fxviewer.RenderSurface;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;

/**
 * A headless {@link RenderSurface} backed by AWT {@link Graphics2D}. Used to render
 * the score with the exact same {@link com.sheetmusic4j.fxviewer.ScorePainter} logic
 * as the on-screen JavaFX view, but into a {@link java.awt.image.BufferedImage}.
 */
public final class AwtRenderSurface implements RenderSurface {

    private final Graphics2D g;
    private Color stroke = Color.BLACK;
    private Color fill = Color.BLACK;

    /**
     * Creates an AWT-backed render surface.
     *
     * @param g graphics context to draw into
     */
    public AwtRenderSurface(Graphics2D g) {
        this.g = g;
    }

    private static Color toAwt(RenderColor color) {
        return new Color(color.red(), color.green(), color.blue());
    }

    @Override
    public void setStroke(RenderColor color) {
        this.stroke = toAwt(color);
    }

    @Override
    public void setFill(RenderColor color) {
        this.fill = toAwt(color);
    }

    @Override
    public void setLineWidth(double width) {
        g.setStroke(new BasicStroke((float) width));
    }

    @Override
    public void fillRect(double x, double y, double width, double height) {
        g.setColor(fill);
        g.fillRect((int) Math.round(x), (int) Math.round(y),
                (int) Math.round(width), (int) Math.round(height));
    }

    @Override
    public void strokeLine(double x1, double y1, double x2, double y2) {
        g.setColor(stroke);
        g.drawLine((int) Math.round(x1), (int) Math.round(y1),
                (int) Math.round(x2), (int) Math.round(y2));
    }

    @Override
    public void fillOval(double x, double y, double width, double height) {
        g.setColor(fill);
        g.fillOval((int) Math.round(x), (int) Math.round(y),
                (int) Math.round(width), (int) Math.round(height));
    }

    @Override
    public void strokeOval(double x, double y, double width, double height) {
        g.setColor(stroke);
        g.drawOval((int) Math.round(x), (int) Math.round(y),
                (int) Math.round(width), (int) Math.round(height));
    }

    @Override
    public void strokeText(String text, double x, double y) {
        g.setColor(stroke);
        g.drawString(text, (float) x, (float) y);
    }
}
