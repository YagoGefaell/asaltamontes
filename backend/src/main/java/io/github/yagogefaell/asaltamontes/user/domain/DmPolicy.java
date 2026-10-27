package io.github.yagogefaell.asaltamontes.user.domain;

/** Who may start a new private chat with a user (T13, D-018). Existing chats are not affected. */
public enum DmPolicy {
    EVERYONE,
    /** Only users she follows. */
    FOLLOWING,
    /** Only users she follows and who follow her back. */
    MUTUALS,
    NOBODY
}
