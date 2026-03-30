package io.github.yagogefaell.asaltamontes.message.dto;

public record ChatReadEvent(
    Long conversationId,
    Long lastMessageId // may be useful for future doble check functionality ( knows which message was the last one read)
) {}
