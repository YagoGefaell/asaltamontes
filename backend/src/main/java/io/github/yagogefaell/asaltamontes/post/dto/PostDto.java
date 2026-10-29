package io.github.yagogefaell.asaltamontes.post.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import io.github.yagogefaell.asaltamontes.media.dto.MediaUrlsDto;

/** A publication as the viewer sees it. {@code community} is null for a post on a profile. */
public record PostDto(
        UUID id,
        String body,
        Instant createdAt,
        Author author,
        Community community,
        List<MediaUrlsDto> media,
        long likeCount,
        long commentCount,
        boolean likedByMe,
        boolean canDelete) {

    public record Author(String username, String fullName, String profilePictureUrl) {
    }

    public record Community(String slug, String name) {
    }
}
