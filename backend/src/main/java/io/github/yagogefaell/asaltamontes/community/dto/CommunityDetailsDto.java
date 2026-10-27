package io.github.yagogefaell.asaltamontes.community.dto;

import java.util.UUID;

import io.github.yagogefaell.asaltamontes.community.domain.CommunityRole;
import io.github.yagogefaell.asaltamontes.community.domain.CommunityVisibility;

/**
 * {@code role} is null for anyone who is not an (unbanned) member, and {@code joinRequested} says she has a pending
 * request in a private community; neither reveals a ban.
 */
public record CommunityDetailsDto(
    UUID id,
    String name,
    String slug,
    String description,
    String imageUrl,
    CommunityRole role,
    Long memberCount,
    CommunityVisibility visibility,
    String city,
    String province,
    boolean joinRequested
) {}
