package io.github.yagogefaell.asaltamontes.post.service;

import java.util.UUID;

/** Someone commented on a post; its author is notified (T22a2) after the commit. Internal ids: never leaves the backend. */
public record PostCommented(UUID postPublicId, Long postAuthorId, Long commenterId, String excerpt) {
}
