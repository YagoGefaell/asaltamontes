package io.github.yagogefaell.asaltamontes.community.dto;

public record CommunityDetailsDto(
    Long id,
    String name,
    String description,
    String imageUrl,
    boolean isOwner,
    boolean isMember
    // Long memberCount
    // CommunityRole role
) {}
