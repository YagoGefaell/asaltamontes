package io.github.yagogefaell.asaltamontes.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record VerifyEmailRequest(@NotBlank(message = "Falta el enlace de verificación") @Size(max = 128) String token) {}
