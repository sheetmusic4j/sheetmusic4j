package com.sheetmusic4j.fxviewer;

import com.sheetmusic4j.core.model.Score;
import com.sheetmusic4j.engraving.Engraver;
import com.sheetmusic4j.engraving.LayoutOptions;
import com.sheetmusic4j.engraving.LayoutResult;
import javafx.scene.canvas.Canvas;
import javafx.scene.layout.Region;

/**
 * A JavaFX control that renders a {@link Score}. It engraves the score into a
 * {@link LayoutResult} via {@link Engraver} and draws it on a {@link Canvas},
 * re-laying-out and redrawing when resized or when a new score is set.
 */
public final class SheetView extends Region {

    private final Canvas canvas = new Canvas();
    private final Engraver engraver = new Engraver();
    private final ScoreRenderer renderer = new ScoreRenderer();

    private Score score;

    public SheetView() {
        getChildren().add(canvas);
        widthProperty().addListener((obs, oldV, newV) -> requestLayout());
        heightProperty().addListener((obs, oldV, newV) -> requestLayout());
        setMinSize(200, 120);
        setPrefSize(1000, 400);
    }

    public void setScore(Score score) {
        this.score = score;
        requestLayout();
    }

    public Score getScore() {
        return score;
    }

    @Override
    protected void layoutChildren() {
        double w = Math.max(getWidth(), getMinWidth());
        double h = Math.max(getHeight(), getMinHeight());
        canvas.setWidth(w);
        canvas.setHeight(h);
        canvas.relocate(0, 0);
        redraw();
    }

    private void redraw() {
        if (canvas.getWidth() <= 0 || canvas.getHeight() <= 0) {
            return;
        }
        if (score == null) {
            canvas.getGraphicsContext2D().clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
            return;
        }
        LayoutOptions options = layoutOptions();
        LayoutResult layout = engraver.layout(score, options);
        renderer.render(canvas.getGraphicsContext2D(), layout);
    }

    private LayoutOptions layoutOptions() {
        LayoutOptions defaults = LayoutOptions.defaults();
        double width = canvas.getWidth() > 0 ? canvas.getWidth() : defaults.systemWidth();
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
