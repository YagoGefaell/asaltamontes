package io.github.yagogefaell.asaltamontes.message.service;

import java.util.UUID;

/** A moderator deleted a message: ChatMessageBroadcaster tells the participants after the commit. */
public record MessageDeletedEvent(UUID conversationId, UUID messageId) {
}
