package com.sheetmusic4j.fxdemo.reference;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Writes a self-contained HTML report showing the rendered Sheet4j engraving
 * side by side with the reference image and the per-measure diagnostic table.
 *
 * <p>The report layout deliberately keeps everything (CSS, PNG paths) inside the
 * output directory so that a developer can just double-click {@code report.html}
 * and see the diff without a web server.
 */
public final class DiffReportWriter {

    private DiffReportWriter() {
    }

    /**
     * Write a diff report and return the path to the produced HTML file.
     *
     * @param outputDir  directory where {@code rendered.png}, {@code reference.png},
     *                   {@code diff.png} and {@code report.html} are written
     * @param name       fixture name (used only in the report header)
     * @param rendered   the Sheet4j-rendered image
     * @param reference  the reference image
     * @param diagnostic the diagnostic to summarize in the report
     * @return path to the generated {@code report.html} file
     * @throws IOException if the output directory or report files cannot be written
     */
    public static Path write(Path outputDir, String name, BufferedImage rendered, BufferedImage reference,
                             DiagnosticComparator.Diagnostic diagnostic) throws IOException {
        Files.createDirectories(outputDir);

        Path renderedPng = outputDir.resolve("rendered.png");
        Path referencePng = outputDir.resolve("reference.png");
        Path diffPng = outputDir.resolve("diff.png");
        Path html = outputDir.resolve("report.html");

        ImageIO.write(rendered, "png", renderedPng.toFile());
        ImageIO.write(reference, "png", referencePng.toFile());
        ImageIO.write(buildDiffMap(rendered, reference), "png", diffPng.toFile());

        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>\n<html><head><meta charset=\"UTF-8\">\n");
        sb.append("<title>Sheet4j diff report: ").append(escape(name)).append("</title>\n");
        sb.append("<style>\n")
                .append("body { font-family: sans-serif; margin: 16px; }\n")
                .append("h1 { font-size: 18px; }\n")
                .append(".row { display: flex; gap: 12px; flex-wrap: wrap; margin-bottom: 16px; }\n")
                .append(".pane { border: 1px solid #ccc; padding: 6px; }\n")
                .append(".pane h3 { margin: 0 0 6px 0; font-size: 12px; color: #444; }\n")
                .append(".pane img { display: block; max-width: 100%; }\n")
                .append("table { border-collapse: collapse; font-size: 12px; }\n")
                .append("th, td { border: 1px solid #ccc; padding: 4px 8px; text-align: right; }\n")
                .append("th { background: #f0f0f0; }\n")
                .append(".bad { background: #ffe5e5; }\n")
                .append(".summary { font-size: 13px; margin-bottom: 12px; }\n")
                .append("</style>\n</head><body>\n");

        sb.append("<h1>Sheet4j diff: ").append(escape(name)).append("</h1>\n");
        sb.append("<div class=\"summary\">\n")
                .append("Overall similarity: <b>").append(format(diagnostic.overallSimilarity())).append("</b> &middot; ")
                .append("Rendered ink: ").append(format(diagnostic.renderedInkRatio())).append(" &middot; ")
                .append("Reference ink: ").append(format(diagnostic.referenceInkRatio())).append(" &middot; ")
                .append("Rendered staves: ").append(diagnostic.renderedStaves().size()).append(" &middot; ")
                .append("Reference staves: ").append(diagnostic.referenceStaves().size())
                .append("</div>\n");

        sb.append("<div class=\"row\">\n")
                .append("  <div class=\"pane\"><h3>Sheet4j rendering</h3><img src=\"rendered.png\"></div>\n")
                .append("  <div class=\"pane\"><h3>Reference (OSMD)</h3><img src=\"reference.png\"></div>\n")
                .append("  <div class=\"pane\"><h3>Absolute pixel diff</h3><img src=\"diff.png\"></div>\n")
                .append("</div>\n");

        sb.append("<h3>Per-measure similarity</h3>\n<table>\n");
        sb.append("<tr><th>Staff</th><th>Measure</th><th>Number</th><th>Similarity</th></tr>\n");
        double worstThreshold = 0.55;
        for (DiagnosticComparator.MeasureDiff md : diagnostic.measures()) {
            String cls = md.similarity() < worstThreshold ? " class=\"bad\"" : "";
            sb.append("<tr").append(cls).append(">")
                    .append("<td>").append(md.staffIndex()).append("</td>")
                    .append("<td>").append(md.measureIndex()).append("</td>")
                    .append("<td>").append(md.measureNumber()).append("</td>")
                    .append("<td>").append(format(md.similarity())).append("</td>")
                    .append("</tr>\n");
        }
        sb.append("</table>\n");

        long presentCount = diagnostic.glyphs().stream().filter(DiagnosticComparator.GlyphPresence::presentInReference).count();
        sb.append("<h3>Glyph presence</h3>\n")
                .append("<div class=\"summary\">")
                .append(presentCount).append(" / ").append(diagnostic.glyphs().size())
                .append(" engraved glyph anchors have ink in the reference within a small window.")
                .append("</div>\n");

        sb.append("</body></html>\n");
        Files.writeString(html, sb.toString());
        return html;
    }

    private static BufferedImage buildDiffMap(BufferedImage a, BufferedImage b) {
        int w = Math.min(a.getWidth(), b.getWidth());
        int h = Math.min(a.getHeight(), b.getHeight());
        BufferedImage diff = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = diff.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, w, h);
        g.dispose();

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int ra = a.getRGB(x, y);
                int rb = b.getRGB(x, y);
                int grayA = luminance(ra);
                int grayB = luminance(rb);
                int d = Math.abs(grayA - grayB);
                if (d > 15) {
                    diff.setRGB(x, y, new Color(255, Math.max(0, 255 - d * 2), Math.max(0, 255 - d * 2)).getRGB());
                }
            }
        }
        return diff;
    }

    private static int luminance(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        return (int) Math.round(0.299 * r + 0.587 * g + 0.114 * b);
    }

    private static String format(double value) {
        return String.format(java.util.Locale.ROOT, "%.4f", value);
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
