package io.github.yagogefaell.asaltamontes.message.dto;

import io.github.yagogefaell.asaltamontes.common.pagination.OpaqueCursor;
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
 */
public record MessageDto(
    UUID id,
    UUID conversationId,
    String senderUsername,
    String content,
    LocalDateTime sentAt,
    MessageType type,
    Map<String, Object> systemPayload,
    String cursor
) {
    public MessageDto(UUID id, UUID conversationId, String senderUsername, String content, LocalDateTime sentAt,
                      MessageType type, Map<String, Object> systemPayload) {
        this(id, conversationId, senderUsername, content, sentAt, type, systemPayload,
                OpaqueCursor.encode(OpaqueCursor.MESSAGE, sentAt, id));
    }

    /** A message of a user. */
    public MessageDto(UUID id, UUID conversationId, String senderUsername, String content, LocalDateTime sentAt) {
        this(id, conversationId, senderUsername, content, sentAt, MessageType.USER, null);
    }
}
