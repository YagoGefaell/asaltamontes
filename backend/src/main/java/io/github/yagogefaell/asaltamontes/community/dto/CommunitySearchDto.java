package io.github.yagogefaell.asaltamontes.community.dto;

import java.util.UUID;

import io.github.yagogefaell.asaltamontes.community.domain.CommunityRole;

public record CommunitySearchDto(
    UUID id,
    String name,
    String slug,
    String imageUrl,
    CommunityRole role,
    Long memberCount
) {}
