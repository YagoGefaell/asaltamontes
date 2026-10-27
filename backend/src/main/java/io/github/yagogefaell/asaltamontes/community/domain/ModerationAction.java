package io.github.yagogefaell.asaltamontes.community.domain;

/** What the {@code community_moderation_log} records. */
public enum ModerationAction {
    PROMOTE,
    DEMOTE,
    MUTE,
    UNMUTE,
    BAN,
    UNBAN,
    TRANSFER_OWNERSHIP,
    REQUEST_APPROVED,
    REQUEST_REJECTED,
    COMMUNITY_UPDATED
}
