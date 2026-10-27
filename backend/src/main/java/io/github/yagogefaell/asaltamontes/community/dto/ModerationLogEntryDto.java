package io.github.yagogefaell.asaltamontes.community.dto;

import java.time.Instant;
import java.util.UUID;

import io.github.yagogefaell.asaltamontes.community.domain.ModerationAction;

/** actor/target usernames are null if the account was deleted afterwards. */
public record ModerationLogEntryDto(
    UUID id,
    ModerationAction action,
    String actorUsername,
    String targetUsername,
    String reason,
    Instant createdAt
) {}
