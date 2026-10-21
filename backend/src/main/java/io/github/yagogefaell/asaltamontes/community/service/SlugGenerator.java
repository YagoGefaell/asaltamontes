package io.github.yagogefaell.asaltamontes.community.service;

import java.text.Normalizer;
import java.util.Locale;

/**
 * URL slug of a community name (T14): lowercase ASCII letters, digits and single hyphens.
 * "Trail Vigo / Sur #1" → "trail-vigo-sur-1". Same rules as the backfill in V4.
 */
public final class SlugGenerator {

    public static final int MAX_BASE_LENGTH = 100;
    private static final String FALLBACK = "comunidad";

    private SlugGenerator() {
    }

    public static String slugify(String name) {
        if (name == null) {
            return FALLBACK;
        }
        String ascii = Normalizer.normalize(name, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        String slug = ascii.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        if (slug.length() > MAX_BASE_LENGTH) {
            slug = slug.substring(0, MAX_BASE_LENGTH).replaceAll("-+$", "");
        }
        return slug.isEmpty() ? FALLBACK : slug;
    }

    /** Trimmed, with single spaces: the form in which names are stored and compared (BUG-047). */
    public static String normalizeName(String name) {
        return name == null ? null : name.strip().replaceAll("\\s+", " ");
    }
}
