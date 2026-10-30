package io.github.yagogefaell.asaltamontes.auth.dto;

import static io.github.yagogefaell.asaltamontes.user.validation.UserFieldRules.*;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ChangeEmailRequest(
    @NotBlank(message = "Email requerido")
    @Size(max = EMAIL_MAX, message = "Email demasiado largo")
    @Pattern(regexp = EMAIL_PATTERN, message = EMAIL_PATTERN_MESSAGE)
    String newEmail,

    @NotBlank(message = "Contraseña requerida")
    @Size(max = PASSWORD_MAX, message = PASSWORD_SIZE_MESSAGE)
    String currentPassword
) {}
