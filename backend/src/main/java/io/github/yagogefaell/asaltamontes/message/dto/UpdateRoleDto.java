package io.github.yagogefaell.asaltamontes.message.dto;

import io.github.yagogefaell.asaltamontes.message.domain.ParticipantRole;
import jakarta.validation.constraints.NotNull;

public record UpdateRoleDto(
    @NotNull(message = "El rol es obligatorio")
    ParticipantRole role
) {}
