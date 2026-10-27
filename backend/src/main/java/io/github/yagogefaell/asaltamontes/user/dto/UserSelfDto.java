package io.github.yagogefaell.asaltamontes.user.dto;

import java.time.LocalDate;

public record UserSelfDto(
    String username,
    String email,
    Boolean isVerified,

    String fullName,
    String bio,
    String city,
    String profilePictureUrl,
    String runningLevel,

    LocalDate birthDate,
    String phoneNumber,
    Boolean isPublicProfile,
    String dmPolicy,

    Long followersCount,
    Long followingCount
) {}
