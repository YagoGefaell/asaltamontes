package io.github.yagogefaell.asaltamontes.community.dto;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

import io.github.yagogefaell.asaltamontes.community.domain.CommunityRole;
import io.github.yagogefaell.asaltamontes.community.domain.CommunityVisibility;

/**
 * One of the caller's communities with what the "Comunidades" tab shows (T31c): the same fields as
 * {@link CommunitySearchDto}, plus the state of its chat and its next meetup.
 */
public record MyCommunityDto(
    UUID id,
    String name,
    String slug,
    String imageUrl,
    CommunityRole role,
    Long memberCount,
    CommunityVisibility visibility,
    String city,
    String province,
    Chat chat,
    NextEvent nextEvent
) {

    /**
     * {@code lastMessage} and {@code lastSender} are null when the chat has no messages yet
     * ({@code lastMessageAt} null too) or when the last one is from a user with a block in either direction with the
     * caller (D-022): then only the time is shown. {@code conversationId} is null if the chat does not exist yet.
     */
    public record Chat(UUID conversationId, String lastMessage, String lastSender, LocalDateTime lastMessageAt,
                       int unreadCount) {
    }

    public record NextEvent(UUID id, String title, Instant startsAt, String timezone) {
    }
}
