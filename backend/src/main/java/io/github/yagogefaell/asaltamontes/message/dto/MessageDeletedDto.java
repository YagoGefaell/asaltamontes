package io.github.yagogefaell.asaltamontes.message.dto;

import java.util.UUID;

/** Sent to /user/queue/message-deletions when a moderator deletes a message: clients drop it from the chat. */
public record MessageDeletedDto(
    UUID conversationId,
    UUID messageId
) {}
