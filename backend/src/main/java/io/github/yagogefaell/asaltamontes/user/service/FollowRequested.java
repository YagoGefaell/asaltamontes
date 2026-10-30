package io.github.yagogefaell.asaltamontes.user.service;

/** {@code requesterId} asked to follow the private profile of {@code ownerId}; the latter is notified (T23a). */
public record FollowRequested(Long requesterId, Long ownerId) {}
