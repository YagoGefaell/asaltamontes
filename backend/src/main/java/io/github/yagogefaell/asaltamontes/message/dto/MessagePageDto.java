package io.github.yagogefaell.asaltamontes.message.dto;

import java.util.List;

/**
 * One page of a chat history, oldest first. To get the previous page, ask again with before = nextBefore.
 * With ?after= (T11), hasMore means there are newer messages and nextBefore is null.
 */
public record MessagePageDto(
    List<MessageDto> items,
    boolean hasMore,
    Long nextBefore
) {}
