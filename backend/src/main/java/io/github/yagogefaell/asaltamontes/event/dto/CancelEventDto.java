package io.github.yagogefaell.asaltamontes.event.dto;

import jakarta.validation.constraints.Size;

public record CancelEventDto(
    @Size(max = 500, message = "El motivo no puede superar los 500 caracteres")
    String reason
) {}
