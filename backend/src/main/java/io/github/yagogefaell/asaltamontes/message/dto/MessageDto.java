package io.github.yagogefaell.asaltamontes.message.dto;

import io.github.yagogefaell.asaltamontes.common.pagination.OpaqueCursor;
import io.github.yagogefaell.asaltamontes.community.domain.CommunityRole;
import io.github.yagogefaell.asaltamontes.message.domain.MessageType;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * A chat message. {@code cursor} is the opaque token of this message to use as {@code before} / {@code after}
 * in the history (T30c); the constructors without it are the ones the JPQL projections use.
 * <p>
 * A {@code SYSTEM} message (T12) has no sender ({@code senderUsername} is null) and carries {@code systemPayload},
 * e.g. {@code {"action": "MEMBER_ADDED", "actor": "ana", "targets": ["marta"]}}; clients compose the text from it
 * and only fall back to {@code content}. {@code systemPayload} is null in the messages of users.
 * <p>
 * {@code authorRole} (T20e) is OWNER or MODERATOR when the author is, today, the owner or a moderator of the community
 * that manages the chat; null otherwise (plain members, other chats, system messages). It is public on purpose.
 * <p>
 * {@code senderAvatarUrl} and {@code senderFullName} (T29q) are the author's current picture and display name, so the
 * chat can draw avatars without one request per author; null in SYSTEM messages (and when she has no picture).
 */
public record MessageDto(
    UUID id,
    UUID conversationId,
    String senderUsername,
    String content,
    LocalDateTime sentAt,
    MessageType type,
    Map<String, Object> systemPayload,
    String cursor,
    CommunityRole authorRole,
    String senderAvatarUrl,
    String senderFullName
) {
    /** SYSTEM messages (no sender). */
    public MessageDto(UUID id, UUID conversationId, String senderUsername, String content, LocalDateTime sentAt,
                      MessageType type, Map<String, Object> systemPayload) {
        this(id, conversationId, senderUsername, content, sentAt, type, systemPayload, null, null);
    }

    /** What the JPQL projections use. */
    public MessageDto(UUID id, UUID conversationId, String senderUsername, String content, LocalDateTime sentAt,
                      MessageType type, Map<String, Object> systemPayload, String senderAvatarUrl, String senderFullName) {
        this(id, conversationId, senderUsername, content, sentAt, type, systemPayload,
                OpaqueCursor.encode(OpaqueCursor.MESSAGE, sentAt, id), null, senderAvatarUrl, senderFullName);
    }

    public MessageDto withAuthorRole(CommunityRole role) {
        return new MessageDto(id, conversationId, senderUsername, content, sentAt, type, systemPayload, cursor, role,
                senderAvatarUrl, senderFullName);
    }

    /** A message of a user. */
    public MessageDto(UUID id, UUID conversationId, String senderUsername, String content, LocalDateTime sentAt,
                      String senderAvatarUrl, String senderFullName) {
        this(id, conversationId, senderUsername, content, sentAt, MessageType.USER, null, senderAvatarUrl, senderFullName);
    }
}
