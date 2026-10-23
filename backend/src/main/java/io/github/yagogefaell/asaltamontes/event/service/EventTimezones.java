package io.github.yagogefaell.asaltamontes.event.service;

import java.time.DateTimeException;
import java.time.ZoneId;

import io.github.yagogefaell.asaltamontes.common.exceptions.BadRequestException;

/**
 * Time zone validation for events (D-019): the time belongs to the place, so only region-based IANA identifiers are
 * accepted ({@code Europe/Madrid}, {@code America/Argentina/Buenos_Aires}) plus {@code UTC}. Rejected:
 * fixed offsets ({@code +02:00}, {@code UTC+2}, {@code GMT+2}, {@code Etc/GMT-2}) because they ignore daylight saving
 * time, and the legacy abbreviations that tzdata keeps as zones ({@code CET}, {@code EST}, {@code MST}, {@code HST},
 * {@code GMT}...) because they are ambiguous and not tied to a place.
 */
final class EventTimezones {

    static final String UTC = "UTC";
    static final String INVALID = "Zona horaria no válida: usa un identificador como Europe/Madrid";

    private EventTimezones() {}

    /** Returns the canonical id, or throws 400. */
    static String require(String timezone) {
        if (timezone == null || timezone.isBlank()) {
            throw new BadRequestException("La zona horaria es obligatoria");
        }
        String id = timezone.strip();
        if (!isRegionalIana(id)) {
            throw new BadRequestException(INVALID);
        }
        try {
            return ZoneId.of(id).getId();
        } catch (DateTimeException e) {
            throw new BadRequestException(INVALID);
        }
    }

    private static boolean isRegionalIana(String id) {
        if (id.equals(UTC)) {
            return true;
        }
        return id.indexOf('/') > 0
                && !id.startsWith("Etc/")
                && !id.startsWith("SystemV/")
                && ZoneId.getAvailableZoneIds().contains(id);
    }
}
