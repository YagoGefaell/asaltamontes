package io.github.yagogefaell.asaltamontes.user.service;

/** {@code followerId} started following {@code followedId}; the latter is notified (T18). */
public record UserFollowed(Long followerId, Long followedId) {}
