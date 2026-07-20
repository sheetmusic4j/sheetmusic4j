package com.sheetmusic4j.fxviewer;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

/**
 * {@link RenderSurface} backed by a JavaFX {@link GraphicsContext}.
 */
public final class FxRenderSurface implements RenderSurface {

    private final GraphicsContext gc;

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
    public void strokeText(String text, double x, double y) {
        gc.strokeText(text, x, y);
    }
}
