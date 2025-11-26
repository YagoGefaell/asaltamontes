package io.github.yagogefaell.asaltamontes.security.auth.dto;

public record RegisterRequest(String email, String password, String name) {}