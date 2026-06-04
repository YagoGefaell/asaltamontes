package io.github.yagogefaell.asaltamontes.message.dto;

public record SendMessageCommandDto(
    Long conversationId,
    String content
) {}
