package io.github.yagogefaell.asaltamontes.profiles.dto;

public record PrivateProfileResponse(
    Long userId,
    String firstName,
    String lastName,
    String bio,
    String location,
    String profilePictureUrl,
    String instagramProfileUrl,
    String stravaProfileUrl
) {}
