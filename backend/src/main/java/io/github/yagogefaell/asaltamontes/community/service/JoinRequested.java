package io.github.yagogefaell.asaltamontes.community.service;

import java.util.List;

/** Someone asked to join a private community; the staff are notified (T18/T19). Internal ids: never leaves the backend. */
public record JoinRequested(String communitySlug, String communityName, Long requesterId, List<Long> staffIds) {
}
