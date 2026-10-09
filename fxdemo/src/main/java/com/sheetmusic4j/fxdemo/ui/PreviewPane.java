package com.sheetmusic4j.fxdemo.ui;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import com.dlsc.pdfviewfx.PDFView;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

/**
 * The "Preview" pane showing a score's reference - its sibling PDF, or a
 * sibling image with its own zoom controls.
 */
public final class PreviewPane {

    private final PDFView pdfView = new PDFView();
    private final ImageView imageView = new ImageView();
    private final ScrollPane imageScroll = new ScrollPane(imageView);
    private final DoubleProperty imageZoom = new SimpleDoubleProperty(1.0);
    private final Label zoomLabel = new Label();
    private final HBox imageToolbar;
    private final BorderPane pane = new BorderPane(pdfView);

    public PreviewPane() {
        pdfView.setShowThumbnails(false);
        imageView.setPreserveRatio(true);
        imageView.setSmooth(true);
        imageView.fitWidthProperty().bind(imageScroll.widthProperty().subtract(4).multiply(imageZoom));
        imageScroll.setFitToWidth(true);
        imageScroll.setPannable(true);
        imageScroll.addEventFilter(ScrollEvent.SCROLL, this::handleImageScroll);
        imageToolbar = buildImageToolbar();
        pane.setTop(Ui.sectionTitle("Preview"));
    }

    public BorderPane node() {
        return pane;
    }

    /**
     * Show a PDF.
     *
     * @throws IOException when the PDF cannot be read
     */
    public void showPdf(Path pdf) throws IOException {
        try (InputStream in = Files.newInputStream(pdf)) {
            pdfView.load(in);
        }
        pane.setTop(Ui.sectionTitle("Preview"));
        pane.setCenter(pdfView);
    }

    /**
     * Show an image at 100% zoom.
     *
     * @throws IOException when the image cannot be read
     */
    public void showImage(Path image) throws IOException {
        try (InputStream in = Files.newInputStream(image)) {
            imageView.setImage(new Image(in));
        }
        setImageZoom(1.0);
        pane.setTop(imageToolbar);
        pane.setCenter(imageScroll);
    }

    /**
     * Ctrl/Cmd + scroll zooms the image preview in place of the ScrollPane's
     * normal vertical scrolling; plain scroll/drag keeps panning the image.
     */
    private void handleImageScroll(ScrollEvent event) {
        if (!event.isShortcutDown()) {
            return;
        }
        setImageZoom(imageZoom.get() * (event.getDeltaY() > 0 ? Ui.ZOOM_STEP : 1.0 / Ui.ZOOM_STEP));
        event.consume();
    }

    private HBox buildImageToolbar() {
        Button zoomOut = new Button("-");
        zoomOut.setOnAction(e -> setImageZoom(imageZoom.get() / Ui.ZOOM_STEP));
        Button zoomIn = new Button("+");
        zoomIn.setOnAction(e -> setImageZoom(imageZoom.get() * Ui.ZOOM_STEP));
        Button zoomReset = new Button("100%");
        zoomReset.setOnAction(e -> setImageZoom(1.0));
        zoomLabel.setPadding(new Insets(0, 8, 0, 0));
        zoomLabel.setText(Ui.zoomText(imageZoom.get()));

        HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Node title = Ui.sectionTitle("Preview");
        HBox bar = new HBox(8, title, spacer, zoomOut, zoomIn, zoomReset, zoomLabel);
        bar.setPadding(new Insets(4));
        return bar;
    }

    private void setImageZoom(double value) {
        imageZoom.set(Ui.clampZoom(value));
        zoomLabel.setText(Ui.zoomText(imageZoom.get()));
    }
}
