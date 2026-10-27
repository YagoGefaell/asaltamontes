package io.github.yagogefaell.asaltamontes.message.dto;

import java.util.UUID;

/** Sent to /user/queue/conversations when a chat has to disappear from the list: reason REMOVED (expelled) or LEFT. */
public record ConversationRemovedDto(
    UUID conversationId,
    String reason
) {}
