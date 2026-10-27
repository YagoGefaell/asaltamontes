package io.github.yagogefaell.asaltamontes.event.service;

/** Published when an event is cancelled; notifications to the attendees listen to it (T18). {@code actorId} is who cancelled. */
public record EventCancelled(Long eventId, String reason, Long actorId) {}
