package io.github.yagogefaell.asaltamontes.post.dto;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** {@code communitySlug} null = post on her own profile. Photos are ids returned by {@code POST /api/media} (purpose POST_IMAGE). */
public record CreatePostDto(
        String communitySlug,
        @NotBlank(message = "Escribe algo para publicar") @Size(max = 2000, message = "La publicación no puede superar los 2000 caracteres") String body,
        @Size(max = 4, message = "Máximo 4 fotos por publicación") List<UUID> mediaIds) {
}
