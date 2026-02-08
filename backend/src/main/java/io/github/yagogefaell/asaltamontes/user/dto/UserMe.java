package io.github.yagogefaell.asaltamontes.user.dto;

import java.time.LocalDate;

public record UserMe(
    Long userId,
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

    Integer followersCount,
    Integer followingCount
) {}
