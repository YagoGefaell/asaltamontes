package io.github.yagogefaell.asaltamontes.post.dto;

/** Result of liking or unliking: the state after the call, so the client does not have to guess. */
public record LikeStateDto(boolean likedByMe, long likeCount) {
}
