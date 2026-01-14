package io.github.yagogefaell.asaltamontes.profiles.dto;

import java.sql.Date;

public record CreateProfileRequest(long userId, String firstName, String lastName, String bio, String location, String profilePictureUrl, Date birthDate, String instagramProfileUrl, String stravaProfileUrl, boolean isPublicProfile) {}