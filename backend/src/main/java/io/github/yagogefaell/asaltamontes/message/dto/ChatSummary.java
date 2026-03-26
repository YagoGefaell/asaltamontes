package io.github.yagogefaell.asaltamontes.message.dto;

import java.time.LocalDateTime;

public record ChatSummary(
    Long conversationId,
    String contactUsername,
    int unreadCount,
    String lastMessage,
    LocalDateTime lastMessageAt
) {}