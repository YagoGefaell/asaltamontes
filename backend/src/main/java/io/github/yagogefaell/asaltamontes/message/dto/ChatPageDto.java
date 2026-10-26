package io.github.yagogefaell.asaltamontes.message.dto;

import java.util.List;

/**
 * One page of the chat list, most recent first (T10b). For the next page, ask again with
 * before = nextBefore (opaque token, T30c).
 * unreadChats counts every chat with unread messages, not only those of this page.
 */
public record ChatPageDto(
    List<ChatSummaryDto> items,
    boolean hasMore,
    String nextBefore,
    long unreadChats
) {}
