package io.github.yagogefaell.asaltamontes.user.dto;

import java.time.LocalDateTime;

public record BlockedUserDto(
    String username,
    String fullName,
    String profilePictureUrl,
    LocalDateTime blockedAt
) {}
