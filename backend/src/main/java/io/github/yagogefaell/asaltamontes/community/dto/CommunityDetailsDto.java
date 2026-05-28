package io.github.yagogefaell.asaltamontes.community.dto;

import io.github.yagogefaell.asaltamontes.community.domain.CommunityRole;

public record CommunityDetailsDto(
    Long id,
    String name,
    String description,
    String imageUrl,
    CommunityRole role,
    Long memberCount
) {}
