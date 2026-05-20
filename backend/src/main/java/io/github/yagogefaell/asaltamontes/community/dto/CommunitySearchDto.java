package io.github.yagogefaell.asaltamontes.community.dto;

public record CommunitySearchDto(
    Long id,
    String name,
    String imageUrl,
    boolean isMember
    // Long memberCount
) {}
