package io.github.yagogefaell.asaltamontes.notification.dto;

import java.util.List;

public record NotificationPageDto(List<NotificationDto> items, boolean hasMore, String nextBefore) {
}
