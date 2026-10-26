package io.github.yagogefaell.asaltamontes.message.dto;

import io.github.yagogefaell.asaltamontes.common.pagination.OpaqueCursor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A chat message. {@code cursor} is the opaque token of this message to use as {@code before} / {@code after}
 * in the history (T30c); the constructor without it is the one the JPQL projections use.
 */
public record MessageDto(
    UUID id,
    UUID conversationId,
    String senderUsername,
    String content,
    LocalDateTime sentAt,
    String cursor
) {
    public MessageDto(UUID id, UUID conversationId, String senderUsername, String content, LocalDateTime sentAt) {
        this(id, conversationId, senderUsername, content, sentAt, OpaqueCursor.encode(OpaqueCursor.MESSAGE, sentAt, id));
    }
}
