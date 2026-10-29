package com.sheetmusic4j.fxdemo.sibling;

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
     *
     * @param scoreFile score file path to transform
     * @return sibling PDF path, if applicable
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
     *
     * @param scoreFile score file whose sibling PDF should be resolved
     * @return existing sibling PDF path, if present
     */
    public static Optional<Path> existingPathFor(Path scoreFile) {
        return pathFor(scoreFile).filter(Files::isRegularFile);
    }

    /**
     * The PDF to show next to a loaded file for comparison: the file itself when it is an existing PDF (an engraved
     * PDF is read as a score and is its own reference), otherwise its sibling PDF if there is one.
     *
     * @param loadedFile the file that was loaded as a score
     * @return the reference PDF, if any
     */
    public static Optional<Path> referenceFor(Path loadedFile) {
        if (loadedFile != null && loadedFile.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".pdf")
                && Files.isRegularFile(loadedFile)) {
            return Optional.of(loadedFile);
        }
        return existingPathFor(loadedFile);
    }
}
