package io.github.yagogefaell.asaltamontes.notification.domain;

import java.io.Serializable;

public record NotificationPreferenceId(Long userId, NotificationType type) implements Serializable {
}
