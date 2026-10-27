package io.github.yagogefaell.asaltamontes.community.dto;

import jakarta.validation.constraints.Size;

/** Optional body of ban/mute: the reason that goes to the moderation log. */
public record ModerationReasonDto(
    @Size(max = 300, message = "El motivo no puede superar los 300 caracteres") String reason
) {}
