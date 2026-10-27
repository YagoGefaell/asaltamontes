package io.github.yagogefaell.asaltamontes.notification.dto;

import java.util.List;
import java.util.UUID;

/** Either a list of ids (at most 50) or {@code all: true}. */
public record MarkReadRequest(List<UUID> ids, Boolean all) {
}
