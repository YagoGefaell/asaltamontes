package io.github.yagogefaell.asaltamontes.community.dto;

import io.github.yagogefaell.asaltamontes.community.domain.CommunityRole;

public record CommunitySearchDto(
    Long id,
    String name,
    String slug,
    String imageUrl,
    CommunityRole role,
    Long memberCount
) {}
