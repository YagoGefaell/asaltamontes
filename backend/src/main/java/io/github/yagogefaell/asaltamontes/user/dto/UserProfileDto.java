package io.github.yagogefaell.asaltamontes.user.dto;


public record UserProfileDto(
    String username,
    Boolean isVerified,

    String fullName,
    String bio,
    String city,
    String profilePictureUrl,
    String runningLevel,

    Boolean isPublicProfile,

    Long followersCount,
    Long followingCount
) {}
