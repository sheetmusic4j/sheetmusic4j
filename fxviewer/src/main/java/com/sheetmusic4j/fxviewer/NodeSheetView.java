package com.sheetmusic4j.fxviewer;

import com.sheetmusic4j.core.model.MusicElement;
import com.sheetmusic4j.core.model.Score;
import com.sheetmusic4j.engraving.Engraver;
import com.sheetmusic4j.engraving.layout.LayoutOptions;
import com.sheetmusic4j.engraving.layout.LayoutResult;
import com.sheetmusic4j.engraving.layout.NoteAnchor;
import com.sheetmusic4j.render.ScorePainter;

import java.util.function.BiConsumer;

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Point2D;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.effect.DropShadow;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.transform.Scale;

/**
 * Retained-mode counterpart of {@link SheetView}: the score is engraved by the
 * same {@link Engraver} and painted by the same {@link ScorePainter}, but into a
 * JavaFX scene graph ({@link NodeRenderSurface}) instead of a canvas. Every
 * note, rest and chord is its own {@link Group} (see {@link #nodeFor}), so it
 * can take mouse and keyboard events, effects, tooltips and accessibility
 * properties like any other node.
 *
 * <p>Interaction is built in: hovering and selecting elements, plus
 * {@link #setOnElementClicked click} and {@link #setOnElementHover hover}
 * callbacks. A click that misses an element's own shapes (a thin stem, the
 * gap between notehead and accidental) still resolves through
 * {@link LayoutResult#hitTest}.
 *
 * <p>The whole score becomes scene graph nodes, so this view suits editing and
 * moderately sized scores; prefer {@link SheetView} for large scores that are
 * only displayed. Changing the score or layout rebuilds the node tree; the
 * nodes obtained from {@link #nodeFor} are then replaced.
 */
public final class NodeSheetView extends Region {

    private static final Color SELECTION_COLOR = Color.DODGERBLUE;
    private static final Color HOVER_COLOR = Color.LIGHTSKYBLUE;

    private final Engraver engraver = new Engraver();
    private final ScorePainter painter = new ScorePainter();
    private final Group scaled = new Group();
    private final Scale scale = new Scale(1, 1, 0, 0);

    private final DoubleProperty systemWidth =
            new SimpleDoubleProperty(this, "systemWidth", LayoutOptions.defaults().systemWidth());
    private final DoubleProperty zoom = new SimpleDoubleProperty(this, "zoom", 1.0);
    private final ObjectProperty<MusicElement> selectedElement =
            new SimpleObjectProperty<>(this, "selectedElement");
    private final ObjectProperty<MusicElement> hoveredElement =
            new SimpleObjectProperty<>(this, "hoveredElement");

    private Score score;
    private LayoutResult layout;
    private NodeRenderSurface surface = new NodeRenderSurface();
    private BiConsumer<MusicElement, MouseEvent> onElementClicked;
    private BiConsumer<MusicElement, MouseEvent> onElementHover;

    /** Creates an empty view at the default engraving width. */
    public NodeSheetView() {
        scaled.getTransforms().add(scale);
        getChildren().add(scaled);
        systemWidth.addListener((obs, oldV, newV) -> rebuild());
        zoom.addListener((obs, oldV, newV) -> applyZoom());
        selectedElement.addListener((obs, oldV, newV) -> {
            setHighlight(oldV, null);
            setHighlight(newV, SELECTION_COLOR);
        });
        hoveredElement.addListener((obs, oldV, newV) -> {
            if (oldV != selectedElement.get()) {
                setHighlight(oldV, null);
            }
            if (newV != null && newV != selectedElement.get()) {
                setHighlight(newV, HOVER_COLOR);
            }
        });
        addEventHandler(MouseEvent.MOUSE_CLICKED, this::handleClick);
        addEventHandler(MouseEvent.MOUSE_MOVED, this::handleMove);
        addEventHandler(MouseEvent.MOUSE_EXITED, e -> hoveredElement.set(null));
        applyZoom();
    }

    /** The score currently displayed, or {@code null}. */
    public Score getScore() {
        return score;
    }

    /** Sets the score to display; engraves it and rebuilds the node tree. */
    public void setScore(Score score) {
        this.score = score;
        rebuild();
    }

    /** The engraved layout of the current score, or {@code null} without a score. */
    public LayoutResult getLayout() {
        return layout;
    }

    /** The system width used by the engraver; changing it re-engraves. */
    public DoubleProperty systemWidthProperty() {
        return systemWidth;
    }

    /** The zoom factor; changing it only rescales the existing nodes. */
    public DoubleProperty zoomProperty() {
        return zoom;
    }

    /** The selected element, or {@code null}. Set it to select programmatically. */
    public ObjectProperty<MusicElement> selectedElementProperty() {
        return selectedElement;
    }

    /** The element under the mouse, or {@code null}. */
    public ObjectProperty<MusicElement> hoveredElementProperty() {
        return hoveredElement;
    }

    /**
     * The group holding all nodes of a note, rest or chord of the current
     * score, or {@code null} when it isn't drawn. Valid until the next rebuild.
     */
    public Group nodeFor(MusicElement element) {
        return surface.nodeFor(element);
    }

    /**
     * Sets a callback for clicks on an element. The click target is picked
     * from the scene graph first, then by the layout's bounding boxes.
     * Selection follows the click; clicking empty space clears it.
     */
    public void setOnElementClicked(BiConsumer<MusicElement, MouseEvent> handler) {
        this.onElementClicked = handler;
    }

    /** Sets a callback for the mouse moving onto a different element (or off all, with {@code null}). */
    public void setOnElementHover(BiConsumer<MusicElement, MouseEvent> handler) {
        this.onElementHover = handler;
    }

    private void rebuild() {
        hoveredElement.set(null);
        MusicElement keep = selectedElement.get();
        surface = new NodeRenderSurface();
        if (score == null) {
            layout = null;
            scaled.getChildren().clear();
        } else {
            LayoutOptions defaults = LayoutOptions.defaults();
            double width = systemWidth.get() > 0 ? systemWidth.get() : defaults.systemWidth();
            layout = engraver.layout(score, defaults.toBuilder().systemWidth(width).build());
            painter.paint(surface, layout, layout.width(), layout.height());
            scaled.getChildren().setAll(surface.getRoot());
            setHighlight(keep, SELECTION_COLOR);
        }
        requestLayout();
    }

    private void applyZoom() {
        double factor = Math.max(zoom.get(), 0.01);
        scale.setX(factor);
        scale.setY(factor);
        requestLayout();
    }

    private void setHighlight(MusicElement element, Color color) {
        Group node = element == null ? null : surface.nodeFor(element);
        if (node != null) {
            node.setEffect(color == null ? null : new DropShadow(6, color));
        }
    }

    /**
     * The element under a mouse event: the element of the scene graph node that
     * was hit, else the nearest element by the layout's bounding boxes, else
     * {@code null}. Use it from your own press/drag handlers.
     */
    public MusicElement pick(MouseEvent e) {
        for (Node n = e.getTarget() instanceof Node t ? t : null; n != null && n != this; n = n.getParent()) {
            if (n.getUserData() instanceof MusicElement element) {
                return element;
            }
        }
        if (layout == null) {
            return null;
        }
        Point2D p = surface.getRoot().sceneToLocal(e.getSceneX(), e.getSceneY());
        double tolerance = LayoutOptions.defaults().staffLineGap() * 0.5;
        return p == null ? null : layout.hitTest(p.getX(), p.getY(), tolerance)
                .map(NoteAnchor::elementRef).orElse(null);
    }

    /**
     * Converts a point in scene coordinates to layout units, the coordinate
     * space of {@link #getLayout()} and of the element nodes' translate
     * properties (so a drag delta in layout units can be added to
     * {@code translateX/Y} directly, whatever the zoom).
     */
    public Point2D sceneToLayout(double sceneX, double sceneY) {
        return surface.getRoot().sceneToLocal(sceneX, sceneY);
    }

    private void handleClick(MouseEvent e) {
        MusicElement element = pick(e);
        selectedElement.set(element);
        if (element != null && onElementClicked != null) {
            onElementClicked.accept(element, e);
        }
    }

    private void handleMove(MouseEvent e) {
        MusicElement element = pick(e);
        if (element != hoveredElement.get()) {
            hoveredElement.set(element);
            if (onElementHover != null) {
                onElementHover.accept(element, e);
            }
        }
    }

    private double contentWidth() {
        return Math.max((layout != null ? layout.width() : systemWidth.get()) * scale.getX(), 1.0);
    }

    private double contentHeight() {
        double fallback = LayoutOptions.defaults().staffHeight() + LayoutOptions.defaults().topMargin() * 2;
        return Math.max((layout != null ? layout.height() : fallback) * scale.getY(), 1.0);
    }

    @Override
    protected double computePrefWidth(double height) {
        return contentWidth();
    }

    @Override
    protected double computePrefHeight(double width) {
        return contentHeight();
    }
}
