package io.github.yagogefaell.asaltamontes.event.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import io.github.yagogefaell.asaltamontes.event.domain.EventType;
import io.github.yagogefaell.asaltamontes.user.domain.RunningLevel;

/** An event in a listing (T15a). */
public record EventSummaryDto(
    UUID id,
    String title,
    EventType type,
    Instant startsAt,
    String timezone,
    String meetingPointName,
    String city,
    BigDecimal distanceKm,
    Integer elevationGainM,
    RunningLevel level,
    Integer capacity,
    int attendeeCount,
    String communitySlug,
    String communityName,
    String organizerUsername,
    String imageUrl,
    // Meeting point; null when the event has none (BUG-062)
    Double latitude,
    Double longitude,
    // Up to 3 people going, oldest first, without blocked ones in either direction (T31)
    List<EventAttendeePreviewDto> attendeePreview
) {

    /** Without preview: the projection of the listing query, which adds it afterwards in one extra query. */
    public EventSummaryDto(UUID id, String title, EventType type, Instant startsAt, String timezone,
            String meetingPointName, String city, BigDecimal distanceKm, Integer elevationGainM, RunningLevel level,
            Integer capacity, int attendeeCount, String communitySlug, String communityName,
            String organizerUsername, String imageUrl, Double latitude, Double longitude) {
        this(id, title, type, startsAt, timezone, meetingPointName, city, distanceKm, elevationGainM, level, capacity,
                attendeeCount, communitySlug, communityName, organizerUsername, imageUrl, latitude, longitude, List.of());
    }

    public EventSummaryDto withAttendeePreview(List<EventAttendeePreviewDto> preview) {
        return new EventSummaryDto(id, title, type, startsAt, timezone, meetingPointName, city, distanceKm,
                elevationGainM, level, capacity, attendeeCount, communitySlug, communityName, organizerUsername,
                imageUrl, latitude, longitude, preview);
    }
}
