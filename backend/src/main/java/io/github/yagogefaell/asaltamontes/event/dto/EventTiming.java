package io.github.yagogefaell.asaltamontes.event.dto;

import java.time.Instant;

import io.github.yagogefaell.asaltamontes.event.domain.EventStatus;

/** When an event happens and whether it was cancelled: what its chat needs to know (T21). Internal, never serialized. */
public record EventTiming(EventStatus status, Instant startsAt, Instant endsAt) {
}
