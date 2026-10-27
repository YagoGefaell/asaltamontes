package io.github.yagogefaell.asaltamontes.common.pagination;

import io.github.yagogefaell.asaltamontes.common.exceptions.BadRequestException;

import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.UUID;

/**
 * Opaque pagination cursor (D-021, T30c): base64url of {@code kind|orderMicros|publicId}.
 * {@code kind} ties a token to the list it came from (a message cursor is not a chat cursor), {@code order} is
 * the sort value of the row (UTC microseconds, or empty when the row has none) and {@code publicId} the row's
 * public id. The token never carries an internal id. It is encoded, not signed: whoever receives one must
 * still check the row against the caller's permissions, and any malformed token is a 400.
 */
public record OpaqueCursor(String kind, LocalDateTime order, UUID publicId) {

    public static final String MESSAGE = "m";
    public static final String CHAT = "c";

    private static final String INVALID = "Cursor no válido";

    public static String encode(String kind, LocalDateTime order, UUID publicId) {
        String micros = order == null ? "" : Long.toString(toMicros(order));
        String raw = kind + "|" + micros + "|" + publicId;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /** Decodes a token of the expected kind; blank means "no cursor" (null). Anything else invalid is a 400. */
    public static OpaqueCursor decode(String token, String expectedKind) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            String raw = new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
            String[] parts = raw.split("\\|", -1);
            if (parts.length != 3 || !parts[0].equals(expectedKind)) {
                throw new BadRequestException(INVALID);
            }
            LocalDateTime order = parts[1].isEmpty() ? null : fromMicros(Long.parseLong(parts[1]));
            UUID id = UUID.fromString(parts[2]);
            // A sane range, so an absurd value never reaches the database as a timestamp (would be a 500)
            if (order != null && (order.getYear() < 2000 || order.getYear() > 2100)) {
                throw new BadRequestException(INVALID);
            }
            // Only the canonical form is accepted, so one cursor has a single spelling
            if (!encode(expectedKind, order, id).equals(token)) {
                throw new BadRequestException(INVALID);
            }
            return new OpaqueCursor(expectedKind, order, id);
        } catch (IllegalArgumentException | DateTimeException e) {
            throw new BadRequestException(INVALID);
        }
    }

    /** Rows are stored with microsecond precision, so that is the precision of the order value. */
    public static LocalDateTime truncate(LocalDateTime value) {
        return value == null ? null : value.truncatedTo(ChronoUnit.MICROS);
    }

    private static long toMicros(LocalDateTime value) {
        LocalDateTime t = truncate(value);
        return t.toEpochSecond(ZoneOffset.UTC) * 1_000_000L + t.getNano() / 1000;
    }

    private static LocalDateTime fromMicros(long micros) {
        long seconds = Math.floorDiv(micros, 1_000_000L);
        int nanos = (int) Math.floorMod(micros, 1_000_000L) * 1000;
        return LocalDateTime.ofEpochSecond(seconds, nanos, ZoneOffset.UTC);
    }
}
