package io.github.yagogefaell.asaltamontes.message.dto;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

public record AddParticipantsDto(
    @NotEmpty(message = "Debes indicar al menos una usuaria")
    @Size(max = 50, message = "Como máximo 50 usuarias por petición")
    List<String> usernames
) {}
