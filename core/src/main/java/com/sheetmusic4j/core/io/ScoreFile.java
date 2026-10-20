package com.sheetmusic4j.core.io;

import java.nio.file.Path;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.Set;

import com.sheetmusic4j.core.model.Score;

/**
 * Convenience facade that loads and saves a {@link Score} by dispatching on the
 * file extension:
 * <ul>
 *   <li>{@code .musicxml}, {@code .xml}, {@code .mxl} &rarr; MusicXML</li>
 *   <li>{@code .mid}, {@code .midi} &rarr; MIDI</li>
 *   <li>{@code .abc} &rarr; ABC notation</li>
 *   <li>{@code .ly} &rarr; LilyPond</li>
 *   <li>{@code .gp} &rarr; Guitar Pro 8 (load only)</li>
 * </ul>
 * plus any other {@link ScoreFormat} found via {@link ServiceLoader}. The
 * built-in formats are themselves services of this module, so this facade
 * does not depend on the format packages.
 */
public final class ScoreFile {

    private static final List<ScoreFormat> FORMATS = discoverFormats();

    private ScoreFile() {
    }

    public static Score load(Path path) {
        return formatFor(path).reader().read(path);
    }

    public static void save(Score score, Path path) {
        ScoreFormat format = formatFor(path);
        format.writer()
                .orElseThrow(() -> new UnsupportedOperationException(
                        "Saving " + format.name() + " files is not supported"))
                .write(score, path);
    }

    /**
     * All known formats: the built-in ones first, then any contributed via
     * {@link ServiceLoader}.
     */
    public static List<ScoreFormat> formats() {
        return FORMATS;
    }

    /**
     * The format a file's extension maps to.
     *
     * @throws IllegalArgumentException when no format claims the extension
     */
    public static ScoreFormat formatFor(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        String ext = dot >= 0 ? name.substring(dot + 1) : "";
        for (ScoreFormat format : FORMATS) {
            if (format.extensions().contains(ext)) {
                return format;
            }
        }
        throw new IllegalArgumentException("Unsupported file extension: " + name);
    }

    private static List<ScoreFormat> discoverFormats() {
        Set<ClassLoader> loaders = new LinkedHashSet<>();
        loaders.add(ScoreFile.class.getClassLoader());
        ClassLoader context = Thread.currentThread().getContextClassLoader();
        if (context != null) {
            loaders.add(context);
        }
        List<ScoreFormat> builtIn = new ArrayList<>();
        List<ScoreFormat> contributed = new ArrayList<>();
        Set<Class<?>> seen = new LinkedHashSet<>();
        for (ClassLoader loader : loaders) {
            for (ServiceLoader.Provider<ScoreFormat> provider
                    : ServiceLoader.load(ScoreFormat.class, loader).stream().toList()) {
                if (seen.add(provider.type())) {
                    (isBuiltIn(provider.type()) ? builtIn : contributed).add(provider.get());
                }
            }
        }
        builtIn.addAll(contributed);
        return List.copyOf(builtIn);
    }

    /**
     * Whether a format ships in the same module/jar as this class, so the
     * built-ins win an extension clash wherever a contributed format happens
     * to sit on the class or module path.
     */
    private static boolean isBuiltIn(Class<?> type) {
        if (type.getModule().isNamed()) {
            return type.getModule() == ScoreFile.class.getModule();
        }
        return Objects.equals(location(type), location(ScoreFile.class));
    }

    private static Object location(Class<?> type) {
        CodeSource source = type.getProtectionDomain().getCodeSource();
        return source == null ? null : source.getLocation();
    }
}
