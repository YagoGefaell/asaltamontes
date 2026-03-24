package io.github.yagogefaell.asaltamontes.message.dto;

import java.time.LocalDateTime;

public record MessageDto(
    Long id,
    Long conversationId,
    String senderUsername,
    String content,
    LocalDateTime sentAt
) {}