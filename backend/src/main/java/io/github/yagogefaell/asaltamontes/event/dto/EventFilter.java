package io.github.yagogefaell.asaltamontes.event.dto;

import java.math.BigDecimal;
import java.time.Instant;

import io.github.yagogefaell.asaltamontes.common.exceptions.BadRequestException;
import io.github.yagogefaell.asaltamontes.event.domain.EventType;
import io.github.yagogefaell.asaltamontes.user.domain.RunningLevel;

/** Filters of an event listing (T15c). A null field does not filter. */
public record EventFilter(
    Instant from,
    Instant to,
    EventType type,
    RunningLevel level,
    Long communityId,
    String city,
    BigDecimal minDistance,
    BigDecimal maxDistance,
    Double minLat,
    Double maxLat,
    Double minLng,
    Double maxLng,
    boolean organizerMe,
    boolean attendingMe
) {

    /** From the raw query parameters: organizer and attending only accept "me". */
    public static EventFilter of(Instant from, Instant to, EventType type, RunningLevel level, Long communityId,
            String city, BigDecimal minDistance, BigDecimal maxDistance, Double minLat, Double maxLat,
            Double minLng, Double maxLng, String organizer, String attending) {
        return new EventFilter(from, to, type, level, communityId, city, minDistance, maxDistance,
                minLat, maxLat, minLng, maxLng, isMe("organizer", organizer), isMe("attending", attending));
    }

    private static boolean isMe(String name, String value) {
        if (value == null) {
            return false;
        }
        if (!"me".equals(value)) {
            throw new BadRequestException("'" + name + "' solo admite el valor 'me'");
        }
        return true;
    }
}
