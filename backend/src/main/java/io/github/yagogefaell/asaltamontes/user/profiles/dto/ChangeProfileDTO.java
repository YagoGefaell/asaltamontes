package io.github.yagogefaell.asaltamontes.user.profiles.dto;

public record ChangeProfileDTO(
    long id,
    String username,
    String email,
    String fullName,
    String bio,
    String city,
    String profilePictureUrl,
    Boolean isPublicProfile
) {}
