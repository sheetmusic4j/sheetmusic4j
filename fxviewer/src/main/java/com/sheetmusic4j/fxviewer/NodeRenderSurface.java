package com.sheetmusic4j.fxviewer;

import com.sheetmusic4j.render.RenderColor;
import com.sheetmusic4j.render.RenderSurface;

import java.util.IdentityHashMap;
import java.util.Map;

import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.paint.Color;
import javafx.scene.shape.CubicCurve;
import javafx.scene.shape.Ellipse;
import javafx.scene.shape.Line;
import javafx.scene.shape.QuadCurve;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.Shape;
import javafx.scene.text.Font;
import javafx.scene.text.Text;

/**
 * {@link RenderSurface} that builds a retained JavaFX scene graph instead of
 * drawing into a canvas. Every primitive becomes a {@link Node}, and the
 * primitives between {@link #beginElement(Object)} and {@link #endElement()}
 * are collected in one {@link Group} per source element. That group is
 * reachable through {@link #nodeFor(Object)} (and carries the element as its
 * {@link Node#getUserData() user data}), so mouse, keyboard and effect
 * handling can be attached to each note, rest or chord individually.
 *
 * <p>Primitives drawn outside any element go straight into {@link #getRoot()}.
 * A surface is single-use: create a new one for every paint of a layout.
 */
public final class NodeRenderSurface implements RenderSurface {

    private final Group root = new Group();
    private final Map<Object, Group> elementGroups = new IdentityHashMap<>();

    private Group current = root;
    private Color stroke = Color.BLACK;
    private Color fill = Color.BLACK;
    private double lineWidth = 1.0;

    /** Creates an empty surface. */
    public NodeRenderSurface() {
    }

    /** The scene graph built so far; the primitives are its descendants. */
    public Group getRoot() {
        return root;
    }

    /**
     * The group holding everything drawn for the given source element, or
     * {@code null} when nothing was drawn for it. Looked up by identity.
     */
    public Group nodeFor(Object source) {
        return elementGroups.get(source);
    }

    /** All element groups, keyed by source element (identity semantics). */
    public Map<Object, Group> elementNodes() {
        return Map.copyOf(elementGroups);
    }

    private static Color toColor(RenderColor color) {
        return Color.rgb(color.red(), color.green(), color.blue(), color.alpha() / 255.0);
    }

    private <T extends Node> T add(T node) {
        current.getChildren().add(node);
        return node;
    }

    private <T extends Shape> T filled(T shape) {
        shape.setFill(fill);
        shape.setStroke(null);
        return add(shape);
    }

    private <T extends Shape> T stroked(T shape) {
        shape.setFill(null);
        shape.setStroke(stroke);
        shape.setStrokeWidth(lineWidth);
        return add(shape);
    }

    @Override
    public void beginElement(Object source) {
        current = elementGroups.computeIfAbsent(source, key -> {
            Group group = new Group();
            group.setUserData(key);
            root.getChildren().add(group);
            return group;
        });
    }

    @Override
    public void endElement() {
        current = root;
    }

    @Override
    public void setStroke(RenderColor color) {
        stroke = toColor(color);
    }

    @Override
    public void setFill(RenderColor color) {
        fill = toColor(color);
    }

    @Override
    public void setLineWidth(double width) {
        lineWidth = width;
    }

    @Override
    public void fillRect(double x, double y, double width, double height) {
        filled(new Rectangle(x, y, width, height));
    }

    @Override
    public void strokeRect(double x, double y, double width, double height) {
        stroked(new Rectangle(x, y, width, height));
    }

    @Override
    public void fillRoundedRect(double x, double y, double width, double height,
                                double arcWidth, double arcHeight, RenderColor color) {
        Rectangle rect = new Rectangle(x, y, width, height);
        rect.setArcWidth(arcWidth);
        rect.setArcHeight(arcHeight);
        rect.setFill(toColor(color));
        add(rect);
    }

    @Override
    public void strokeLine(double x1, double y1, double x2, double y2) {
        stroked(new Line(x1, y1, x2, y2));
    }

    @Override
    public void fillOval(double x, double y, double width, double height) {
        filled(new Ellipse(x + width / 2.0, y + height / 2.0, width / 2.0, height / 2.0));
    }

    @Override
    public void strokeOval(double x, double y, double width, double height) {
        stroked(new Ellipse(x + width / 2.0, y + height / 2.0, width / 2.0, height / 2.0));
    }

    @Override
    public void strokeQuadCurve(double x1, double y1, double cx, double cy, double x2, double y2) {
        stroked(new QuadCurve(x1, y1, cx, cy, x2, y2));
    }

    @Override
    public void strokeCubicCurve(double x1, double y1, double c1x, double c1y,
                                 double c2x, double c2y, double x2, double y2) {
        stroked(new CubicCurve(x1, y1, c1x, c1y, c2x, c2y, x2, y2));
    }

    @Override
    public void strokeText(String text, double x, double y) {
        Text node = new Text(x, y, text);
        node.setFill(null);
        node.setStroke(stroke);
        node.setStrokeWidth(lineWidth);
        add(node);
    }

    @Override
    public void drawText(String text, double x, double y, double fontSize) {
        Text node = new Text(x, y, text);
        node.setFont(Font.font(Font.getDefault().getFamily(), fontSize));
        node.setFill(fill);
        node.setPickOnBounds(true);
        add(node);
    }

    @Override
    public boolean drawSmuflGlyph(String glyphChars, double x, double y, double sizeHint) {
        Font font = FxRenderSurface.ensureBravura(sizeHint);
        if (font == null) {
            return false;
        }
        Text node = new Text(x, y, glyphChars);
        node.setFont(Font.font(font.getFamily(), sizeHint));
        node.setFill(fill);
        node.setPickOnBounds(true);
        add(node);
        return true;
    }
}
