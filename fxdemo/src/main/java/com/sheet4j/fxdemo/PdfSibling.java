package com.sheet4j.fxdemo;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;

/**
 * Resolves the PDF file that accompanies a loaded score, if one exists.
 *
 * <p>A score file such as {@code song.musicxml} is considered to have a companion
 * PDF at {@code song.pdf} in the same directory.
 */
public final class PdfSibling {

    private PdfSibling() {
    }

    /**
     * Compute the sibling PDF path for the given score file by replacing its
     * extension with {@code .pdf}. Returns empty if the input already is a PDF.
     */
    public static Optional<Path> pathFor(Path scoreFile) {
        if (scoreFile == null) {
            return Optional.empty();
        }
        String fileName = scoreFile.getFileName().toString();
        if (fileName.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            return Optional.empty();
        }
        int dot = fileName.lastIndexOf('.');
        String base = dot >= 0 ? fileName.substring(0, dot) : fileName;
        Path parent = scoreFile.getParent();
        Path pdf = parent != null ? parent.resolve(base + ".pdf") : Path.of(base + ".pdf");
        return Optional.of(pdf);
    }

    /**
     * Resolve the sibling PDF only if it exists as a regular file.
     */
    public static Optional<Path> existingPathFor(Path scoreFile) {
        return pathFor(scoreFile).filter(Files::isRegularFile);
    }
}
