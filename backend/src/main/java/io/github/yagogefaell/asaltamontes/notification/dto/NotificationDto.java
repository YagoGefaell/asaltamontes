package io.github.yagogefaell.asaltamontes.notification.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import io.github.yagogefaell.asaltamontes.notification.domain.NotificationType;

/** What the API and the WebSocket send. No text: the client composes it from type + payload (D-019). */
public record NotificationDto(UUID id, NotificationType type, ActorDto actor, TargetDto target,
                              Map<String, Object> payload, Instant readAt, Instant createdAt) {
}
