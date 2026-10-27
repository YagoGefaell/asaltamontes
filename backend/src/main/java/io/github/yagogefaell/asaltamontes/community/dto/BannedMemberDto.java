package io.github.yagogefaell.asaltamontes.community.dto;

import java.time.Instant;
import java.util.UUID;

/** A member of the "Expulsadas" section: who, since when, why and who expelled her (from the moderation log). */
public record BannedMemberDto(
    UUID id,
    String username,
    String fullName,
    String profilePictureUrl,
    Instant bannedAt,
    String reason,
    String bannedBy
) {}
