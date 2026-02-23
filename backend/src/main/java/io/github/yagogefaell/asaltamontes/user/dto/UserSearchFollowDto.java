package io.github.yagogefaell.asaltamontes.user.dto;

public record UserSearchFollowDto (
    String username,
    String fullName,
    String profilePictureUrl,
    boolean isFollowing
) {}
