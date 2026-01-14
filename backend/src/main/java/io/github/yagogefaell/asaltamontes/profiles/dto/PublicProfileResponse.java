package io.github.yagogefaell.asaltamontes.profiles.dto;

public record PublicProfileResponse(
    Long userId,
    String bio,
    String location,
    String profilePictureUrl
) {}
