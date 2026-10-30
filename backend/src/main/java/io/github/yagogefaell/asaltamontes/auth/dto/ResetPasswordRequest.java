package io.github.yagogefaell.asaltamontes.auth.dto;

import static io.github.yagogefaell.asaltamontes.user.validation.UserFieldRules.*;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
    @NotBlank(message = "Falta el enlace de recuperación") @Size(max = 128) String token,

    @NotBlank(message = "Contraseña requerida")
    @Size(min = PASSWORD_MIN, max = PASSWORD_MAX, message = PASSWORD_SIZE_MESSAGE)
    String password,

    String confirmPassword
) {}
