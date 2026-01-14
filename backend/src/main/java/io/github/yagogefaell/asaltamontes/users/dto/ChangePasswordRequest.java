package io.github.yagogefaell.asaltamontes.users.dto;

public record ChangePasswordRequest(String oldPassword, String newPassword) {}
