package io.github.yagogefaell.asaltamontes.user.dto;

public record ChangeProfile(
    String username,
    String email,
    String fullName,
    String bio,
    String city,
    String profilePictureUrl,
    Boolean isPublicProfile
) {}
