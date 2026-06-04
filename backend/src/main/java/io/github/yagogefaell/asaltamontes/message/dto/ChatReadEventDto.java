package io.github.yagogefaell.asaltamontes.message.dto;

public record ChatReadEventDto(
    Long conversationId,
    Long lastMessageId // may be useful for future doble check functionality ( knows which message was the last one read)
) {}
