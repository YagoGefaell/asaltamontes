package io.github.yagogefaell.asaltamontes.notification.dto;

import io.github.yagogefaell.asaltamontes.notification.domain.NotificationTargetType;

/** {@code ref} is the public reference: event/chat UUID, community slug or username. */
public record TargetDto(NotificationTargetType type, String ref) {
}
