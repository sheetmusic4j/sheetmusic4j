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

    public ReferenceCache(Path referencesDir) {
        this(referencesDir, new WebViewReferenceRenderer());
    }

    public ReferenceCache(Path referencesDir, WebViewReferenceRenderer renderer) {
        this.referencesDir = referencesDir;
        this.renderer = renderer;
    }

    /**
     * Path where the reference PNG for the given sample should live.
     */
    public Path referencePath(String sampleName) {
        return referencesDir.resolve(sampleName + ".png");
    }

    /**
     * True when a committed reference PNG is already present.
     */
    public boolean hasReference(String sampleName) {
        return Files.isRegularFile(referencePath(sampleName));
    }

    /**
     * Load the reference PNG if present.
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
