package io.github.yagogefaell.asaltamontes.event.service;

/** Someone on the waitlist got a place; T18 notifies her. */
public record AttendeePromoted(Long eventId, Long userId) {}
