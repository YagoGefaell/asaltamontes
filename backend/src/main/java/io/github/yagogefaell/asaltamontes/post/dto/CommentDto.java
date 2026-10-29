package io.github.yagogefaell.asaltamontes.post.dto;

import java.time.Instant;
import java.util.UUID;

/** A comment as the viewer sees it; {@code canDelete}: hers, she moderates the community of the post, or she wrote the profile post (D-027). */
public record CommentDto(UUID id, String body, Instant createdAt, PostDto.Author author, boolean canDelete) {
}
