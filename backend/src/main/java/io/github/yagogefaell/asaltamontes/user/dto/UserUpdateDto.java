package io.github.yagogefaell.asaltamontes.user.dto;

import static io.github.yagogefaell.asaltamontes.user.validation.UserFieldRules.*;

import java.util.UUID;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// Every field is optional (PATCH): constraints only apply when the field is present
public record UserUpdateDto(
    @Size(min = USERNAME_MIN, max = USERNAME_MAX, message = USERNAME_SIZE_MESSAGE)
    @Pattern(regexp = USERNAME_PATTERN, message = USERNAME_PATTERN_MESSAGE)
    String username,

    @Size(max = EMAIL_MAX, message = "Email demasiado largo")
    @Pattern(regexp = EMAIL_PATTERN, message = EMAIL_PATTERN_MESSAGE)
    String email,

    @Size(min = FULL_NAME_MIN, max = FULL_NAME_MAX, message = FULL_NAME_SIZE_MESSAGE)
    @Pattern(regexp = FULL_NAME_PATTERN, message = FULL_NAME_PATTERN_MESSAGE)
    String fullName,

    String bio,
    String city,
    // Only a URL of one of our own uploads is accepted (BUG-057), or an empty string to remove the picture;
    // avatarMediaId is the preferred way
    @Size(max = 255, message = "La URL de la imagen no puede superar los 255 caracteres")
    String profilePictureUrl,
    UUID avatarMediaId,
    Boolean isPublicProfile
) {}
