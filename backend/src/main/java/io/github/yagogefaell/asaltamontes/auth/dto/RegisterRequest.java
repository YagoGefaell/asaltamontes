package io.github.yagogefaell.asaltamontes.auth.dto;

import static io.github.yagogefaell.asaltamontes.user.validation.UserFieldRules.*;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
    @NotBlank(message = "Nombre completo requerido")
    @Size(min = FULL_NAME_MIN, max = FULL_NAME_MAX, message = FULL_NAME_SIZE_MESSAGE)
    @Pattern(regexp = FULL_NAME_PATTERN, message = FULL_NAME_PATTERN_MESSAGE)
    String fullName,

    @NotBlank(message = "Username requerido")
    @Size(min = USERNAME_MIN, max = USERNAME_MAX, message = USERNAME_SIZE_MESSAGE)
    @Pattern(regexp = USERNAME_PATTERN, message = USERNAME_PATTERN_MESSAGE)
    String username,

    @NotBlank(message = "Email requerido")
    @Size(max = EMAIL_MAX, message = "Email demasiado largo")
    @Pattern(regexp = EMAIL_PATTERN, message = EMAIL_PATTERN_MESSAGE)
    String email,

    @NotBlank(message = "Contraseña requerida")
    @Size(min = PASSWORD_MIN, max = PASSWORD_MAX, message = PASSWORD_SIZE_MESSAGE)
    String password,

    String confirmPassword
) {}
