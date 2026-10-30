package io.github.yagogefaell.asaltamontes.user.service;

/** {@code ownerId} accepted the request of {@code requesterId}; the latter is notified (T23a). A rejection says nothing. */
public record FollowRequestApproved(Long ownerId, Long requesterId) {}
