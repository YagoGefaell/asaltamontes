package io.github.yagogefaell.asaltamontes.message.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record MessageDto(
    UUID id,
    UUID conversationId,
    String senderUsername,
    String content,
    LocalDateTime sentAt
) {}