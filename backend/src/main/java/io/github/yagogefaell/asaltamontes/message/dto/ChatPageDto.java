package io.github.yagogefaell.asaltamontes.message.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * One page of the chat list, most recent first (T10b). For the next page, ask again with
 * beforeAt = nextBeforeAt (absent when the cursor is among the chats without messages) and beforeId = nextBeforeId.
 * unreadChats counts every chat with unread messages, not only those of this page.
 */
public record ChatPageDto(
    List<ChatSummaryDto> items,
    boolean hasMore,
    LocalDateTime nextBeforeAt,
    Long nextBeforeId,
    long unreadChats
) {}
