package io.github.yagogefaell.asaltamontes.notification.domain;

/**
 * Kinds of notification. A later task (T19, T21, T23) adds its value here, its case in
 * {@code NotificationListener} and its text in the frontend; nothing else.
 */
public enum NotificationType {
    NEW_FOLLOWER,
    EVENT_JOINED,
    EVENT_UPDATED,
    EVENT_CANCELLED,
    WAITLIST_PROMOTED,
    EVENT_REMINDER,
    ADDED_TO_GROUP,
    COMMUNITY_JOIN_REQUEST,
    COMMUNITY_JOIN_APPROVED,
    COMMUNITY_JOIN_REJECTED,
    POST_COMMENT
}
