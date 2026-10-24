package io.github.yagogefaell.asaltamontes.event.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import io.github.yagogefaell.asaltamontes.event.domain.AttendeeStatus;
import io.github.yagogefaell.asaltamontes.event.domain.EventStatus;
import io.github.yagogefaell.asaltamontes.event.domain.EventType;
import io.github.yagogefaell.asaltamontes.event.domain.EventVisibility;
import io.github.yagogefaell.asaltamontes.user.domain.RunningLevel;

/** One event as the requester sees it: myStatus is her own attendance (null if none), canEdit her rights. */
public record EventDetailsDto(
    UUID id,
    String title,
    String description,
    EventType type,
    EventStatus status,
    Instant startsAt,
    Instant endsAt,
    String timezone,
    String meetingPointName,
    Double latitude,
    Double longitude,
    String city,
    BigDecimal distanceKm,
    Integer elevationGainM,
    RunningLevel level,
    Integer capacity,
    int attendeeCount,
    EventVisibility visibility,
    UUID communityId,
    String communitySlug,
    String communityName,
    String organizerUsername,
    String organizerFullName,
    String organizerPictureUrl,
    String imageUrl,
    String cancelReason,
    Instant cancelledAt,
    AttendeeStatus myStatus,
    boolean canEdit
) {}
