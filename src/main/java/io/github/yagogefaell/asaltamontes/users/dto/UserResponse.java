package io.github.yagogefaell.asaltamontes.users.dto;

public record UserResponse(
    Long id,
    String username,
    String email,
    String role
) {}

