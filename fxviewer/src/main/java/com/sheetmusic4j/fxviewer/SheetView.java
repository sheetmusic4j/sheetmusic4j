package com.sheetmusic4j.fxviewer;

import java.util.EnumSet;

import com.sheetmusic4j.core.model.Score;
import com.sheetmusic4j.engraving.Engraver;
import com.sheetmusic4j.engraving.LayoutOptions;
import com.sheetmusic4j.engraving.LayoutResult;
import com.sheetmusic4j.engraving.TextPlacement;

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableSet;
import javafx.collections.SetChangeListener;
import javafx.scene.canvas.Canvas;
import javafx.scene.layout.Region;

/**
 * A JavaFX control that renders a {@link Score}. It engraves the score into a
 * {@link LayoutResult} via {@link Engraver} and draws it on a {@link Canvas}.
 *
 * <p>The view is <em>content-sized</em>: after each engrave, the underlying
 * canvas and the region's preferred/min/max sizes track {@link LayoutResult}'s
 * width and height. That way, when the view is wrapped in a
 * {@code ScrollPane}, the pane sees the real content size and shows scrollbars
 * whenever the content is larger than the viewport.
 *
 * <p>Callers can override the engraving width via {@link #setSystemWidth(double)}
 * (or the {@link #systemWidthProperty()} JavaFX property, e.g. by binding it to
 * a container's width). The default is {@link LayoutOptions#defaults()}
 * {@code .systemWidth()}.
 */
public final class SheetView extends Region {

    private static final double FALLBACK_HEIGHT = LayoutOptions.defaults().staffHeight()
            + LayoutOptions.defaults().topMargin() * 2;

    private final Canvas canvas = new Canvas();
    private final Engraver engraver = new Engraver();
    private final ScoreRenderer renderer = new ScoreRenderer();

    private final DoubleProperty systemWidth =
            new SimpleDoubleProperty(this, "systemWidth", LayoutOptions.defaults().systemWidth());

    private final ObservableSet<TextPlacement.Category> hiddenTextCategories =
            FXCollections.observableSet(EnumSet.noneOf(TextPlacement.Category.class));

    private Score score;

    /** Creates an empty score view at the default engraving width. */
    public SheetView() {
        getChildren().add(canvas);
        systemWidth.addListener((obs, oldV, newV) -> rebuild());
        hiddenTextCategories.addListener((SetChangeListener<TextPlacement.Category>) change -> rebuild());
        // Initial empty canvas at the default width; setScore replaces it.
        canvas.setWidth(systemWidth.get());
        canvas.setHeight(FALLBACK_HEIGHT);
        setMinSize(200, 120);
        setPrefSize(canvas.getWidth(), canvas.getHeight());
    }

    /** Sets the score to display and rebuilds the engraved layout. */
    public void setScore(Score score) {
        this.score = score;
        rebuild();
    }

    /** Returns the score currently displayed by this view, or {@code null}. */
    public Score getScore() {
        return score;
    }

    /**
     * The system width used by the engraver. Changing this triggers a rebuild.
     * Callers can bind this to a container's width (e.g., the ScrollPane
     * viewport) to make the score reflow while still relying on the layout
     * to report the actual content size.
     *
     * @return the writable width property used by the engraver
     */
    public DoubleProperty systemWidthProperty() {
        return systemWidth;
    }

    /** Returns the current system width used by the engraver. */
    public double getSystemWidth() {
        return systemWidth.get();
    }

    /** Updates the system width used by the engraver, if the width is positive. */
    public void setSystemWidth(double width) {
        if (width > 0) {
            systemWidth.set(width);
        }
    }

    /**
     * Live-observable set of {@link TextPlacement.Category text categories}
     * that this view should hide. Mutations trigger a rebuild.
     *
     * @return the observable set (never {@code null})
     */
    public ObservableSet<TextPlacement.Category> hiddenTextCategoriesProperty() {
        return hiddenTextCategories;
    }

    /**
     * Recompute the layout for the current score, resize the canvas to the
     * layout's content extent, and redraw. Also updates the region's
     * preferred/min sizes so a wrapping {@link javafx.scene.control.ScrollPane}
     * discovers the real content dimensions and shows scrollbars.
     */
    private void rebuild() {
        if (score == null) {
            canvas.setWidth(systemWidth.get());
            canvas.setHeight(FALLBACK_HEIGHT);
            canvas.getGraphicsContext2D().clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
        } else {
            LayoutResult layout = engraver.layout(score, layoutOptions());
            canvas.setWidth(Math.max(layout.width(), 1.0));
            canvas.setHeight(Math.max(layout.height(), 1.0));
            renderer.setHiddenTextCategories(hiddenTextCategories);
            renderer.render(canvas.getGraphicsContext2D(), layout);
        }
        setPrefSize(canvas.getWidth(), canvas.getHeight());
        setMinSize(Math.min(200, canvas.getWidth()), Math.min(120, canvas.getHeight()));
        setMaxSize(canvas.getWidth(), canvas.getHeight());
        requestLayout();
        if (getParent() != null) {
            getParent().requestLayout();
        }
    }

    @Override
    protected void layoutChildren() {
        canvas.relocate(0, 0);
    }

    @Override
    protected double computePrefWidth(double height) {
        return canvas.getWidth();
    }

    @Override
    protected double computePrefHeight(double width) {
        return canvas.getHeight();
    }

    @Override
    protected double computeMinWidth(double height) {
        return Math.min(200, canvas.getWidth());
    }

    @Override
    protected double computeMinHeight(double width) {
        return Math.min(120, canvas.getHeight());
    }

    @Override
    protected double computeMaxWidth(double height) {
        return canvas.getWidth();
    }

    @Override
    protected double computeMaxHeight(double width) {
        return canvas.getHeight();
    }

    private LayoutOptions layoutOptions() {
        LayoutOptions defaults = LayoutOptions.defaults();
        double width = systemWidth.get() > 0 ? systemWidth.get() : defaults.systemWidth();
        return new LayoutOptions(
                defaults.staffLineGap(),
                defaults.staffSpacing(),
                width,
                defaults.leftMargin(),
                defaults.rightMargin(),
                defaults.topMargin(),
                defaults.measureMinWidth(),
                defaults.fontSize());
    }
}
