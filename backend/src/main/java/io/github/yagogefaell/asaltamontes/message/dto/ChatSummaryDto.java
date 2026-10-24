package io.github.yagogefaell.asaltamontes.message.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import io.github.yagogefaell.asaltamontes.message.domain.ChatType;

public record ChatSummaryDto(
    UUID conversationId,
    String displayName,
    String displayImage,
    ChatType type,
    int unreadCount,
    String lastMessage,
    String lastMessageSenderName,
    LocalDateTime lastMessageAt
) {}