package io.github.yagogefaell.asaltamontes.user.dto;

import java.time.Instant;

/** Someone who asked to follow the caller's private profile (T23a). */
public record FollowRequestDto(String username, String fullName, String profilePictureUrl, Instant createdAt) {}
