package io.github.yagogefaell.asaltamontes.community.service;

/** The staff accepted or rejected a request; the requester is notified (T18/T19). Internal ids. */
public record JoinRequestDecided(String communitySlug, String communityName, Long actorId, Long requesterId, boolean approved) {
}
