package io.github.yagogefaell.asaltamontes.community.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCommunityDto(
    @NotBlank(message = "El nombre de la comunidad es obligatorio")
    @Size(max = 100, message = "El nombre no puede superar los 100 caracteres")
    String name,

    @Size(max = 500, message = "La descripción no puede superar los 500 caracteres")
    String description,
    
    String imageUrl
) {}
