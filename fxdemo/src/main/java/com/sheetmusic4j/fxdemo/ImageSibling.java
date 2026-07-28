package com.sheetmusic4j.fxdemo;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Resolves an image file (PNG / JPG / JPEG / GIF / BMP) that accompanies a
 * loaded score, if one exists. Mirrors {@link PdfSibling} but for raster
 * image references — useful when debugging against the pre-rendered example
 * images downloaded alongside ABC or MusicXML samples.
 *
 * <p>Given {@code song.abc} the class will look for {@code song.png},
 * {@code song.jpg}, {@code song.jpeg}, {@code song.gif} and {@code song.bmp}
 * in the same directory, in that order, and return the first one that exists.
 */
public final class ImageSibling {

    /**
     * Sibling image extensions, in probing order. PNG is checked first
     * because it is the lossless format most commonly used for staff-notation
     * screenshots.
     */
    static final List<String> EXTENSIONS = List.of("png", "jpg", "jpeg", "gif", "bmp");

    private ImageSibling() {
    }

    /**
     * Compute the list of candidate sibling image paths for the given score
     * file, in probe order. Returns an empty list if the input already has an
     * image extension (self-reference is not useful).
     *
     * @param scoreFile score file path to transform
     * @return the candidate sibling paths (never {@code null})
     */
    public static List<Path> candidatesFor(Path scoreFile) {
        if (scoreFile == null) {
            return List.of();
        }
        String fileName = scoreFile.getFileName().toString();
        String lower = fileName.toLowerCase(Locale.ROOT);
        for (String ext : EXTENSIONS) {
            if (lower.endsWith("." + ext)) {
                return List.of();
            }
        }
        int dot = fileName.lastIndexOf('.');
        String base = dot >= 0 ? fileName.substring(0, dot) : fileName;
        Path parent = scoreFile.getParent();
        java.util.ArrayList<Path> out = new java.util.ArrayList<>(EXTENSIONS.size());
        for (String ext : EXTENSIONS) {
            String name = base + "." + ext;
            out.add(parent != null ? parent.resolve(name) : Path.of(name));
        }
        return java.util.Collections.unmodifiableList(out);
    }

    /**
     * Resolve the first existing sibling image file for the given score.
     *
     * @param scoreFile score file whose sibling image should be resolved
     * @return the existing sibling image path, or empty if none exist
     */
    public static Optional<Path> existingPathFor(Path scoreFile) {
        for (Path candidate : candidatesFor(scoreFile)) {
            if (Files.isRegularFile(candidate)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }
}
