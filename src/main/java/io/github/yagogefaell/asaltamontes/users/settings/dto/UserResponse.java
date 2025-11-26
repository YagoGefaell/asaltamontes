package io.github.yagogefaell.asaltamontes.users.settings.dto;

public record UserResponse(
    Long id,
    String username,
    String email,
    String role
) {}

