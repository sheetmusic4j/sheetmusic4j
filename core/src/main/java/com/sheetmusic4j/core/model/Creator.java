package com.sheetmusic4j.core.model;

import java.util.Locale;
import java.util.Objects;

/**
 * A named contributor to a {@link Score}: composer, lyricist, arranger,
 * translator, ... Modelled after the MusicXML {@code <creator type="...">}
 * element inside {@code <identification>}.
 *
 * <p>The {@link #role()} is kept as a free-form lowercase string rather than a
 * closed enum so we do not have to enumerate every role vocabulary a MusicXML
 * producer might use.
 *
 * @param role lowercase MusicXML creator {@code type} (e.g. {@code composer},
 *             {@code lyricist}); never {@code null}
 * @param name the human-readable name of the contributor; never {@code null}
 */
public record Creator(String role, String name) {

    /** Default role used when the raw role is missing or blank. */
    public static final String OTHER = "other";

    public Creator {
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(name, "name");
    }

    /**
     * Build a {@code Creator} from a raw MusicXML {@code type} attribute and
     * text content, normalizing the role to lowercase / trimmed and treating
     * a null/blank role as {@link #OTHER}. Returns {@code null} when the name
     * itself is blank.
     */
    public static Creator of(String rawType, String name) {
        if (name == null) {
            return null;
        }
        String trimmedName = name.trim();
        if (trimmedName.isEmpty()) {
            return null;
        }
        String normalizedRole;
        if (rawType == null || rawType.isBlank()) {
            normalizedRole = OTHER;
        } else {
            normalizedRole = rawType.trim().toLowerCase(Locale.ROOT);
        }
        return new Creator(normalizedRole, trimmedName);
    }
}
