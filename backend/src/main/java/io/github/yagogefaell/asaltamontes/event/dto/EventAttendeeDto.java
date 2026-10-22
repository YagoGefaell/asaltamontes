package io.github.yagogefaell.asaltamontes.event.dto;

import java.time.Instant;

import io.github.yagogefaell.asaltamontes.event.domain.AttendeeStatus;

/** One person in the attendee list of an event (T15b). waitlistPosition only for WAITLIST (1 = next one in). */
public record EventAttendeeDto(
    String username,
    String fullName,
    String profilePictureUrl,
    AttendeeStatus status,
    Integer waitlistPosition,
    Instant joinedAt
) {}
