package com.sheetmusic4j.fxviewer;

import java.io.InputStream;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;

/**
 * {@link RenderSurface} backed by a JavaFX {@link GraphicsContext}.
 *
 * <p>On first call to {@link #drawSmuflGlyph}, this surface tries to load
 * {@link SmuflGlyphs#BRAVURA_RESOURCE Bravura.otf} from the fxviewer classpath.
 * If the font is available, subsequent SMuFL draws use it; if not, this method
 * returns {@code false} so {@link ScorePainter} falls back to primitives.
 */
public final class FxRenderSurface implements RenderSurface {

    private static volatile boolean bravuraAttempted;
    private static volatile Font bravuraTemplate;

    private final GraphicsContext gc;

    /** Creates a surface that draws into the given JavaFX graphics context. */
    public FxRenderSurface(GraphicsContext gc) {
        this.gc = gc;
    }

    private static Color toColor(RenderColor color) {
        return Color.rgb(color.red(), color.green(), color.blue());
    }

    @Override
    public void setStroke(RenderColor color) {
        gc.setStroke(toColor(color));
    }

    @Override
    public void setFill(RenderColor color) {
        gc.setFill(toColor(color));
    }

    @Override
    public void setLineWidth(double width) {
        gc.setLineWidth(width);
    }

    @Override
    public void fillRect(double x, double y, double width, double height) {
        gc.fillRect(x, y, width, height);
    }

    @Override
    public void strokeLine(double x1, double y1, double x2, double y2) {
        gc.strokeLine(x1, y1, x2, y2);
    }

    @Override
    public void fillOval(double x, double y, double width, double height) {
        gc.fillOval(x, y, width, height);
    }

    @Override
    public void strokeOval(double x, double y, double width, double height) {
        gc.strokeOval(x, y, width, height);
    }

    @Override
    public void strokeRect(double x, double y, double width, double height) {
        gc.strokeRect(x, y, width, height);
    }

    @Override
    public void strokeText(String text, double x, double y) {
        gc.strokeText(text, x, y);
    }

    @Override
    public void drawText(String text, double x, double y, double fontSize) {
        Font previous = gc.getFont();
        gc.setFont(Font.font(previous != null ? previous.getFamily() : "System", fontSize));
        gc.fillText(text, x, y);
        gc.setFont(previous);
    }

    @Override
    public boolean drawSmuflGlyph(String glyphChars, double x, double y, double sizeHint) {
        Font font = ensureBravura(sizeHint);
        if (font == null) {
            return false;
        }
        Font previous = gc.getFont();
        gc.setFont(Font.font(font.getFamily(), sizeHint));
        gc.fillText(glyphChars, x, y);
        gc.setFont(previous);
        return true;
    }

    private static synchronized Font ensureBravura(double sizeHint) {
        if (bravuraAttempted) {
            return bravuraTemplate == null ? null : bravuraTemplate;
        }
        bravuraAttempted = true;
        try (InputStream in = FxRenderSurface.class.getResourceAsStream(SmuflGlyphs.BRAVURA_RESOURCE)) {
            if (in == null) {
                return null;
            }
            Font loaded = Font.loadFont(in, sizeHint);
            if (loaded != null) {
                bravuraTemplate = loaded;
            }
            return loaded;
        } catch (Exception e) {
            return null;
        }
    }
}
