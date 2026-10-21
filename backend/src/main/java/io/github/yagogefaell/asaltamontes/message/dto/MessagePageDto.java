package io.github.yagogefaell.asaltamontes.message.dto;

import java.util.List;

/**
 * One page of a chat history, oldest first. To get the previous page, ask again with before = nextBefore.
 */
public record MessagePageDto(
    List<MessageDto> items,
    boolean hasMore,
    Long nextBefore
) {}
