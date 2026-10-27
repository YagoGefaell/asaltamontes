package io.github.yagogefaell.asaltamontes.community.dto;

import java.time.Instant;
import java.util.UUID;

public record JoinRequestDto(
    UUID id,
    String username,
    String fullName,
    String profilePictureUrl,
    Instant requestedAt
) {}
