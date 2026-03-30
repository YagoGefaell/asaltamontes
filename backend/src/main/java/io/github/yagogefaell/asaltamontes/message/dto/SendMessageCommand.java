package io.github.yagogefaell.asaltamontes.message.dto;

public record SendMessageCommand(
    Long conversationId,
    String content
) {}
