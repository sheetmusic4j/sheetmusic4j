package com.sheetmusic4j.fxdemo.reference;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Resolves and (on demand) regenerates reference PNG images used by the
 * diagnostic comparator.
 *
 * <p>Committed reference images live under
 * {@code fxdemo/src/test/resources/reference/generated/&lt;sample&gt;.png}.
 * A regeneration is performed only when:
 * <ul>
 *   <li>The system property {@code sheetmusic4j.reference.regenerate} is set
 *       to {@code true}, or</li>
 *   <li>The reference PNG is missing or older than its source MusicXML file.</li>
 * </ul>
 * This keeps CI deterministic (uses committed PNGs) while giving developers a
 * single flag to refresh everything locally.
 */
public final class ReferenceCache {

    private static final String REGENERATE_PROPERTY = "sheetmusic4j.reference.regenerate";

    private final Path referencesDir;
    private final WebViewReferenceRenderer renderer;

    /**
     * Creates a cache backed by the given references directory using the default
     * {@link WebViewReferenceRenderer}.
     *
     * @param referencesDir directory containing reference PNG files
     */
    public ReferenceCache(Path referencesDir) {
        this(referencesDir, new WebViewReferenceRenderer());
    }

    /**
     * Creates a cache backed by the given references directory.
     *
     * @param referencesDir directory containing reference PNG files
     * @param renderer      renderer used when a reference must be generated
     */
    public ReferenceCache(Path referencesDir, WebViewReferenceRenderer renderer) {
        this.referencesDir = referencesDir;
        this.renderer = renderer;
    }

    /**
     * Path where the reference PNG for the given sample should live.
     *
     * @param sampleName sample base name without extension
     * @return expected reference PNG path
     */
    public Path referencePath(String sampleName) {
        return referencesDir.resolve(sampleName + ".png");
    }

    /**
     * True when a committed reference PNG is already present.
     *
     * @param sampleName sample base name without extension
     * @return {@code true} when a reference PNG already exists
     */
    public boolean hasReference(String sampleName) {
        return Files.isRegularFile(referencePath(sampleName));
    }

    /**
     * Load the reference PNG if present.
     *
     * @param sampleName sample base name without extension
     * @return loaded reference image, if available and readable
     */
    public Optional<BufferedImage> load(String sampleName) {
        Path path = referencePath(sampleName);
        if (!Files.isRegularFile(path)) {
            return Optional.empty();
        }
        try {
            BufferedImage image = ImageIO.read(path.toFile());
            return Optional.ofNullable(image);
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    /**
     * Get the reference for the given sample, regenerating it via
     * {@link WebViewReferenceRenderer} only when necessary.
     *
     * @param sampleName base name (no extension) used to key the cache
     * @param xmlSource  path to the source MusicXML file (for freshness comparison)
     * @param musicXml   the MusicXML content to render if regeneration is needed
     * @param widthPx    render width
     * @param heightPx   render height
     * @return the reference image, or a missing/error {@link WebViewReferenceRenderer.Result}
     * @throws IOException if reference output directories or files cannot be written
     */
    public WebViewReferenceRenderer.Result getOrGenerate(String sampleName, Path xmlSource, String musicXml,
                                                         int widthPx, int heightPx) throws IOException {
        Path png = referencePath(sampleName);
        if (!shouldRegenerate(png, xmlSource)) {
            Optional<BufferedImage> cached = load(sampleName);
            if (cached.isPresent()) {
                return WebViewReferenceRenderer.Result.success(cached.get());
            }
        }

        WebViewReferenceRenderer.Result result = renderer.render(musicXml, widthPx, heightPx);
        if (result.isSuccess()) {
            Files.createDirectories(png.getParent());
            ImageIO.write(result.image(), "png", png.toFile());
        }
        return result;
    }

    private boolean shouldRegenerate(Path png, Path xmlSource) {
        if (Boolean.getBoolean(REGENERATE_PROPERTY)) {
            return true;
        }
        if (!Files.isRegularFile(png)) {
            return true;
        }
        if (xmlSource == null || !Files.isRegularFile(xmlSource)) {
            return false;
        }
        try {
            return Files.getLastModifiedTime(xmlSource)
                    .compareTo(Files.getLastModifiedTime(png)) > 0;
        } catch (IOException e) {
            return false;
        }
    }
}
