package io.github.yagogefaell.asaltamontes.message.dto;

import java.time.LocalDateTime;

import io.github.yagogefaell.asaltamontes.message.domain.ChatType;

public record ChatSummary(
    Long conversationId,
    String displayName,
    String displayImage,
    ChatType type,
    int unreadCount,
    String lastMessage,
    String lastMessageSenderName,
    Long lastMessageSenderId,
    LocalDateTime lastMessageAt
) {}