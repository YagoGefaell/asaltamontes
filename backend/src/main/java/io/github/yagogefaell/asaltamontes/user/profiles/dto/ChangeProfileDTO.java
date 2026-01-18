package io.github.yagogefaell.asaltamontes.user.profiles.dto;

import io.github.yagogefaell.asaltamontes.user.profiles.RunningLevel;

public record ChangeProfileDTO(
    String username,
    String email,
    String currentPassword,
    String newPassword,
    String fullName,
    String bio,
    String city,
    String profilePictureUrl,
    RunningLevel runningLevel,
    Boolean isPublicProfile
) {}
