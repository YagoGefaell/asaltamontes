package io.github.yagogefaell.asaltamontes.community.dto;

public record CreateCommunityDto(
    String name,
    String description,
    String imageUrl
) {}
