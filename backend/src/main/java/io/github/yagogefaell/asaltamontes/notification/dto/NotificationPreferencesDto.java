package io.github.yagogefaell.asaltamontes.notification.dto;

import java.util.Map;

/** Every type with its switch. */
public record NotificationPreferencesDto(Map<String, Boolean> types) {
}
