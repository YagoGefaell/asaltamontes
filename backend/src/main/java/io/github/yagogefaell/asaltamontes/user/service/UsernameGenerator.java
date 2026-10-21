package io.github.yagogefaell.asaltamontes.user.service;

import java.text.Normalizer;
import java.util.Locale;

import org.springframework.stereotype.Component;

import io.github.yagogefaell.asaltamontes.user.repository.UserAccountRepository;
import io.github.yagogefaell.asaltamontes.user.validation.UserFieldRules;
import lombok.RequiredArgsConstructor;

/**
 * Builds a free, valid username from a display name (Google sign-up): "María José" -> "mariajose",
 * then "mariajose1", "mariajose2"... if it is taken.
 */
@Component
@RequiredArgsConstructor
public class UsernameGenerator {

    static final String FALLBACK = "runner";

    private final UserAccountRepository userAccountRepository;

    public String generate(String name) {
        String base = normalize(name);
        String candidate = base;
        for (int suffix = 1; userAccountRepository.existsByUsernameIgnoreCase(candidate); suffix++) {
            String number = String.valueOf(suffix);
            candidate = truncate(base, UserFieldRules.USERNAME_MAX - number.length()) + number;
        }
        return candidate;
    }

    static String normalize(String name) {
        if (name == null) {
            return FALLBACK;
        }

        String ascii = Normalizer.normalize(name, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        String cleaned = ascii.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9._]", "")
                .replaceAll("^[.]+|[.]+$", "");
        cleaned = truncate(cleaned, UserFieldRules.USERNAME_MAX).replaceAll("[.]+$", "");

        return cleaned.length() < UserFieldRules.USERNAME_MIN ? FALLBACK : cleaned;
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
