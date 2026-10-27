package io.github.yagogefaell.asaltamontes.community.dto;

import java.util.List;

/** A page of a growing list (moderation log, banned, requests); {@code nextBefore} is an opaque cursor or null. */
public record CursorPageDto<T>(List<T> items, String nextBefore) {
}
