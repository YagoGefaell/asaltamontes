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
    Boolean isFollowing,
    // True when the requester asked to follow this private profile and waits for approval (T23a)
    Boolean isFollowRequested,
    // False for a private profile she does not follow: no lists, events, communities or posts (D-025)
    Boolean canViewContent,
    // True when the requester has blocked this user (so the client offers "Desbloquear")
    Boolean isBlockedByMe,

    Long followersCount,
    Long followingCount
) {}
