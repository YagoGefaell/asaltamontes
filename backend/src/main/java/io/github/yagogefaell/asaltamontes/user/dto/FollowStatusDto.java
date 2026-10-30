package io.github.yagogefaell.asaltamontes.user.dto;

/** Answer of following someone: FOLLOWING (done) or REQUESTED (private profile, waiting for approval; T23a). */
public record FollowStatusDto(String status) {}
