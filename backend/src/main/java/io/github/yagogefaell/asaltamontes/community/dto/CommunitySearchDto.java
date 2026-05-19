package io.github.yagogefaell.asaltamontes.community.dto;

public record CommunitySearchDto(
    String name,
    String imageUrl,
    boolean isMember
    // Long memberCount
) {}
