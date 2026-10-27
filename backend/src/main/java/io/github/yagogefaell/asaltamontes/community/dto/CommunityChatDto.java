package io.github.yagogefaell.asaltamontes.community.dto;

import java.util.UUID;

/**
 * The chat of a community for one of its members (T20): {@code conversationId} is the public id of the group chat;
 * {@code enabled} says the moderation has not switched it off, and {@code canWrite} that she may write (muted
 * members read only).
 */
public record CommunityChatDto(UUID conversationId, boolean enabled, boolean canWrite) {
}
