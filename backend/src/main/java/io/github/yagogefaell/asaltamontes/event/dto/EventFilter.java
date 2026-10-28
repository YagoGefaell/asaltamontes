package io.github.yagogefaell.asaltamontes.event.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import io.github.yagogefaell.asaltamontes.common.exceptions.BadRequestException;
import io.github.yagogefaell.asaltamontes.event.domain.EventType;
import io.github.yagogefaell.asaltamontes.user.domain.RunningLevel;

/** Filters of an event listing (T15c). A null field does not filter. */
public record EventFilter(
    Instant from,
    Instant to,
    EventType type,
    RunningLevel level,
    UUID communityId,
    String city,
    BigDecimal minDistance,
    BigDecimal maxDistance,
    Double minLat,
    Double maxLat,
    Double minLng,
    Double maxLng,
    boolean organizerMe,
    boolean attendingMe,
    String q,
    boolean followedByMe,
    // Internal (never a request parameter): events organized by this user, for the profile of another user (T31b)
    Long organizerKey
) {

    /** From the raw query parameters: organizer, attending and followedBy only accept "me". */
    public static EventFilter of(Instant from, Instant to, EventType type, RunningLevel level, UUID communityId,
            String city, BigDecimal minDistance, BigDecimal maxDistance, Double minLat, Double maxLat,
            Double minLng, Double maxLng, String organizer, String attending, String q, String followedBy) {
        return new EventFilter(from, to, type, level, communityId, city, minDistance, maxDistance,
                minLat, maxLat, minLng, maxLng, isMe("organizer", organizer), isMe("attending", attending),
                q == null || q.isBlank() ? null : q.strip(), isMe("followedBy", followedBy), null);
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
