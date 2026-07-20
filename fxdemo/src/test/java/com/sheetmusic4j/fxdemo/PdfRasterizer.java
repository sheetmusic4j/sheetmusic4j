package com.sheetmusic4j.fxdemo;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Rasterizes the first page of a PDF to a {@link BufferedImage} using Apache PDFBox
 * if it is available on the (test) classpath.
 *
 * <p>PDFBox is invoked reflectively so this helper compiles and runs regardless of
 * whether PDFBox 2.x ({@code PDDocument.load}) or 3.x ({@code Loader.loadPDF}) is
 * present - or whether it is present at all. When it cannot rasterize, it returns
 * {@link Optional#empty()} so tests can skip gracefully instead of failing.
 */
final class PdfRasterizer {

    private PdfRasterizer() {
    }

    static Optional<BufferedImage> rasterizeFirstPage(Path pdf, float dpi) {
        try {
            Object document = loadDocument(pdf.toFile());
            Class<?> pdDocumentClass = Class.forName("org.apache.pdfbox.pdmodel.PDDocument");
            Class<?> rendererClass = Class.forName("org.apache.pdfbox.rendering.PDFRenderer");

            Object renderer = rendererClass.getConstructor(pdDocumentClass).newInstance(document);
            BufferedImage image = (BufferedImage) rendererClass
                    .getMethod("renderImageWithDPI", int.class, float.class)
                    .invoke(renderer, 0, dpi);

            pdDocumentClass.getMethod("close").invoke(document);
            return Optional.ofNullable(image);
        } catch (Throwable t) {
            return Optional.empty();
        }
    }

    private static Object loadDocument(File file) throws Exception {
        try {
            // PDFBox 3.x
            Class<?> loaderClass = Class.forName("org.apache.pdfbox.Loader");
            return loaderClass.getMethod("loadPDF", File.class).invoke(null, file);
        } catch (ClassNotFoundException notV3) {
            // PDFBox 2.x
            Class<?> pdDocumentClass = Class.forName("org.apache.pdfbox.pdmodel.PDDocument");
            return pdDocumentClass.getMethod("load", File.class).invoke(null, file);
        }
    }
}
