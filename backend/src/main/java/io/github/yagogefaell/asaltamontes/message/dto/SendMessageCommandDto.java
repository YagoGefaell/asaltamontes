package io.github.yagogefaell.asaltamontes.message.dto;

import java.util.UUID;

public record SendMessageCommandDto(
    UUID conversationId,
    String content
) {}
