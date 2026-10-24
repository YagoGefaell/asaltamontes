package io.github.yagogefaell.asaltamontes.message.dto;

import java.util.UUID;

public record ChatReadEventDto(
    UUID conversationId,
    UUID lastMessageId // may be useful for future doble check functionality ( knows which message was the last one read)
) {}
