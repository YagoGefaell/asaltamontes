package io.github.yagogefaell.asaltamontes.event.service;

import java.util.List;

/**
 * An event changed something attendees care about (T18). {@code changedFields} are a subset of
 * startsAt, timezone, city, meetingPoint (name or coordinates).
 */
public record EventUpdated(Long eventId, Long actorId, List<String> changedFields) {}
