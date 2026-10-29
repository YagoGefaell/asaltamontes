package io.github.yagogefaell.asaltamontes.post.dto;

/** Plain text, 1-1000 characters after trimming (validated in the service so the message is in Spanish). */
public record CreateCommentDto(String body) {
}
