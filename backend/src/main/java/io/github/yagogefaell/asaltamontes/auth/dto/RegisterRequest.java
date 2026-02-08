package io.github.yagogefaell.asaltamontes.auth.dto;

public record RegisterRequest(
    String fullName,
    String username,
    String email,
    String password,
    String confirmPassword
) {}
