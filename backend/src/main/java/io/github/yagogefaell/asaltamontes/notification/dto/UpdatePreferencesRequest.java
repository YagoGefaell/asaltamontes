package io.github.yagogefaell.asaltamontes.notification.dto;

import java.util.Map;

/** Only the types present change (D-020); the values are required booleans. */
public record UpdatePreferencesRequest(Map<String, Boolean> types) {
}
