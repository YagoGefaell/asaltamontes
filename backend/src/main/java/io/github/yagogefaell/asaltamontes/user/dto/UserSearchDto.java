package io.github.yagogefaell.asaltamontes.user.dto;

public record UserSearchDto (
    String username,
    String fullName,
    String profilePictureUrl,
    Boolean isFollowing
) {}
