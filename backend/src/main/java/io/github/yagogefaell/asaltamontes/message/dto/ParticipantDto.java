package io.github.yagogefaell.asaltamontes.message.dto;

import java.time.LocalDateTime;

import io.github.yagogefaell.asaltamontes.message.domain.ParticipantRole;

/** A member of a group. Identified by username (D-021); the participation row has no public id. */
public record ParticipantDto(
    String username,
    String fullName,
    String profilePictureUrl,
    ParticipantRole role,
    LocalDateTime joinedAt
) {}
