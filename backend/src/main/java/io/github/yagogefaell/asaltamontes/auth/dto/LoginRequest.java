package io.github.yagogefaell.asaltamontes.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// No minimum password length here: accounts created before T06b may have 6-7 character passwords
public record LoginRequest(
    @NotBlank(message = "Username requerido") @Size(max = 100) String username,
    @NotBlank(message = "Contraseña requerida") @Size(max = 100) String password
) {}
