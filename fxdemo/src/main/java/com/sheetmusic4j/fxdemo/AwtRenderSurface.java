package com.sheetmusic4j.fxdemo;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.io.InputStream;

import com.sheetmusic4j.fxviewer.RenderColor;
import com.sheetmusic4j.fxviewer.RenderSurface;
import com.sheetmusic4j.fxviewer.SmuflGlyphs;

/**
 * A headless {@link RenderSurface} backed by AWT {@link Graphics2D}. Used to render
 * the score with the exact same {@link com.sheetmusic4j.fxviewer.ScorePainter} logic
 * as the on-screen JavaFX view, but into a {@link java.awt.image.BufferedImage}.
 *
 * <p>Loads Bravura from the fxviewer classpath on first SMuFL glyph draw when
 * available; otherwise reports the glyph as unrendered so the painter falls
 * back to primitive shapes.
 */
public final class AwtRenderSurface implements RenderSurface {

    private static volatile boolean bravuraAttempted;
    private static volatile Font bravuraTemplate;

    private final Graphics2D g;
    private Color stroke = Color.BLACK;
    private Color fill = Color.BLACK;

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

    @Override
    public void drawText(String text, double x, double y, double fontSize) {
        Font previous = g.getFont();
        g.setFont(previous.deriveFont((float) fontSize));
        g.setColor(fill);
        g.drawString(text, (float) x, (float) y);
        g.setFont(previous);
    }

    @Override
    public boolean drawSmuflGlyph(String glyphChars, double x, double y, double sizeHint) {
        Font font = ensureBravura();
        if (font == null) {
            return false;
        }
        Font previous = g.getFont();
        g.setFont(font.deriveFont((float) sizeHint));
        g.setColor(fill);
        g.drawString(glyphChars, (float) x, (float) y);
        g.setFont(previous);
        return true;
    }

    private static synchronized Font ensureBravura() {
        if (bravuraAttempted) {
            return bravuraTemplate;
        }
        bravuraAttempted = true;
        try (InputStream in = AwtRenderSurface.class.getResourceAsStream(SmuflGlyphs.BRAVURA_RESOURCE)) {
            if (in == null) {
                return null;
            }
            Font loaded = Font.createFont(Font.TRUETYPE_FONT, in);
            GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(loaded);
            bravuraTemplate = loaded;
            return loaded;
        } catch (Exception e) {
            return null;
        }
    }
}
