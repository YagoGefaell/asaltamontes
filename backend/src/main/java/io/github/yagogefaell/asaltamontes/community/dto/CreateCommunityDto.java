package io.github.yagogefaell.asaltamontes.community.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCommunityDto(
    @NotBlank(message = "Community name cannot be empty")
    @Size(max = 100, message = "Community name cannot be longer than 100 characters")
    String name,

    @Size(max = 500, message = "Community description cannot be longer than 500 characters")
    String description,
    
    String imageUrl
) {}
