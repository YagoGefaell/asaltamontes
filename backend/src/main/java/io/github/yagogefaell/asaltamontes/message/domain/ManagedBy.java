package io.github.yagogefaell.asaltamontes.message.domain;

/** Who manages the members of a group: nobody (a manual group), or the community/event it belongs to (T20/T21). */
public enum ManagedBy {
    NONE,
    COMMUNITY,
    EVENT
}
