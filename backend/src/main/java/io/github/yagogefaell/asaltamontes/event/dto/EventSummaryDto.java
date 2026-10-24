package io.github.yagogefaell.asaltamontes.event.dto;

import java.math.BigDecimal;
import java.time.Instant;
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
    Double longitude
) {}
