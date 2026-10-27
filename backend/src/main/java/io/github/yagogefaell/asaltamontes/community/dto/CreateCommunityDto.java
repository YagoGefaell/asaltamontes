package io.github.yagogefaell.asaltamontes.community.dto;

import java.util.UUID;

import io.github.yagogefaell.asaltamontes.community.domain.CommunityVisibility;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCommunityDto(
    @NotBlank(message = "El nombre de la comunidad es obligatorio")
    @Size(max = 100, message = "El nombre no puede superar los 100 caracteres")
    String name,

    @Size(max = 500, message = "La descripción no puede superar los 500 caracteres")
    String description,

    // Only a URL of one of our own uploads is accepted (BUG-057); coverMediaId is the preferred way
    @Size(max = 255, message = "La URL de la imagen no puede superar los 255 caracteres")
    String imageUrl,

    UUID coverMediaId,

    // Optional: PUBLIC when absent (T19)
    CommunityVisibility visibility,

    @Size(max = 80, message = "La ciudad no puede superar los 80 caracteres")
    String city,

    @Size(max = 80, message = "La provincia no puede superar los 80 caracteres")
    String province
) {
    public CreateCommunityDto(String name, String description, String imageUrl, UUID coverMediaId) {
        this(name, description, imageUrl, coverMediaId, null, null, null);
    }
}
