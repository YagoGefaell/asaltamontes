package io.github.yagogefaell.asaltamontes.community.dto;

import io.github.yagogefaell.asaltamontes.community.domain.CommunityRole;
import jakarta.validation.constraints.NotNull;

/** New role of a member: MODERATOR or MEMBER (ownership has its own endpoint). */
public record ChangeRoleDto(
    @NotNull(message = "El rol es obligatorio") CommunityRole role
) {}
