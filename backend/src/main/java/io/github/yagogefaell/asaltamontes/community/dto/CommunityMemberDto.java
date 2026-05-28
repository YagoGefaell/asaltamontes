package io.github.yagogefaell.asaltamontes.community.dto;

import io.github.yagogefaell.asaltamontes.community.domain.CommunityRole;

public record CommunityMemberDto(
    String username,
    String fullName,
    String profilePictureUrl,
    CommunityRole role,
    Boolean isFollowing
) {}
