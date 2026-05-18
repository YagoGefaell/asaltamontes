package io.github.yagogefaell.asaltamontes.community.dto;

public record CommunityDto(
    Long id,
    String name,
    String description,
    String imageUrl,
    String createdByUsername
) {}
