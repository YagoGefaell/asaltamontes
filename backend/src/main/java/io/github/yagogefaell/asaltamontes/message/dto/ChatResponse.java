package io.github.yagogefaell.asaltamontes.message.dto;

public record ChatResponse(
    Long conversationId,
    String contactUsername
) {}