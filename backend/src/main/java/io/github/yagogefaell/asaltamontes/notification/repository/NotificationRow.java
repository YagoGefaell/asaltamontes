package io.github.yagogefaell.asaltamontes.notification.repository;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import io.github.yagogefaell.asaltamontes.notification.domain.NotificationTargetType;
import io.github.yagogefaell.asaltamontes.notification.domain.NotificationType;

/** A notification joined with its actor, as the queries return it. {@code recipientId} is internal. */
public record NotificationRow(UUID publicId, Long recipientId, NotificationType type, String actorUsername,
                              String actorFullName, String actorPicture, boolean actorFollowedByRecipient,
                              NotificationTargetType targetType,
                              String targetRef, Map<String, Object> payload, Instant readAt, Instant createdAt) {
}
