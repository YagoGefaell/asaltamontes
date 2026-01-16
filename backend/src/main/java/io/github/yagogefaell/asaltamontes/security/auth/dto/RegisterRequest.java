package io.github.yagogefaell.asaltamontes.security.auth.dto;

public record RegisterRequest(
    String username,
    String email,
    String password,
    String confirmPassword
) {}
