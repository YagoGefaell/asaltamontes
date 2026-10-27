package io.github.yagogefaell.asaltamontes.message.service;

import java.util.UUID;

/** A user stopped being in a chat (expelled or left): ChatMessageBroadcaster tells her sessions after the commit. */
public record ConversationRemovedEvent(Long userId, UUID conversationId, String reason) {
}
