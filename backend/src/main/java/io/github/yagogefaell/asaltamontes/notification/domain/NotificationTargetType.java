package io.github.yagogefaell.asaltamontes.notification.domain;

/** What a notification points to; {@code target_ref} holds its public reference (UUID, slug or username). */
public enum NotificationTargetType {
    EVENT,
    COMMUNITY,
    CONVERSATION,
    USER,
    POST
}
