package com.sheetmusic4j.fxdemo.ui;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import com.sheetmusic4j.core.model.Score;
import com.sheetmusic4j.engraving.layout.LayoutResult;
import com.sheetmusic4j.fxdemo.reference.DiagnosticComparator;
import com.sheetmusic4j.fxdemo.reference.DiffReportWriter;
import com.sheetmusic4j.fxdemo.reference.ImageStack;
import com.sheetmusic4j.fxdemo.reference.PdfRasterizer;
import com.sheetmusic4j.render.awt.HeadlessScoreImage;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebView;

/**
 * The "Diff" pane: renders the current score headlessly, rasterizes its
 * sibling PDF, compares the two and shows the resulting HTML report. The
 * comparison runs on a background thread.
 */
public final class ReferenceDiffPane {

    /**
     * Fixed engraving width for the comparison, so per-fixture similarity
     * numbers stay comparable across runs regardless of the window size.
     */
    public static final int DIFF_WIDTH = 1000;

    private static final float PDF_DPI =
            (float) Double.parseDouble(System.getProperty("sheetmusic4j.compare.pdf.dpi", "150"));

    private final WebView webView = new WebView();
    private final Label status =
            new Label("Open a MusicXML file with a sibling PDF and click 'Compare against PDF'.");
    private final Button compareButton = new Button("Compare against PDF");
    private final BorderPane pane = new BorderPane(webView);
    private final Runnable onShow;

    private Path file;
    private Score score;
    private Path pdf;

    /**
     * @param onShow called when the pane needs to become visible
     */
    public ReferenceDiffPane(Runnable onShow) {
        this.onShow = onShow;
        compareButton.setOnAction(e -> compareAsync());
        compareButton.setDisable(true);
        HBox toolbar = new HBox(8, compareButton, status);
        toolbar.setPadding(new Insets(4));
        pane.setTop(new VBox(Ui.sectionTitle("Diff (Sheetmusic4J vs sibling PDF)"), toolbar));
    }

    public BorderPane node() {
        return pane;
    }

    /**
     * Set the score to compare, with its sibling PDF and sibling image (used
     * only for the status text).
     */
    public void setSource(Path file, Score score, Optional<Path> pdf, Optional<Path> image) {
        this.file = file;
        this.score = score;
        this.pdf = pdf.orElse(null);
        compareButton.setDisable(pdf.isEmpty());
        if (pdf.isPresent()) {
            status.setText("Click 'Compare against PDF' to render the diff report.");
        } else if (image.isPresent()) {
            status.setText("No sibling PDF for this score - diff disabled. "
                    + "Showing sibling image " + image.get().getFileName() + " instead.");
        } else {
            status.setText("No sibling PDF for this score - diff disabled.");
        }
    }

    /**
     * Forget the current score.
     */
    public void clear() {
        file = null;
        score = null;
        pdf = null;
        compareButton.setDisable(true);
    }

    private void compareAsync() {
        if (file == null || score == null || pdf == null) {
            status.setText("Open a MusicXML file with a sibling PDF first.");
            return;
        }
        onShow.run();
        compareButton.setDisable(true);
        status.setText("Rasterizing sibling PDF...");

        String name = stripExtension(file.getFileName().toString());
        Path pdfPath = pdf;
        Score source = score;
        Task<Path> task = new Task<>() {
            @Override
            protected Path call() throws Exception {
                return compare(name, pdfPath, source);
            }
        };
        task.setOnSucceeded(e -> {
            Path html = task.getValue();
            webView.getEngine().load(html.toUri().toString());
            status.setText("Report: " + html);
            compareButton.setDisable(false);
        });
        task.setOnFailed(e -> {
            Throwable t = task.getException();
            status.setText("Failed: " + (t != null ? t.getMessage() : "unknown"));
            compareButton.setDisable(false);
        });
        Thread thread = new Thread(task, "sheetmusic4j-diff-generator");
        thread.setDaemon(true);
        thread.start();
    }

    private static Path compare(String name, Path pdfPath, Score score) throws Exception {
        OptionalInt count = PdfRasterizer.pageCount(pdfPath);
        if (count.isEmpty()) {
            throw new IllegalStateException("PDFBox unavailable; cannot rasterize " + pdfPath);
        }
        Optional<List<BufferedImage>> pages = PdfRasterizer.rasterizeAllPages(pdfPath, PDF_DPI);
        if (pages.isEmpty()) {
            throw new IllegalStateException("Failed to rasterize " + pdfPath);
        }
        BufferedImage reference = ImageStack.stackVertically(pages.get(), 8, Color.WHITE);

        HeadlessScoreImage.Rendering rendering = HeadlessScoreImage.renderWithLayout(score, DIFF_WIDTH);
        LayoutResult layout = rendering.layout();
        BufferedImage rendered = rendering.image();
        DiagnosticComparator.Diagnostic diagnostic =
                new DiagnosticComparator().compare(rendered, reference, layout);

        Path outDir = Path.of(System.getProperty("java.io.tmpdir"), "sheetmusic4j-diff", name);
        return DiffReportWriter.write(outDir, name,
                rendered, reference, count.getAsInt(), layout.systems().size(), diagnostic);
    }

    private static String stripExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot >= 0 ? fileName.substring(0, dot) : fileName;
    }
}
